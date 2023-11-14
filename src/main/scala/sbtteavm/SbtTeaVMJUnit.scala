package sbtteavm

import org.teavm.tooling.TeaVMTargetType
import org.teavm.tooling.builder.BuildResult
import sbt.Keys.*
import sbt.*

object SbtTeaVMJUnit extends AutoPlugin {

  object autoImport {
    val teavmJUnitOption = settingKey[SbtTeaVMJUnitOption]("")
    val teavmTest = taskKey[Unit]("")

    private[SbtTeaVMJUnit] val teavmJUnitOpt = settingKey[Seq[TeaVMJUnitOpt]]("")
  }

  override def requires: Plugins = SbtTeaVM

  import autoImport.*

  private case class TeaVMPlatform(
    targetType: TeaVMTargetType,
    task: TaskKey[BuildResult],
    createOption: Boolean => TeaVMJUnitOpt
  )

  private[this] val values: Seq[TeaVMPlatform] = Seq(
    TeaVMPlatform(TeaVMTargetType.JAVASCRIPT, SbtTeaVM.autoImport.teavmJS, TeaVMJUnitOpt.Js),
    TeaVMPlatform(TeaVMTargetType.C, SbtTeaVM.autoImport.teavmC, TeaVMJUnitOpt.C),
    TeaVMPlatform(TeaVMTargetType.WEBASSEMBLY, SbtTeaVM.autoImport.teavmWasm, TeaVMJUnitOpt.Wasm),
    TeaVMPlatform(TeaVMTargetType.WEBASSEMBLY_WASI, SbtTeaVM.autoImport.teavmWasi, TeaVMJUnitOpt.Wasi),
  )

  override val projectSettings: Seq[Setting[?]] = Def.settings(
    Test / fork := true,
    libraryDependencies ++= Seq(
      SbtTeaVM.excludeLibraries(
        "org.teavm" % "teavm-junit" % SbtTeaVM.autoImport.teavmBuildOption.value.version % Test,
      ),
      "com.github.sbt" % "junit-interface" % "0.13.3" % Test,
    ),
    libraryDependencies ++= {
      teavmJUnitOption.value.testServerLog match {
        case TestServerLog.Stdout =>
          Nil
        case TestServerLog.Disable =>
          Seq("org.slf4j" % "slf4j-nop" % "1.7.36" % Test)
      }
    },
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
      testServerLog = TestServerLog.Stdout,
    ),
    Test / javaOptions := {
      val log = streams.value.log
      teavmJUnitOpt.value.zipWithIndex.groupBy(_._1.fullKey).toList.sortBy(_._1).map(_._2).map { x =>
        val res = x.maxBy(_._2)._1.asString
        if (x.size > 1) {
          log.info(s"found multiple value. overwrite ${res}")
        }
        res
      }
    },
    teavmJUnitOpt := {
      val x = teavmJUnitOption.value

      Seq(
        TeaVMJUnitOpt.Target(x.target),
        TeaVMJUnitOpt.JsRunner(x.jsRunner.value),
        TeaVMJUnitOpt.Minified(x.minified),
        TeaVMJUnitOpt.Optimized(x.optimized),
        TeaVMJUnitOpt.JsDecodeStack(x.jsDecodeStack),
        TeaVMJUnitOpt.WasmRunner(x.wasmRunner.value),
        TeaVMJUnitOpt.SourceDirs(x.sourceDirs)
      )
    },
    values.map { platform =>
      platform.task / test := {
        val s1 = state.value
        teavmJUnitOption.value.wasiRunner.withPath { runWasiPath =>
          teavmJUnitOption.value.cCompiler.withPath { cCompilerPath =>
            val newOptions = Seq[TeaVMJUnitOpt](
              platform.createOption(true),
              TeaVMJUnitOpt.WasiRunner(runWasiPath),
              TeaVMJUnitOpt.CCompiler(cCompilerPath),
            ) ++ values
              .filter(_.targetType != platform.targetType)
              .map(
                _.createOption(false)
              )

            val s2 = s1.appendWithSession(
              Seq(
                teavmJUnitOpt ++= newOptions
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
                TeaVMJUnitOpt.WasiRunner(runWasiPath),
                TeaVMJUnitOpt.CCompiler(cCompilerPath),
              ),
              values.map(x => x.createOption(types(x.targetType))),
            ).flatten
            val s2 = s1.appendWithSession(
              Seq(
                teavmJUnitOpt ++= newOptions,
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
