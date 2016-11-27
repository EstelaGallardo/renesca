package renesca

import org.specs2.matcher._

trait ExceptionMatchers {
  def failMatcher(matcher: Matcher[String]) = ExceptionMatchers.throwA[Exception].like {
    case e: Exception => matcher(Expectations.createExpectable(e.getMessage))
  }

  def fail(s: String) = failMatcher(AnyMatchers.beEqualTo(s))
  def failLike(s: String) = failMatcher(StringMatchers.matching(s.r))
}
