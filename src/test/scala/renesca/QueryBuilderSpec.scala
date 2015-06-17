package renesca

import org.junit.runner.RunWith
import org.specs2.mock._
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner
import renesca.parameter._
import renesca.parameter.implicits._
import renesca.graph._
import renesca.table.Table

@RunWith(classOf[JUnitRunner])
class QueryBuilderSpec extends Specification with Mockito {
  sequential

  def builder = new QueryBuilder {
    var counter = -1
    override def randomVariable = {
      counter += 1
      s"V$counter"
    }
  }

  def parameterMap: MapParameterValue = MapParameterValue(Map.empty)

  def parameterMap(keys: String*): ParameterMap = keys.map(k => (PropertyKey(k), parameterMap)).toMap

  def trimQuery(queryStr: String) = queryStr.trim.replaceAll(" +", " ").replace(" )", ")").replace(" ]", "]")

  def q(queryStr: String, parameterMap: ParameterMap) = Query(trimQuery(queryStr), parameterMap)

  def exq(response: Either[String, Seq[() => Seq[QueryConfig]]]): Seq[Seq[Query]] = {
    var counter = 0
    response.right.get.map(getter => {
      val configs = getter()
      configs.map(config => {
        config.item match {
          case i: Item =>
            counter += 1
            i.origin = Id(counter)
          case _       =>
        }
        q(config.query.statement, config.query.parameters)
      })
    }).filter(_.nonEmpty)
  }

  "QueryBuilder" should {
    "work with empty list of changes" in {
      val queries = exq(builder.generateQueries(Seq.empty))

      queries.size mustEqual 0
    }

    "node" should {
      "create only once" in {
        val n = Node.create
        val changes = Seq(
          AddItem(n),
          AddItem(n)
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(Seq(
          q(
            "create (V0 {V0_properties}) return V0",
            Map("V0_properties" -> parameterMap))
        ))
      }

      "not delete local node" in {
        val n = Node.create
        val changes = Seq(
          DeleteItem(n)
        )

        val queries = exq(builder.generateQueries(changes))

        queries.size mustEqual 0
      }

      "node deletion after node" in {
        val a = Node.create

        val changes = Seq(
          AddItem(a),
          DeleteItem(a)
        )

        val queries = exq(builder.generateQueries(changes))

        queries.size mustEqual 0
      }

      "node deletion before node" in {
        val a = Node.create

        val changes = Seq(
          DeleteItem(a),
          AddItem(a)
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(Seq(
          q(
            "create (V0 {V0_properties}) return V0",
            Map("V0_properties" -> parameterMap))
        ))
      }

      "create" in {
        val changes = Seq(
          AddItem(Node.create(Seq("v", "f", "l"), Map("18" -> 48)))
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(Seq(
          q(
            "create (V0 :`v`:`f`:`l` {V0_properties}) return V0",
            Map("V0_properties" -> Map("18" -> 48)))
        ))
      }

      "merge" in {
        val changes = Seq(
          AddItem(Node.merge(Seq("v", "f", "l"), Map("18" -> 48, "B" -> 0, "o" -> 1), Set("18"), Set("B")))
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(Seq(
          q(
            "merge (V0 :`v`:`f`:`l` {18: {V0_18}}) on create set V0 += {V0_onCreateProperties} on match set V0 += {V0_onMatchProperties} return V0",
            Map("V0_18" -> 48, "V0_onCreateProperties" -> Map("o" -> 1, "B" -> 0), "V0_onMatchProperties" -> Map("B" -> 0)))
        ))
      }

      "match" in {
        val changes = Seq(
          AddItem(Node.matches(Seq("v", "f", "l"), Map("18" -> 48)))
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(Seq(
          q(
            "match (V0 :`v`:`f`:`l` {18: {V0_18}}) return V0",
            Map("V0_18" -> 48))
        ))
      }

      "delete" in {
        val changes = Seq(
          DeleteItem(Node(1))
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(Seq(
          q(
            "match (V0) where id(V0) = {V0_nodeId} optional match (V0)-[V1]-() delete V1, V0",
            Map("V0_nodeId" -> 1))
        ))
      }

      "create with properties and labels" in {
        val changes = Seq(
          AddItem(Node.create(Seq("labello"), Map("prop" -> "erty")))
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(Seq(
          q(
            "create (V0 :`labello` {V0_properties})  return V0",
            Map("V0_properties" -> Map("prop" -> "erty")))
        ))
      }

      "set and remove properties and labels" in {
        // create non-local node with positive id
        val node = Node(1)
        val changes = Seq(
          SetProperty(node, "gisela", 3),
          RemoveLabel(node, "helmut"),
          SetLabel(node, "peter"),
          SetProperty(node, "edit", true),
          SetProperty(node, "new", true),
          RemoveProperty(node, "remove"),
          SetProperty(node, "gisela", 3),
          RemoveProperty(node, "new"),
          RemoveLabel(node, "peter"),
          SetLabel(node, "franz")
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(Seq(
          q(
            "match (V0) where id(V0) = {V0_itemId} set V0 += {V0_propertyAdditions} remove V0.`remove` remove V0.`new` set V0:`franz` remove V0:`peter` remove V0:`helmut`",
            Map("V0_itemId" -> 1, "V0_propertyAdditions" -> Map("edit" -> true, "gisela" -> 3)))
        ))
      }
    }

    "Relation" should {
      "fail on delete of relation node" in {
        val n = Node(1)
        val m = Node(2)
        val r = Relation.create(n, "a", m)
        val changes = Seq(
          DeleteItem(n),
          AddItem(r)
        )

        val result = builder.generateQueries(changes)

        result.left.toOption.isDefined mustEqual true
        result.left.get startsWith "Cannot delete start- or endnode of a new relation: "
      }

      "not delete local relation" in {
        val n = Node(1)
        val m = Node(2)
        val r = Relation.create(n, "a", m)
        val changes = Seq(
          DeleteItem(r)
        )

        val queries = exq(builder.generateQueries(changes))

        queries.size mustEqual 0
      }

      "fail on node deletion after relation" in {
        val a = Node.matches
        val b = Node.create
        val r = Relation.create(a, "kicks", b)

        val changes = Seq(
          AddItem(a),
          AddItem(b),
          AddItem(r),
          DeleteItem(a)
        )

        val result = builder.generateQueries(changes)

        result mustEqual Left("Cannot delete start- or endnode of a new relation: (Match())")
      }

      "node deletion before relation" in {
        val a = Node.matches
        val b = Node.create
        val r = Relation.create(a, "kicks", b)

        val changes = Seq(
          DeleteItem(b),
          AddItem(a),
          AddItem(b),
          AddItem(r)
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(
          Seq(
            q("match (V0) return V0", parameterMap),
            q("create (V1 {V1_properties}) return V1", parameterMap("V1_properties"))
          ),
          Seq(
            q("match (V2) where id(V2) = {V2_nodeId} match (V3) where id(V3) = {V3_nodeId} create (V2)-[V4 :`kicks` {V4_properties}]->(V3) return V4",
              Map("V2_nodeId" -> 1, "V4_properties" -> parameterMap, "V3_nodeId" -> 2))
          )
        )
      }

      "relation deletion after relation" in {
        val a = Node.matches
        val b = Node.create
        val r = Relation.create(a, "kicks", b)

        val changes = Seq(
          AddItem(a),
          AddItem(b),
          AddItem(r),
          DeleteItem(r)
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(
          Seq(
            q("match (V0) return V0", parameterMap),
            q("create (V1 {V1_properties}) return V1", parameterMap("V1_properties"))
          )
        )
      }

      "relation deletion before relation" in {
        val a = Node.matches
        val b = Node.create
        val r = Relation.create(a, "kicks", b)

        val changes = Seq(
          DeleteItem(r),
          AddItem(a),
          AddItem(b),
          AddItem(r)
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(
          Seq(
            q("match (V0) return V0", parameterMap),
            q("create (V1 {V1_properties}) return V1", parameterMap("V1_properties"))
          ),
          Seq(
            q("match (V2) where id(V2) = {V2_nodeId} match (V3) where id(V3) = {V3_nodeId} create (V2)-[V4 :`kicks` {V4_properties}]->(V3) return V4",
              Map("V2_nodeId" -> 1, "V4_properties" -> parameterMap, "V3_nodeId" -> 2))
          )
        )
      }

      "create" in {
        val a = Node.matches
        val b = Node.create
        val r = Relation.create(a, "kicks", b, Map("a" -> 1))

        val changes = Seq(
          AddItem(a),
          AddItem(b),
          AddItem(r)
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(
          Seq(
            q("match (V0) return V0", parameterMap),
            q("create (V1 {V1_properties}) return V1", parameterMap("V1_properties"))
          ),
          Seq(
            q("match (V2) where id(V2) = {V2_nodeId} match (V3) where id(V3) = {V3_nodeId} create (V2)-[V4 :`kicks` {V4_properties}]->(V3) return V4",
              Map("V2_nodeId" -> 1, "V4_properties" -> Map("a" -> 1), "V3_nodeId" -> 2))
          )
        )
      }

      "merge" in {
        val a = Node.matches
        val b = Node.merge
        val r = Relation.merge(a, "kicks", b, Map("a" -> 0, "b" -> 1, "c" -> 2), Set("a"), Set("b"))

        val changes = Seq(
          AddItem(a),
          AddItem(b),
          AddItem(r)
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(
          Seq(
            q("match (V0) return V0", parameterMap),
            q("merge (V1) on create set V1 += {V1_onCreateProperties} on match set V1 += {V1_onMatchProperties} return V1",
              parameterMap("V1_onCreateProperties", "V1_onMatchProperties"))
          ),
          Seq(
            q("match (V2) where id(V2) = {V2_nodeId} match (V3) where id(V3) = {V3_nodeId} merge (V2)-[V4 :`kicks` {a: {V4_a}}]->(V3) on create set V4 += {V4_onCreateProperties} on match set V4 += {V4_onMatchProperties} return V4",
              Map("V2_nodeId" -> 1, "V4_onCreateProperties" -> Map("b" -> 1, "c" -> 2), "V4_onMatchProperties" -> Map("b" -> 1), "V4_a" -> 0, "V3_nodeId" -> 2))
          )
        )
      }

      "match" in {
        val a = Node.matches
        val b = Node.matches
        val r = Relation.matches(a, "kicks", b, Map("a" -> 1))

        val changes = Seq(
          AddItem(a),
          AddItem(b),
          AddItem(r)
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(
          Seq(
            q("match (V0) return V0", parameterMap),
            q("match (V1) return V1", parameterMap)
          ),
          Seq(
            q("match (V2) where id(V2) = {V2_nodeId} match (V3) where id(V3) = {V3_nodeId} match (V2)-[V4 :`kicks` {a: {V4_a}}]->(V3) return V4",
              Map("V2_nodeId" -> 1, "V4_a" -> 1, "V3_nodeId" -> 2))
          )
        )
      }

      "delete" in {
        val a = Node(1)
        val b = Node(2)
        val r = Relation(3, a, b, "r")

        val changes = Seq(
          DeleteItem(r)
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(Seq(
          q("match ()-[V0]-() where id(V0) = {V0_relationId} delete V0",
            Map("V0_relationId" -> 3))
        ))
      }

      "create with properties" in {
        val a = Node(1)
        val b = Node.create(Seq("the kicked one"), Map("hurt" -> false))
        val r = Relation.create(a, "kicks", b, Map("really?" -> "no!"))

        val changes = Seq(
          AddItem(b),
          AddItem(r)
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(
          Seq(
            q("create (V0 :`the kicked one` {V0_properties}) return V0",
              Map("V0_properties" -> Map("hurt" -> false)))
          ),
          Seq(
            q("match (V1) where id(V1) = {V1_nodeId} match (V2) where id(V2) = {V2_nodeId} create (V1)-[V3 :`kicks` {V3_properties}]->(V2) return V3",
              Map("V1_nodeId" -> 1, "V3_properties" -> Map("really?" -> "no!"), "V2_nodeId" -> 1))
          )
        )
      }

      "set and remove properties" in {
        val r = Relation(3, Node(1), Node(2), "r")
        val changes = Seq(
          SetProperty(r, "edit", true),
          SetProperty(r, "new", true),
          RemoveProperty(r, "remove"),
          SetProperty(r, "gisela", 3),
          RemoveProperty(r, "new")
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(Seq(
          q("match ()-[V0]->() where id(V0) = {V0_itemId} set V0 += {V0_propertyAdditions} remove V0.`remove` remove V0.`new`",
            Map("V0_itemId" -> 3, "V0_propertyAdditions" -> Map("edit" -> true, "gisela" -> 3)))
        ))
      }
    }

    "Path" should {
      "fail on empty path" in {
        val p = Path()

        p mustEqual Left("Path cannot be empty")
      }

      "fail on inconsistent path origin" in {
        val a = Node.matches
        val b = Node.create
        val c = Node.merge
        val r1 = Relation.create(a, "kicks", b)
        val r2 = Relation.merge(b, "kicks", c)
        val p = Path(r1, r2)

        p mustEqual Left("Relations have inconsistent origin")
      }

      "fail on disconnected path" in {
        val a = Node.matches
        val b = Node.create
        val c = Node.merge
        val r1 = Relation.create(a, "kicks", b)
        val r2 = Relation.merge(a, "kicks", c)
        val p = Path(r1, r2)

        p mustEqual Left("Relations do not form a path")
      }

      "fail on overlapping paths" in {
        val a = Node.matches
        val b = Node.create
        val c = Node.merge
        val r1 = Relation.create(a, "kicks", b)
        val r2 = Relation.create(b, "from", c)
        val Right(p1) = Path(r1, r2)
        val d = Node.matches
        val r3 = Relation.create(b, "kicks", d)
        val Right(p2) = Path(r3)

        val changes = Seq(
          AddPath(p1),
          AddPath(p2)
        )

        val result = builder.generateQueries(changes)

        result mustEqual Left("Overlapping paths are currently not supported")
      }

      "fail on node deletion after path" in {
        val a = Node.matches
        val b = Node.create
        val r = Relation.create(a, "kicks", b)
        val Right(p) = Path(r)

        val changes = Seq(
          AddItem(a),
          AddItem(b),
          AddItem(r),
          AddPath(p),
          DeleteItem(a)
        )

        val result = builder.generateQueries(changes)

        result mustEqual Left("Cannot delete item which is contained in a path: (Match())")
      }

      "node deletion before path" in {
        val a = Node.matches
        val b = Node.create
        val r = Relation.create(a, "kicks", b)
        val Right(p) = Path(r)

        val changes = Seq(
          DeleteItem(b),
          AddItem(a),
          AddItem(b),
          AddItem(r),
          AddPath(p)
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(
          Seq(
            q("match (V0) return V0", parameterMap)
          ),
          Seq(
            q("match (V1) where id(V1) = {V1_nodeId} create (V1)-[V3 :`kicks` {V3_properties}]->(V2 {V2_properties}) return {id: id(V1), properties: V1, labels: labels(V1)} as V1,{id: id(V2), properties: V2, labels: labels(V2)} as V2,{id: id(V3), properties: V3} as V3",
              Map("V1_nodeId" -> 1, "V2_properties" -> parameterMap, "V3_properties" -> parameterMap))
          )
        )
      }

      "fail on relation deletion after path" in {
        val a = Node.matches
        val b = Node.create
        val r = Relation.create(a, "kicks", b)
        val Right(p) = Path(r)

        val changes = Seq(
          AddItem(a),
          AddItem(b),
          AddItem(r),
          AddPath(p),
          DeleteItem(r)
        )

        val result = builder.generateQueries(changes)

        result mustEqual Left("Cannot delete item which is contained in a path: (Match())-[Create():kicks]->(Create())")
      }

      "relation deletion before path" in {
        val a = Node.matches
        val b = Node.create
        val r = Relation.create(a, "kicks", b)
        val Right(p) = Path(r)

        val changes = Seq(
          DeleteItem(r),
          AddItem(a),
          AddItem(b),
          AddItem(r),
          AddPath(p)
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(
          Seq(
            q("match (V0) return V0", parameterMap)
          ),
          Seq(
            q("match (V1) where id(V1) = {V1_nodeId} create (V1)-[V3 :`kicks` {V3_properties}]->(V2 {V2_properties}) return {id: id(V1), properties: V1, labels: labels(V1)} as V1,{id: id(V2), properties: V2, labels: labels(V2)} as V2,{id: id(V3), properties: V3} as V3",
              Map("V1_nodeId" -> 1, "V2_properties" -> parameterMap, "V3_properties" -> parameterMap))
          )
        )
      }

      "create single relation" in {
        val a = Node.matches
        val b = Node.create
        val r = Relation.create(a, "kicks", b)
        val Right(p) = Path(r)

        val changes = Seq(
          AddItem(a),
          AddItem(b),
          AddItem(r),
          AddPath(p)
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(
          Seq(
            q("match (V0) return V0", parameterMap)
          ),
          Seq(
            q("match (V1) where id(V1) = {V1_nodeId} create (V1)-[V3 :`kicks` {V3_properties}]->(V2 {V2_properties}) return {id: id(V1), properties: V1, labels: labels(V1)} as V1,{id: id(V2), properties: V2, labels: labels(V2)} as V2,{id: id(V3), properties: V3} as V3",
              Map("V1_nodeId" -> 1, "V2_properties" -> parameterMap, "V3_properties" -> parameterMap))
          )
        )
      }

      "create" in {
        val a = Node.matches
        val b = Node.create
        val c = Node.merge
        val r1 = Relation.create(a, "kicks", b)
        val r2 = Relation.create(b, "from", c)
        val Right(p) = Path(r1, r2)

        val changes = Seq(
          AddItem(a),
          AddItem(b),
          AddItem(c),
          AddItem(r1),
          AddItem(r2),
          AddPath(p)
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(
          Seq(
            q("match (V0) return V0", parameterMap),
            q("merge (V1) on create set V1 += {V1_onCreateProperties} on match set V1 += {V1_onMatchProperties} return V1",
              parameterMap("V1_onCreateProperties", "V1_onMatchProperties"))
          ),
          Seq(
            q("match (V2) where id(V2) = {V2_nodeId} match (V3) where id(V3) = {V3_nodeId} create (V2)-[V5 :`kicks` {V5_properties}]->(V4 {V4_properties}) -[V6 :`from` {V6_properties}]->(V3) return {id: id(V2), properties: V2, labels: labels(V2)} as V2,{id: id(V3), properties: V3, labels: labels(V3)} as V3,{id: id(V4), properties: V4, labels: labels(V4)} as V4,{id: id(V5), properties: V5} as V5,{id: id(V6), properties: V6} as V6",
              Map("V6_properties" -> parameterMap, "V5_properties" -> parameterMap, "V3_nodeId" -> 2, "V2_nodeId" -> 1, "V4_properties" -> parameterMap))
          )
        )
      }

      "merge" in {
        val a = Node.matches
        val b = Node.create
        val c = Node.merge
        val r1 = Relation.merge(a, "kicks", b)
        val r2 = Relation.merge(b, "from", c)
        val Right(p) = Path(r1, r2)

        val changes = Seq(
          AddItem(a),
          AddItem(b),
          AddItem(c),
          AddItem(r1),
          AddItem(r2),
          AddPath(p)
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(
          Seq(
            q("match (V0) return V0", parameterMap),
            q("create (V1 {V1_properties}) return V1", parameterMap("V1_properties"))
          ),
          Seq(
            q("match (V2) where id(V2) = {V2_nodeId} match (V3) where id(V3) = {V3_nodeId} merge (V2)-[V4 :`kicks`]->(V3) -[V6 :`from`]->(V5) on create set V4 += {V4_onCreateProperties} on match set V4 += {V4_onMatchProperties} on create set V5 += {V5_onCreateProperties} on match set V5 += {V5_onMatchProperties} on create set V6 += {V6_onCreateProperties} on match set V6 += {V6_onMatchProperties} return {id: id(V2), properties: V2, labels: labels(V2)} as V2,{id: id(V3), properties: V3, labels: labels(V3)} as V3,{id: id(V4), properties: V4} as V4,{id: id(V5), properties: V5, labels: labels(V5)} as V5,{id: id(V6), properties: V6} as V6",
              Map("V5_onCreateProperties" -> parameterMap, "V4_onCreateProperties" -> parameterMap, "V6_onCreateProperties" -> parameterMap, "V5_onMatchProperties" -> parameterMap, "V3_nodeId" -> 2, "V2_nodeId" -> 1, "V4_onMatchProperties" -> parameterMap, "V6_onMatchProperties" -> parameterMap))
          )
        )
      }

      "match" in {
        val a = Node.matches
        val b = Node.create
        val c = Node.merge
        val r1 = Relation.matches(a, "kicks", b)
        val r2 = Relation.matches(b, "from", c)
        val Right(p) = Path(r1, r2)

        val changes = Seq(
          AddItem(a),
          AddItem(b),
          AddItem(c),
          AddItem(r1),
          AddItem(r2),
          AddPath(p)
        )

        val queries = exq(builder.generateQueries(changes))

        queries mustEqual Seq(
          Seq(
            q("create (V0 {V0_properties}) return V0", parameterMap("V0_properties")),
            q("merge (V1) on create set V1 += {V1_onCreateProperties} on match set V1 += {V1_onMatchProperties} return V1",
              parameterMap("V1_onCreateProperties", "V1_onMatchProperties"))
          ),
          Seq(
            q("match (V2) where id(V2) = {V2_nodeId} match (V3) where id(V3) = {V3_nodeId} match (V4)-[V5 :`kicks`]->(V2) -[V6 :`from`]->(V3) return {id: id(V2), properties: V2, labels: labels(V2)} as V2,{id: id(V3), properties: V3, labels: labels(V3)} as V3,{id: id(V4), properties: V4, labels: labels(V4)} as V4,{id: id(V5), properties: V5} as V5,{id: id(V6), properties: V6} as V6",
              Map("V3_nodeId" -> 2, "V2_nodeId" -> 1))
          )
        )
      }
    }
  }
}
