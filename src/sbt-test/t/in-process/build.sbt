scalacOptions += "-deprecation"

enablePlugins(SbtTeaVM)

val stdOutFile = file("run_std_out")
val stdErrFile = file("run_std_err")

InputKey[Unit]("cleanTestOutFiles") := {
  IO.delete(stdOutFile)
  IO.delete(stdErrFile)
}

InputKey[Unit]("checkRunOutput") := {
  import sbt.complete.DefaultParsers.*
  val args = spaceDelimited("<arg>").parsed
  assert(stdErrFile.exists == false)
  val actual = IO.read(stdOutFile)
  val expect = s"hello sbt-teavm ${args.mkString(", ")}"
  assert(actual == expect, s" '${actual}' != '${expect}' ")
}

teavmRunOption ~= (_.withLogger(
  sys.process.ProcessLogger(
    fout = str => {
      scala.Console.out.println(str)
      IO.write(
        file = stdOutFile,
        content = str,
        append = true,
      )
    },
    ferr = str => {
      scala.Console.err.println(str)
      IO.write(
        file = stdErrFile,
        content = str,
        append = true,
      )
    },
  )
))

teavmBuildOption ~= (_ withDaemon false)
