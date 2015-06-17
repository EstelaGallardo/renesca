package renesca

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import renesca.graph._
import renesca.parameter._
import renesca.parameter.implicits._
import renesca.table.Table

@RunWith(classOf[JUnitRunner])
class QueryHandlerDbSpec extends IntegrationSpecification {

  def resultNode: Node = {
    val resultGraph = db.queryGraph("match n return n")
    resultGraph.nodes.head
  }

  def resultRelation: Relation = {
    val resultGraph = db.queryGraph("match ()-[r]-() return r")
    resultGraph.relations.head
  }

  def testNodeSetProperty(data: PropertyValue) = {
    val graph = Graph.empty
    val node = Node.create

    graph.nodes += node

    node.properties("key") = data
    db.persistChanges(graph)

    resultNode.properties("key") mustEqual data
  }

  def testRelationSetProperty(data: PropertyValue) = {
    val graph = Graph.empty
    val start = Node.create
    val end = Node.create
    graph.nodes += start
    graph.nodes += end
    val relation = Relation.create(start, "EATS", end)
    graph.relations += relation

    relation.properties("key") = data
    db.persistChanges(graph)

    resultRelation.properties("key") mustEqual data
  }

  "QueryHandler" should {
    "throw exception on Neo4j Error" in {
      db.query("this is invalid cypher syntax") must throwA[RuntimeException]
    }

    "query table" in {
      db.query("create (n {a:1}),(m {a:2})")
      val table = db.queryTable("match x return x.a order by x.a")

      table mustEqual Table(
        columns = List("x.a"),
        data = List(
          List[ParameterValue](1),
          List[ParameterValue](2)
        )
      )
    }

    "return only graphs in json data on queryGraphs" in todo
    "return only parameters in json data on queryTables" in todo
    "return no json data on query" in todo
  }

  "QueryHandler.persist" should {

    "set long property on node" in { testNodeSetProperty(123) }
    "set double property on node" in { testNodeSetProperty(1.337) }
    "set string property on node" in { testNodeSetProperty("schnipp") }
    "set boolean property on node" in { testNodeSetProperty(true) }

    "set long array property on node" in { testNodeSetProperty(List(1, 3)) }
    "set double array property on node" in { testNodeSetProperty(List(1.7, 2.555555)) }
    "set string array property on node" in { testNodeSetProperty(List("schnipp", "schnapp")) }
    "set boolean array property on node" in { testNodeSetProperty(List(true, false)) }

    "remove property from node" in {
      val graph = Graph.empty
      val node = Node.create(properties = Map("yes" -> 0))
      graph.nodes += node
      db.persistChanges(graph)

      node.properties -= "yes"
      db.persistChanges(graph)

      resultNode.properties must beEmpty
    }

    "set label on node" in {
      val graph = Graph.empty
      val node = Node.create
      graph.nodes += node
      db.persistChanges(graph)

      node.labels += Label("BEER")
      db.persistChanges(graph)

      resultNode.labels must contain(exactly(Label("BEER")))
    }

    "remove label from node" in {
      val graph = Graph.empty
      val node = Node.create(Set("WINE"))
      graph.nodes += node
      db.persistChanges(graph)

      node.labels -= Label("WINE")
      db.persistChanges(graph)

      resultNode.labels must beEmpty
    }

    "delete node" in {
      val graph = Graph.empty
      val node = Node.create
      graph.nodes += node
      db.persistChanges(graph)

      graph.nodes -= node
      db.persistChanges(graph)

      val resultGraph = db.queryGraph("match n return n")
      resultGraph.nodes must beEmpty
    }

    "delete node with relations" in {
      // 1. create (m)-r->(n)<-l-(q)
      // 2. query (m)-r->(n)
      // 3. delete n (implicitly deletes relation r from graph and relation l which is only in the database)
      // 4. whole graph should be (m) and (q)

      val graph = Graph.empty
      val m = Node.create
      val n = Node.create
      val o = Node.create
      graph.nodes ++= List(m, n, o)
      val rel1 = Relation.create(m, "INTERNAL", n)
      val rel2 = Relation.create(n, "EXTERNAL", o)
      graph.relations ++= List(rel1, rel2)
      db.persistChanges(graph)

      db.queryWholeGraph.nodes must haveSize(3)

      val reducedGraph = db.queryGraph(Query(
        "match (m)-[r:INTERNAL]->(n) where id(m) = {mid} and id(n) = {nid} return n,r,m",
        Map("mid" -> m.origin.asInstanceOf[Id].id, "nid" -> n.origin.asInstanceOf[Id].id)))

      reducedGraph.nodes must haveSize(2) // m, n
      reducedGraph.relations must haveSize(1) // r

      reducedGraph.nodes -= n // deletes node n and relations l,r
      db.persistChanges(reducedGraph)

      val resultGraph = db.queryWholeGraph
      resultGraph.nodes must haveSize(2)
      resultGraph.nodes must not contain n
      resultGraph.relations must beEmpty
    }

    "set long property on relation" in { testRelationSetProperty(123) }
    "set double property on relation" in { testRelationSetProperty(1.337) }
    "set string property on relation" in { testRelationSetProperty("schnipp") }
    "set boolean property on relation" in { testRelationSetProperty(true) }

    "set long array property on relation" in { testRelationSetProperty(List(1, 3)) }
    "set double array property on relation" in { testRelationSetProperty(List(1.7, 2.555555)) }
    "set string array property on relation" in { testRelationSetProperty(List("schnipp", "schnapp")) }
    "set boolean array property on relation" in { testRelationSetProperty(List(true, false)) }

    "remove property from relation" in {
      val graph = Graph.empty
      val start = Node.create
      val end = Node.create
      graph.nodes += start
      graph.nodes += end
      val relation = Relation.create(start, "EATS", end, Map("yes" -> 100))
      graph.relations += relation
      db.persistChanges(graph)

      relation.properties -= "yes"
      db.persistChanges(graph)

      resultRelation.properties must beEmpty
    }

    "delete relation" in {
      val graph = Graph.empty
      val start = Node.create
      val end = Node.create
      graph.nodes += start
      graph.nodes += end
      val relation = Relation.create(start, "EATS", end)
      graph.relations += relation
      db.persistChanges(graph)

      graph.relations -= relation
      db.persistChanges(graph)

      val resultGraph = db.queryWholeGraph
      resultGraph.nodes must haveSize(2)
      resultGraph.relations must beEmpty
    }

    "add node" in {
      val graph = Graph.empty
      val node = Node.create
      graph.nodes += node
      node.origin.kind mustEqual Create.kind
      db.persistChanges(graph)
      node.origin.kind mustEqual Id.kind

      resultNode.origin mustEqual node.origin
      resultNode.labels must beEmpty
      resultNode.properties must beEmpty
    }

    "add merge node" in {
      def createNode: (Graph, Node) = {
        val graph = Graph.empty
        val node = Node.merge(Seq("merge"), Map("me" -> "be"), Set("me"))
        graph.nodes += node
        (graph, node)
      }

      val (graph, node) = createNode
      node.properties += ("you" -> "not")
      db.persistChanges(graph)

      val (graph2, node2) = createNode
      node2.origin.kind mustEqual Merge.kind
      db.persistChanges(graph2)
      node2.origin.kind mustEqual Id.kind

      node2.origin must beEqualTo(node.origin)
      node2.properties("you") must beEqualTo(node.properties("you"))
    }

    "add merge node with onMatch setter" in {
      val graph = Graph.empty
      val node = Node.create(Seq("merge"))
      graph.nodes += node
      db.persistChanges(graph)

      val graph2 = Graph.empty
      val node2 = Node.merge(Seq("merge"), Map("new" -> "yes"), onMatch = Set("new"))
      graph2.nodes += node2
      db.persistChanges(graph2)

      node2.origin must beEqualTo(node.origin)
      node2.properties("new") must beEqualTo(StringPropertyValue("yes"))
    }

    "add match node" in {
      val graph = Graph.empty
      val node = Node.create(Seq("matsch"), Map("me" -> "be", "you" -> "not"))
      graph.nodes += node
      val labelDistraction = Node.create(Seq("matsch"))
      val propertyDistraction = Node.create(properties = Map("me" -> "be"))
      graph.nodes += labelDistraction
      graph.nodes += propertyDistraction
      db.persistChanges(graph)

      val graph2 = Graph.empty
      val node2 = Node.matches(Seq("matsch"), Map("me" -> "be"))
      graph2.nodes += node2
      node2.origin.kind mustEqual Match.kind
      db.persistChanges(graph2)
      node2.origin.kind mustEqual Id.kind

      graph2.nodes.size must beEqualTo(1)
      node2.origin must beEqualTo(node.origin)
      node2.properties("you") must beEqualTo(node.properties("you"))
    }

    "add missing match node" in {
      val graph = Graph.empty
      val node = Node.matches(Seq("matsch"), Map("me" -> "be"))
      graph.nodes += node
      val failure = db.persistChanges(graph)

      failure mustEqual Some("Failed to apply queries")
    }

    "add properties and labels after NodeAdd" in {
      val graph = Graph.empty
      val node = Node.create
      graph.nodes += node
      node.properties += ("test" -> 5)
      node.labels ++= Set("foo", "bar")
      db.persistChanges(graph)

      resultNode.properties mustEqual Map("test" -> 5)
      resultNode.labels must contain(exactly(Label("foo"), Label("bar")))
    }

    "set properties and labels in NodeAdd" in {
      val graph = Graph.empty
      val node = Node.create(Set("foo", "bar"), Map("test" -> 5))
      graph.nodes += node
      db.persistChanges(graph)

      resultNode.properties mustEqual Map("test" -> 5)
      resultNode.labels must contain(exactly(Label("foo"), Label("bar")))
    }

    "add relation" in {
      val graph = Graph.empty
      val start = Node.create(Set("I"))
      val end = Node.create(Set("cheezburger"))
      graph.nodes += start
      graph.nodes += end
      val relation = Relation.create(start, "can haz", end)
      graph.relations += relation
      relation.origin.kind mustEqual Create.kind
      db.persistChanges(graph)
      relation.origin.kind mustEqual Id.kind

      resultRelation mustEqual relation
      resultRelation.startNode mustEqual start
      resultRelation.endNode mustEqual end
      resultRelation.relationType mustEqual RelationType("can haz")
    }

    "add merge relation" in {
      val (nodeA, nodeB) = (Node.create, Node.create)

      def createRelation: (Graph, Relation) = {
        val graph = Graph.empty
        val relation = Relation.merge(nodeA, "merge", nodeB, Map("me" -> "be"), Set("me"))
        graph.nodes += nodeA
        graph.nodes += nodeB
        graph.relations += relation
        (graph, relation)
      }

      val (graph, relation) = createRelation
      relation.properties += ("you" -> "not")
      db.persistChanges(graph)

      val (graph2, relation2) = createRelation
      relation2.origin.kind mustEqual Merge.kind
      db.persistChanges(graph2)
      relation2.origin.kind mustEqual Id.kind

      relation2.origin must beEqualTo(relation.origin)
      relation2.properties("you") must beEqualTo(relation.properties("you"))
    }

    "add merge relation with onMatch setter" in {
      val (nodeA, nodeB) = (Node.create, Node.create)

      def createRelation(graph: Graph, relation: Relation) {
        graph.nodes += nodeA
        graph.nodes += nodeB
        graph.relations += relation
      }

      val graph = Graph.empty
      val relation = Relation.create(nodeA, "merge", nodeB)
      createRelation(graph, relation)
      db.persistChanges(graph)

      val graph2 = Graph.empty
      val relation2 = Relation.merge(nodeA, "merge", nodeB, Map("new" -> "yes"), onMatch = Set("new"))
      createRelation(graph2, relation2)
      db.persistChanges(graph2)

      relation2.origin must beEqualTo(relation.origin)
      relation2.properties("new") must beEqualTo(StringPropertyValue("yes"))
    }

    "add match relation" in {
      val graph = Graph.empty
      val (nodeA, nodeB) = (Node.create, Node.create)
      val relation = Relation.create(nodeA, "matsch", nodeB, Map("me" -> "be", "you" -> "not"))
      graph.nodes += nodeA
      graph.nodes += nodeB
      graph.relations += relation
      val labelDistraction = Relation.create(nodeA, "matsch", nodeB)
      val propertyDistraction = Relation.create(nodeA, "sand", nodeB, Map("me" -> "be"))
      graph.relations += labelDistraction
      graph.relations += propertyDistraction
      db.persistChanges(graph)

      val graph2 = Graph.empty
      val relation2 = Relation.matches(nodeA, "matsch", nodeB, Map("me" -> "be"))
      graph2.nodes += nodeA
      graph2.nodes += nodeB
      graph2.relations += relation2
      relation2.origin.kind mustEqual Match.kind
      db.persistChanges(graph2)
      relation2.origin.kind mustEqual Id.kind

      graph2.relations.size must beEqualTo(1)
      relation2.origin must beEqualTo(relation.origin)
      relation2.properties("you") must beEqualTo(relation.properties("you"))
    }

    "add missing match relation" in {
      val graph = Graph.empty
      val (nodeA, nodeB) = (Node.create, Node.create)
      val relation = Relation.matches(nodeA, "matsch", nodeB, Map("me" -> "be"))
      graph.nodes += nodeA
      graph.nodes += nodeB
      graph.relations += relation
      val failure = db.persistChanges(graph)

      failure mustEqual Some("Failed to apply queries")
    }

    "add properties after RelationAdd" in {
      val graph = Graph.empty
      val start = Node.create(Set("I"))
      val end = Node.create(Set("cheezburger"))
      graph.nodes += start
      graph.nodes += end
      val relation = Relation.create(start, "can haz", end)
      graph.relations += relation
      relation.properties += ("one" -> "yes")
      db.persistChanges(graph)

      resultRelation.properties mustEqual Map("one" -> "yes")
    }

    "set properties in RelationAdd" in {
      val graph = Graph.empty
      val start = Node.create
      val end = Node.create
      graph.nodes += start
      graph.nodes += end
      val relation = Relation.create(start, "can haz", end, Map("one" -> "yes"))
      graph.relations += relation
      db.persistChanges(graph)

      resultRelation.properties mustEqual Map("one" -> "yes")
    }

    "add merge Path" in {
      val graph = Graph.empty
      val start = Node.merge(Seq("START"))
      val middle = Node.merge(Seq("MIDDLE"))
      val end = Node.merge(Seq("END"))
      val r1 = Relation.merge(start, "r1", middle)
      val r2 = Relation.merge(middle, "r2", end)
      val Right(path) = Path(r1,r2)
      graph += path
      db.persistChanges(graph)

      val graph2 = Graph.empty
      val start2 = Node.merge(Seq("START"))
      val middle2 = Node.merge(Seq("MIDDLE"))
      val end2 = Node.merge(Seq("END"))
      val r12 = Relation.merge(start2, "r1", middle2)
      val r22 = Relation.merge(middle2, "r2", end2)
      val Right(path2) = Path(r12,r22)
      graph2 += path2
      db.persistChanges(graph2)

      val wholeGraph = db.queryWholeGraph

      wholeGraph.relations.size mustEqual 2
      wholeGraph.nodes.size mustEqual 3
      start mustEqual start2
      middle mustEqual middle2
      end mustEqual end2
      r1 mustEqual r12
      r2 mustEqual r22
    }

    "add match Path" in {
      val graph = Graph.empty
      val start = Node.create(Seq("START"))
      val middle = Node.create(Seq("MIDDLE"))
      val end = Node.create(Seq("END"))
      val r1 = Relation.create(start, "r1", middle)
      val r2 = Relation.create(middle, "r2", end)
      val Right(path) = Path(r1,r2)
      graph += path
      db.persistChanges(graph)

      val graph2 = Graph.empty
      val start2 = Node.matches(Seq("START"))
      val middle2 = Node.matches(Seq("MIDDLE"))
      val end2 = Node.matches(Seq("END"))
      val r12 = Relation.matches(start2, "r1", middle2)
      val r22 = Relation.matches(middle2, "r2", end2)
      val Right(path2) = Path(r12,r22)
      graph2 += path2
      db.persistChanges(graph2)

      val wholeGraph = db.queryWholeGraph

      wholeGraph.relations.size mustEqual 2
      wholeGraph.nodes.size mustEqual 3
      start mustEqual start2
      middle mustEqual middle2
      end mustEqual end2
      r1 mustEqual r12
      r2 mustEqual r22
    }

    "add merge Path with matched middle node" in {
      val graph = Graph.empty
      val middle = Node.create(Seq("MIDDLE"))
      graph.nodes += middle
      db.persistChanges(graph)

      val graph2 = Graph.empty
      val start2 = Node.merge(Seq("START"))
      val middle2 = Node.matches(Seq("MIDDLE"))
      val end2 = Node.merge(Seq("END"))
      val r12 = Relation.merge(start2, "r1", middle2)
      val r22 = Relation.merge(middle2, "r2", end2)
      val Right(path2) = Path(r12,r22)
      graph2 += path2
      db.persistChanges(graph2)

      val wholeGraph = db.queryWholeGraph

      wholeGraph.relations.size mustEqual 2
      wholeGraph.nodes.size mustEqual 3
      middle mustEqual middle2
    }

    "add merge Path with matched start and end node" in {
      val graph = Graph.empty
      val start = Node.create(Seq("START"))
      val end = Node.create(Seq("END"))
      graph.nodes ++= Seq(start, end)
      db.persistChanges(graph)

      val graph2 = Graph.empty
      val start2 = Node.matches(Seq("START"))
      val middle2 = Node.merge(Seq("MIDDLE"))
      val end2 = Node.matches(Seq("END"))
      val r12 = Relation.merge(start2, "r1", middle2)
      val r22 = Relation.merge(middle2, "r2", end2)
      val Right(path2) = Path(r12,r22)
      graph2 += path2
      db.persistChanges(graph2)

      val wholeGraph = db.queryWholeGraph

      wholeGraph.relations.size mustEqual 2
      wholeGraph.nodes.size mustEqual 3
      start mustEqual start2
      end mustEqual end2
    }

    "add merge Path with matched start and end node" in {
      val graph = Graph.empty
      val start = Node.create(Seq("START"))
      val end = Node.create(Seq("END"))
      graph.nodes ++= Seq(start, end)
      db.persistChanges(graph)

      val graph2 = Graph.empty
      val start2 = Node.matches(Seq("START"))
      val middle2 = Node.merge(Seq("MIDDLE"))
      val end2 = Node.matches(Seq("END"))
      val r12 = Relation.merge(start2, "r1", middle2)
      val r22 = Relation.merge(middle2, "r2", end2)
      val Right(path2) = Path(r12,r22)
      graph2 += path2
      db.persistChanges(graph2)

      val wholeGraph = db.queryWholeGraph

      wholeGraph.relations.size mustEqual 2
      wholeGraph.nodes.size mustEqual 3
      start mustEqual start2
      end mustEqual end2
    }
  }
}

