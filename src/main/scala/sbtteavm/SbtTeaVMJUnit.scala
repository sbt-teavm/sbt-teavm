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

  private[this] val values: Seq[(TeaVMTargetType, TaskKey[BuildResult], String)] = Seq(
    (TeaVMTargetType.JAVASCRIPT, SbtTeaVM.autoImport.teavmJS, "js"),
    (TeaVMTargetType.C, SbtTeaVM.autoImport.teavmC, "c"),
    (TeaVMTargetType.WEBASSEMBLY, SbtTeaVM.autoImport.teavmWasm, "wasm"),
    (TeaVMTargetType.WEBASSEMBLY_WASI, SbtTeaVM.autoImport.teavmWasi, "wasi"),
  )

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
      wasiRunner = TeaVMRunner.Script(
        """mkdir -p target/wasi-testdir
          |wasmtime run --dir target/wasi-testdir::/ $1 $2
          |""".stripMargin
      ),
      cCompiler = TeaVMRunner.Script(
        """export LC_ALL=C
          |SOURCE_DIR=$(pwd)
          |gcc -g -O0 -lrt all.c -o run_test -lm
          |""".stripMargin
      ),
    ),
    Test / javaOptions := {
      val x = teavmJUnitOption.value

      Seq[(String, String)](
        "target" -> x.target.getAbsolutePath,
        "js.runner" -> x.jsRunner.value,
        "minified" -> java.lang.Boolean.toString(x.minified),
        "optimized" -> java.lang.Boolean.toString(x.optimized),
        "js.decodeStack" -> java.lang.Boolean.toString(x.jsDecodeStack),
        "wasm.runner" -> x.wasmRunner.value,
        "sourceDirs" -> x.sourceDirs.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)
      ).map { case (k, v) => s"-Dteavm.junit.${k}=${v}" }
    },
    values.map { case (_, taskK, propertyKey) =>
      taskK / test := {
        val k = s"teavm.junit.${propertyKey}"
        val x1 = (Test / javaOptions).value
        val x2 = x1.filter(_ != s"-D${k}=false")
        val s1 = state.value
        val log = streams.value.log
        teavmJUnitOption.value.wasiRunner.withPath { runWasiPath =>
          teavmJUnitOption.value.cCompiler.withPath { cCompilerPath =>
            val newOptions = (x2 ++ Seq(
              s"-D${k}=true",
              s"-Dteavm.junit.wasi.runner=${runWasiPath}",
              s"-Dteavm.junit.c.compiler=${cCompilerPath}",
            ) ++ values.map(_._3).filter(_ != propertyKey).map { x =>
              s"-Dteavm.junit.${x}=false"
            }).sorted

            log.info(s"Test / javaOptions = ${newOptions}")
            val s2 = s1.appendWithSession(
              Seq(
                Test / javaOptions := newOptions
              )
            )
            Project.extract(s2).runTask(Test / test, s2)._2
          }
        }
      }
    },
    teavmTest := {
      val option = teavmJUnitOption.value
      val types = option.testTypes.toSet
      val s1 = state.value
      val log = streams.value.log

      if (types.nonEmpty) {

        teavmJUnitOption.value.wasiRunner.withPath { runWasiPath =>
          teavmJUnitOption.value.cCompiler.withPath { cCompilerPath =>
            val newOptions = Seq(
              Seq(
                s"-Dteavm.junit.wasi.runner=${runWasiPath}",
                s"-Dteavm.junit.c.compiler=${cCompilerPath}",
              ),
              values.map { case (x, _, y) =>
                s"-Dteavm.junit.${y}=${types(x)}"
              },
              (Test / javaOptions).value.filterNot { x =>
                values.map(_._3).exists(a => x.startsWith(s"-Dteavm.junit.${a}="))
              }
            ).flatten.sorted
            log.info(s"Test / javaOptions = ${newOptions}")
            val s2 = s1.appendWithSession(
              Seq(
                Test / javaOptions := newOptions,
              )
            )
            Project.extract(s2).runTask(Test / test, s2)._2
          }
        }
      } else {
        log.info("testTypes is empty")
      }
    }
  )

}
