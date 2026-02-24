scalaVersion := "2.13.18"

scalacOptions += "-deprecation"

enablePlugins(SbtTeaVM)

InputKey[Unit]("check") := {
  if (scala.util.Properties.isLinux) {
    val dir = (teavmC / teavmBuildOption).value.targetDirectory
    val env = Seq[(String, String)](
      "LC_ALL" -> "C",
      "SOURCE_DIR" -> dir.getCanonicalPath,
    )
    val executable = "main"
    // https://github.com/konsoletyper/teavm/blob/083ecbdad2ca947/tests/compile-c-unix-fast.sh
    val buildArgs = Seq("gcc", "-g", "-O0", "-lrt", "-lm", "all.c", "-o", executable)
    assert(sys.process.Process(buildArgs, dir, env *).! == 0)
    assert((dir / executable).isFile)
    assert((dir / executable).setExecutable(true))
    val res = sys.process.Process(Seq(s"./${executable}", "c-language"), dir).!!
    assert(res == "hello sbt-teavm c-language\n", res)
  } else {
    streams.value.log.info("skip C test")
  }
}
