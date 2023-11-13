package sbtteavm

import java.rmi.registry.LocateRegistry
import java.util.Properties
import org.teavm.backend.wasm.render.WasmBinaryVersion
import org.teavm.diagnostics.DefaultProblemTextConsumer
import org.teavm.tooling.TeaVMTargetType
import org.teavm.tooling.TeaVMToolLog
import org.teavm.tooling.builder.BuildResult
import org.teavm.tooling.builder.BuildStrategy
import org.teavm.tooling.builder.InProcessBuildStrategy
import org.teavm.tooling.builder.RemoteBuildStrategy
import org.teavm.tooling.daemon.BuildDaemon
import org.teavm.tooling.daemon.DaemonLog
import org.teavm.tooling.daemon.RemoteBuildService
import org.teavm.vm.TeaVMOptimizationLevel
import org.teavm.vm.TeaVMPhase
import org.teavm.vm.TeaVMProgressFeedback
import org.teavm.vm.TeaVMProgressListener
import sbt.Keys.*
import sbt.*
import scala.jdk.CollectionConverters.*
import scala.sys.process.ProcessLogger

object SbtTeaVM extends AutoPlugin {

  object autoImport {
    val teavmJS = taskKey[BuildResult]("")
    val teavmC = taskKey[BuildResult]("")
    val teavmWasm = taskKey[BuildResult]("")
    val teavmWasi = taskKey[BuildResult]("")

    val teavmBuildOption = settingKey[SbtTeaVMBuildOption]("")
    val teavmRunOption = settingKey[SbtTeaVMRunOption]("")

    val teavmApplySetting = taskKey[BuildStrategy => Unit]("")
    val teavmAfterBuild = taskKey[BuildResult => Unit]("")
    val teavmDaemonLog = taskKey[DaemonLog]("")
    val teavmClassPathEntries = taskKey[Seq[File]]("")
    val teavmProgressListener = taskKey[TeaVMProgressListener]("")
    val teavmLog = taskKey[TeaVMToolLog]("")

    val teavmTargetFileNameBase = settingKey[String]("")

    private[sbtteavm] val teavmPuppeteerFile =
      settingKey[(String, Seq[String]) => String]("").withRank(KeyRanks.DSetting)
    private[sbtteavm] val teavmJsHtmlFile =
      settingKey[File => String]("").withRank(KeyRanks.DSetting)
    private[sbtteavm] val teavmWasmHtmlFile =
      settingKey[Seq[String] => String]("").withRank(KeyRanks.DSetting)
  }

  import autoImport.*

  private[this] val buildValues: Seq[(TaskKey[BuildResult], TeaVMTargetType)] = Seq(
    teavmWasm -> TeaVMTargetType.WEBASSEMBLY,
    teavmWasi -> TeaVMTargetType.WEBASSEMBLY_WASI,
    teavmJS -> TeaVMTargetType.JAVASCRIPT,
    teavmC -> TeaVMTargetType.C,
  )

  private[sbtteavm] def excludeLibraries(x: ModuleID): ModuleID = {
    x.excludeAll(
      Seq(
        "commons-io",
        "com.carrotsearch",
        "org.mozilla",
        "org.ow2.asm",
      ).map(x => ExclusionRule(organization = x))*
    )
  }

  private[this] val teavmBuild: Def.SettingsDefinition = Def.settings {
    buildValues.map { case (x, targetType) =>
      x := {
        val buildOption = (x / teavmBuildOption).value
        val s = (x / teavmApplySetting).value
        val jarFiles = Def.taskDyn {
          getJarFiles(
            excludeLibraries(
              "org.teavm" % "teavm-tooling" % (x / teavmBuildOption).value.version
            )
          )
        }.value
        val daemonLog = (x / teavmDaemonLog).value
        val afterBuild = (x / teavmAfterBuild).value

        if (buildOption.cleanTargetDirectory) {
          IO.delete(buildOption.targetDirectory)
          IO.delete(buildOption.cacheDirectory)
        }

        def build(builder: BuildStrategy): BuildResult = {
          builder.init()
          s.apply(builder)
          builder.setTargetType(targetType)
          val result = builder.build()
          afterBuild.apply(result)
          result
        }

        if (buildOption.daemon) {
          val daemon = BuildDaemon.start(
            buildOption.incremental,
            buildOption.daemonMemory,
            daemonLog,
            jarFiles.map(_.getCanonicalPath).toArray*
          )

          try {
            val registry = LocateRegistry.getRegistry("localhost", daemon.getPort)
            val buildService = registry.lookup(RemoteBuildService.ID).asInstanceOf[RemoteBuildService]
            val builder = new RemoteBuildStrategy(buildService)
            build(builder)
          } finally {
            daemon.getProcess.destroy()
          }
        } else {
          val builder = new InProcessBuildStrategy
          build(builder)
        }
      }
    }
  }

  override val projectSettings: Seq[Setting[?]] = Def.settings(
    libraryDependencies += excludeLibraries("org.teavm" % "teavm-classlib" % teavmBuildOption.value.version),
    buildValues.flatMap { case (x, targetType) =>
      Def.settings(
        x / mainClass := {
          (Compile / mainClass).value.orElse(Defaults.askForMainClass((Compile / discoveredMainClasses).value))
        },
        x / sourceDirectories := (Compile / sourceDirectories).value,
        x / teavmApplySetting := {
          val mainClassOpt = (x / mainClass).value.filter(_.nonEmpty)
          val log = streams.value.log
          val options = (x / teavmBuildOption).value

          builder => {
            builder.setTargetType(targetType)
            (x / sourceDirectories).?.value.getOrElse(Nil).map(_.getAbsolutePath).foreach(builder.addSourcesDirectory)
            options.sourcesJar.map(_.getAbsolutePath).foreach(builder.addSourcesJar)
            (x / teavmClassPathEntries).?.value
              .map(_.map(_.getAbsolutePath).asJava)
              .foreach(builder.setClassPathEntries)
            mainClassOpt match {
              case Some(mainClassName) =>
                builder.setMainClass(mainClassName)
              case None =>
                log.warn(s"${mainClass.key.label} is empty")
            }
            builder.setEntryPointName(options.entryPointName)
            builder.setTargetDirectory(options.targetDirectory.getAbsolutePath)
            builder.setSourceMapsFileGenerated(options.sourceMapsFileGenerated)
            builder.setDebugInformationGenerated(options.debugInformationGenerated)
            builder.setSourceFilesCopied(options.sourceFilesCopied)
            (x / teavmProgressListener).?.value.foreach(builder.setProgressListener)
            builder.setIncremental(options.incremental)
            val p = new Properties()
            options.properties.foreach { case (k, v) =>
              p.setProperty(k, v)
            }
            builder.setProperties(p)
            (x / teavmLog).?.value.foreach(builder.setLog)
            builder.setObfuscated(options.obfuscated)
            builder.setStrict(options.strict)
            builder.setMaxTopLevelNames(options.maxTopLevelNames)
            builder.setTransformers(options.transformers.toArray)
            builder.setOptimizationLevel(options.optimizationLevel)
            builder.setFastDependencyAnalysis(options.fastDependencyAnalysis)
            builder.setClassesToPreserve(options.classesToPreserve.toArray)
            builder.setCacheDirectory(options.cacheDirectory.getAbsolutePath)
            builder.setWasmVersion(options.wasmVersion)
            builder.setMinHeapSize(options.minHeapSize)
            builder.setMaxHeapSize(options.maxHeapSize)
            builder.setHeapDump(options.heapDump)
            builder.setShortFileNames(options.shortFileNames)
            builder.setAssertionsRemoved(options.assertionsRemoved)
            if (options.targetFileName.nonEmpty) {
              builder.setTargetFileName(options.targetFileName)
            }
          }
        }
      )
    },
    teavmBuild,
    teavmDaemonLog := new DaemonLogImpl(streams.value.log),
    teavmAfterBuild := {
      val log = streams.value.log
      (result: BuildResult) => {
        log.info(result.getGeneratedFiles.asScala.map("  " + _).mkString("generated teavm files:\n", "\n", ""))
        val problems = result.getProblems.getProblems.asScala.toList
        if (problems.nonEmpty) {
          log.warn(s"found ${problems.size} problems")
          problems.map { p =>
            val consumer = new DefaultProblemTextConsumer();
            p.render(consumer)
            consumer.getText
          }.sorted.foreach(s => log.warn(s))
        } else {
          log.info(s"success. there is no problems")
        }
      }
    },
    inTask(teavmJS)(
      teavmPuppeteerFile := { (url, args) =>
        s"""|const puppeteer = require('puppeteer');
            |
            |(async () => {
            |  const browser = await puppeteer.launch({
            |    headless: 'new',
            |  });
            |  const page = await browser.newPage();
            |  page
            |    .on('console', message => {
            |      console.log(message.text());
            |    });
            |  try {
            |    await page.goto('${url}', {
            |      waitUntil: 'networkidle2'
            |    });
            |    await page.evaluate(() => {
            |      ${teavmBuildOption.value.entryPointName}(${args.map("'" + _ + "'").mkString("[", ", ", "]")});
            |    });
            |  } finally {
            |    await browser.close();
            |  }
            |})();
            |""".stripMargin
      }
    ),
    inTask(teavmWasm)(
      teavmPuppeteerFile := { (url, args) =>
        s"""|const puppeteer = require('puppeteer');
            |
            |(async () => {
            |  const browser = await puppeteer.launch({
            |    headless: 'new',
            |  });
            |  const page = await browser.newPage();
            |  page
            |    .on('console', message => {
            |      console.log(message.text());
            |    });
            |  try {
            |    await page.goto('${url}', {
            |      waitUntil: 'networkidle2'
            |    });
            |  } finally {
            |    await browser.close();
            |  }
            |})();
            |""".stripMargin
      }
    ),
    teavmJsHtmlFile := { jsFile =>
      s"""|<!DOCTYPE html>
          |<html>
          |  <head>
          |    <meta http-equiv="Content-Type" content="text/html;charset=utf-8">
          |    <script type="text/javascript" charset="utf-8" src="${jsFile.getAbsolutePath}"></script>
          |  </head>
          |</html>
          |""".stripMargin
    },
    teavmWasmHtmlFile := { args =>
      val entryPoint = (teavmWasm / teavmBuildOption).value.entryPointName
      val expandArgs = args.map("\"" + _ + "\"").mkString("[", ", ", "]")
      s"""|<!DOCTYPE html>
          |<html>
          |  <head>
          |    <meta http-equiv="Content-Type" content="text/html;charset=utf-8">
          |    <script type="text/javascript" charset="utf-8" src="main.wasm-runtime.js"></script>
          |    <script type="application/javascript">
          |      TeaVM.wasm.load("main.wasm").then(teavm => {
          |        teavm.${entryPoint}(${expandArgs});
          |      });
          |    </script>
          |  </head>
          |</html>
          |""".stripMargin
    },
    teavmRunOption := SbtTeaVMRunOption(
      logger = ProcessLogger(
        fout = str => scala.Console.out.println(str),
        ferr = str => scala.Console.err.println(str),
      ),
      openBrowser = false,
      portNumber = None,
      wasiCommand = "wasmtime",
      puppeteerVersion = "latest",
    ),
    Seq(
      teavmJS -> TeavmRun.js,
      teavmWasi -> TeavmRun.wasi,
      teavmWasm -> TeavmRun.wasm,
    ).flatMap { case (key, runImpl) =>
      inTask(key)(
        Def.settings(
          run := Def.inputTaskDyn {
            import sbt.complete.DefaultParsers.*
            val args = spaceDelimited("<arg>").parsed
            runImpl.run(
              args = args,
              build = key,
              taskName = run.key.label,
              runLogger = teavmRunOption.value.logger,
            )
          }.evaluated,
          runMain := {
            val parser = {
              import sjsonnew.BasicJsonProtocol.*
              Defaults.loadForParser(Compile / discoveredMainClasses)((s, names) =>
                Defaults.runMainParser(s, names getOrElse Nil)
              )
            }

            Def.inputTaskDyn {
              val (mainClassName, args) = parser.parsed

              runImpl.run(
                args = args,
                build = Def.task {
                  val s = state.value.appendWithSession(Seq(key / mainClass := Some(mainClassName)))
                  Project.extract(s).runTask(key, s)._2
                },
                taskName = Keys.runMain.key.label,
                runLogger = teavmRunOption.value.logger,
              )
            }
          }.evaluated,
        )
      )
    },
    teavmLog := new TeaVMLog(streams.value.log),
    teavmClassPathEntries := (Compile / fullClasspath).value.map(_.data),
    teavmTargetFileNameBase := "main",
    teavmBuildOption := SbtTeaVMBuildOption(
      sourcesJar = Vector.empty,
      entryPointName = "main",
      targetDirectory = crossTarget.value / "teavm" / "default",
      sourceMapsFileGenerated = false,
      debugInformationGenerated = false,
      sourceFilesCopied = false,
      incremental = false,
      properties = Map.empty,
      obfuscated = false,
      strict = false,
      maxTopLevelNames = 1000000,
      transformers = Vector.empty,
      optimizationLevel = TeaVMOptimizationLevel.SIMPLE,
      fastDependencyAnalysis = false,
      targetFileName = "",
      classesToPreserve = Vector.empty,
      cacheDirectory = crossTarget.value / "teavm" / "cache" / "default",
      wasmVersion = WasmBinaryVersion.V_0x1,
      minHeapSize = 8 * 1024 * 1024,
      maxHeapSize = 128 * 1024 * 1024,
      heapDump = false,
      shortFileNames = false,
      assertionsRemoved = false,
      daemon = true,
      daemonMemory = 1024,
      version = SbtTeaVMBuildInfo.teavmVersion,
      cleanTargetDirectory = true,
    ),
    Seq[(TaskKey[BuildResult], String, String)](
      (teavmWasi, "wasi", "wasm"),
      (teavmWasm, "wasm", "wasm"),
      (teavmJS, "js", "js"),
      (teavmC, "c", ""),
    ).map { case (k, v, ext) =>
      k / teavmBuildOption := {
        (k / teavmBuildOption).value
          .withTargetDirectory(
            crossTarget.value / "teavm" / v,
          )
          .withTargetFileName(
            if (ext == "") {
              ""
            } else {
              s"${teavmTargetFileNameBase.value}.${ext}"
            }
          )
          .withCacheDirectory(
            cacheDirectory = crossTarget.value / "teavm" / "cache" / v,
          )
      }
    },
    teavmProgressListener := new TeaVMProgressListener {
      override def phaseStarted(phase: TeaVMPhase, count: Int): TeaVMProgressFeedback = {
        streams.value.log.debug(s"teavm phase start ${phase} ${count}")
        TeaVMProgressFeedback.CONTINUE
      }

      override def progressReached(progress: Int): TeaVMProgressFeedback = {
        streams.value.log.debug(s"teavm progress reached ${progress}")
        TeaVMProgressFeedback.CONTINUE
      }
    }
  )

  private[this] def getJarFiles(module: ModuleID): Def.Initialize[Task[Seq[File]]] = Def.task {
    dependencyResolution.value
      .retrieve(
        dependencyId = module,
        scalaModuleInfo = scalaModuleInfo.value,
        retrieveDirectory = csrCacheDirectory.value,
        log = streams.value.log
      )
      .left
      .map(e => throw e.resolveException)
      .merge
      .distinct
  }

}
