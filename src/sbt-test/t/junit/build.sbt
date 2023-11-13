scalaVersion := "2.13.12"

scalacOptions ++= Seq(
  "-deprecation",
  "-Xsource:3",
)

enablePlugins(SbtTeaVMJUnit)

Test / testOptions += Tests.Argument(
  TestFrameworks.JUnit,
  "-v",
)

val testLogFile = Def.setting(target.value / "test_log.txt")

Test / forkOptions := (
  (Test / forkOptions).value.withOutputStrategy(
    OutputStrategy.CustomOutput(
      new java.io.FileOutputStream(testLogFile.value, true)
    )
  )
)

InputKey[Unit]("check") := {
  import sbt.complete.DefaultParsers.*

  val values = Seq(
    "isWebAssembly",
    "isJavaScript",
    "isC",
  )
  val (arg, success) = ((token(Space) ~> token(StringBasic)) ~ (token(Space) ~> token(Bool))).parsed
  val actual = IO.readLines(testLogFile.value).filterNot(_ contains "chrome")
  val x = List(
    s"(os,TeaVM),(class,foo.Test1),${values.map { x => x -> (arg == x) }.mkString(",")}",
  ) ++ {
    if (success) List("SUCCESS") else Nil
  }
  val expect = x ++ x
  assert(actual == expect, s" '${actual}' != '${expect}' ")
}

TaskKey[Unit]("checkAll") := {
  val actual = IO.readLines(testLogFile.value).filterNot(_ contains "chrome")
  val expect = List.fill(2)(
    "(os,TeaVM),(class,foo.Test1),(isWebAssembly,true),(isJavaScript,false),(isC,false)"
  )
  assert(actual == expect, s" '${actual}' != '${expect}' ")
}

teavmJUnitOption ~= (
  _.withTestTypes(
    Seq(
      org.teavm.tooling.TeaVMTargetType.JAVASCRIPT,
      org.teavm.tooling.TeaVMTargetType.WEBASSEMBLY,
      org.teavm.tooling.TeaVMTargetType.WEBASSEMBLY_WASI,
    )
  )
)
