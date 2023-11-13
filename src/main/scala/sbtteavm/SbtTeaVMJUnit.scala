package sbtteavm

import org.teavm.tooling.TeaVMTargetType
import org.teavm.tooling.builder.BuildResult
import sbt.Keys.*
import sbt.*

object SbtTeaVMJUnit extends AutoPlugin {

  object autoImport {
    val teavmJUnitOption = settingKey[SbtTeaVMJUnitOption]("")
    val teavmTest = taskKey[Unit]("")
  }

  override def requires: Plugins = SbtTeaVM

  import autoImport.*

  override val projectSettings: Seq[Setting[?]] = Def.settings(
    Test / fork := true,
    libraryDependencies ++= Seq(
      SbtTeaVM.excludeLibraries(
        "org.teavm" % "teavm-junit" % SbtTeaVM.autoImport.teavmBuildOption.value.version % Test,
      ),
      "com.github.sbt" % "junit-interface" % "0.13.3" % Test,
    ),
    teavmJUnitOption := SbtTeaVMJUnitOption(
      target = crossTarget.value / "teavm-test",
      jsRunner = TeaVMBrowser.Chrome,
      minified = false,
      optimized = true,
      jsDecodeStack = false,
      wasmRunner = TeaVMBrowser.Chrome,
      sourceDirs = Seq(
        (Compile / sourceDirectories).value,
        (Test / sourceDirectories).value
      ).flatten,
      testTypes = Nil,
      wasiRunner = None, // TODO
      cCompiler = None, // TODO
    ),
    Test / javaOptions := {
      val x = teavmJUnitOption.value

      {
        Seq(
          x.wasiRunner.map(f => "wasi.runner" -> f.getAbsolutePath),
          x.cCompiler.map(f => "c.compiler" -> f.getAbsolutePath)
        ).flatten ++
          Seq[(String, String)](
            "target" -> x.target.getAbsolutePath,
            "js.runner" -> x.jsRunner.value,
            "minified" -> java.lang.Boolean.toString(x.minified),
            "optimized" -> java.lang.Boolean.toString(x.optimized),
            "js.decodeStack" -> java.lang.Boolean.toString(x.jsDecodeStack),
            "wasm.runner" -> x.wasmRunner.value,
            "sourceDirs" -> x.sourceDirs.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)
          )
      }.map { case (k, v) => s"-Dteavm.junit.${k}=${v}" }
    },
    Seq[(TaskKey[BuildResult], String)](
      SbtTeaVM.autoImport.teavmJS -> "js",
      SbtTeaVM.autoImport.teavmC -> "c",
      SbtTeaVM.autoImport.teavmWasm -> "wasm",
      SbtTeaVM.autoImport.teavmWasi -> "wasi",
    ).map { case (taskK, propertyKey) =>
      taskK / test := {
        val k = s"teavm.junit.${propertyKey}"
        val x1 = (Test / javaOptions).value
        val x2 = x1.filter(_ != s"-D${k}=false")
        val newOptions = x2 :+ s"-D${k}=true"
        streams.value.log.info(s"Test / javaOptions = ${newOptions}")
        val s = state.value.appendWithSession(
          Seq(
            Test / javaOptions := newOptions
          )
        )
        Project.extract(s).runTask(Test / test, s)._2
      }
    },
    teavmTest := {
      val option = teavmJUnitOption.value
      val types = option.testTypes.toSet
      val s1 = state.value
      val log = streams.value.log

      if (types.nonEmpty) {
        val all = Seq[(TeaVMTargetType, String)](
          TeaVMTargetType.JAVASCRIPT -> "js",
          TeaVMTargetType.WEBASSEMBLY -> "wasm",
          TeaVMTargetType.WEBASSEMBLY_WASI -> "wasi",
          TeaVMTargetType.C -> "c",
        )
        val newOptions = Seq(
          all.map { case (x, y) =>
            s"-D${y}=${types(x)}"
          },
          (Test / javaOptions).value.filterNot { x =>
            all.exists(a => x.startsWith(s"-D${a._2}="))
          }
        ).flatten
        log.info(s"Test / javaOptions = ${newOptions}")
        val s2 = s1.appendWithSession(
          Seq(
            Test / javaOptions := newOptions,
          )
        )
        Project.extract(s2).runTask(Test / test, s2)._2
      } else {
        log.info("testTypes is empty")
      }
    }
  )

}
