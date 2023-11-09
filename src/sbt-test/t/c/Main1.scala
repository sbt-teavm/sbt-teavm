package example

object Main1 {
  def main(args: Array[String]): Unit = {
    assert(sys.props.get("os.name") == Some("TeaVM"))
    println(s"hello sbt-teavm ${args.mkString(", ")}")
  }
}
