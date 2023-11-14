package sbtteavm

sealed abstract class TestServerLog extends Product with Serializable

object TestServerLog {
  case object Stdout extends TestServerLog
  case object Disable extends TestServerLog
}
