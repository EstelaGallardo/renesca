package renesca.graph

import org.junit.runner.RunWith
import org.specs2.mock._
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope
import renesca._

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

@RunWith(classOf[JUnitRunner])
class PropertiesSpec extends Specification with Mockito {

  implicit def intToJson(x: Int) = x.asJson
  implicit def stringToJson(x: String) = x.asJson
  implicit def listToJson[T: Encoder](xs: List[T]) = xs.asJson
  implicit def keyValue[T: Encoder](t: (String, T)) = (NonBacktickName(t._1), t._2.asJson)

  trait MockNode extends Scope {
    val A = Node(1)
    val graph = mock[Graph]
  }

  "Properties" should {
    "store property" in new MockNode {
      A.properties("key") = "value"

      A.properties("key").asString.get mustEqual "value"
    }

    "remove property" in new MockNode {
      A.properties("key") = "value"

      A.properties -= "key"

      A.properties.isDefinedAt("key") must beFalse
    }

    "get non-existing element" in new MockNode {
      A.properties.get("key") mustEqual None
    }

    "get existing element" in new MockNode {
      A.properties("key") = "value"

      A.properties.get("key").flatMap(_.asString) mustEqual Some("value")
    }

    "provide iterator" in new MockNode {
      A.properties("key") = "value"

      A.properties.iterator must contain(exactly(PropertyKey("key") -> "value".asJson))
    }

    "provide empty" in new MockNode {
      A.properties("key") = "value"

      A.properties.empty must beEmpty
    }
  }
}
