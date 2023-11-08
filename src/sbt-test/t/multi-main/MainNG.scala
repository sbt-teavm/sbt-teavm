package example

object MainNG {
  def main(args: Array[String]): Unit = {
    sys.error(s"oops sbt-teavm ${args.mkString(", ")}")
  }
}
