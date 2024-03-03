package sbtteavm

import org.teavm.tooling.builder.BuildResult
import sbt.*
import sbt.Keys.*
import sbtteavm.SbtTeaVM.autoImport.*
import scala.sys.process.ProcessLogger

private[sbtteavm] trait TeavmRun {
  def run(
    args: Seq[String],
    build: Def.Initialize[Task[BuildResult]],
    taskName: String,
    runLogger: ProcessLogger,
  ): Def.Initialize[Task[Unit]]
}

private[sbtteavm] object TeavmRun {
  val js: TeavmRun = (
    args: Seq[String],
    build: Def.Initialize[Task[BuildResult]],
    taskName: String,
    runLogger: ProcessLogger,
  ) =>
    Def.task {
      val _ = build.value
      val buildOptions = teavmBuildOption.value
      val originalJS = buildOptions.targetDirectory / buildOptions.targetFileName
      val log = streams.value.log
      IO.withTemporaryDirectory { tmp =>
        val mainJS = tmp / "main.js"
        IO.copyFile(originalJS, mainJS)
        val htmlFile = tmp / "index.html"
        IO.write(htmlFile, teavmJsHtmlFile.value.apply(mainJS))
        val puppeteer = tmp / "puppeteer.js"
        val htmlUrl = s"file:///${htmlFile.getCanonicalFile}"
        IO.write(puppeteer, teavmPuppeteerFile.value.apply(htmlUrl, args))

        def run[A](command: Seq[String], f: sys.process.ProcessBuilder => A): A = {
          log.info(command.mkString("run: '", " ", "'"))
          f(sys.process.Process(command, tmp))
        }

        run(Seq("npm", "install", s"puppeteer@${teavmRunOption.value.puppeteerVersion}"), _.!)
        log.info(s"html file path = ${htmlFile.getCanonicalFile.toURI.toURL}")
        if (teavmRunOption.value.openBrowser) {
          unfiltered.util.Browser.open(htmlUrl)
        }
        run(Seq("node", puppeteer.getAbsolutePath), _ ! runLogger) match {
          case 0 => // success
          case exitCode =>
            val msg = s"failed ${teavmJS.key.label}/${taskName}. exit code = ${exitCode}"
            log.error(msg)
            sys.error(msg)
        }
      }
    }

  val wasi: TeavmRun = (
    args: Seq[String],
    build: Def.Initialize[Task[BuildResult]],
    taskName: String,
    runLogger: ProcessLogger,
  ) =>
    Def.task {
      val _ = build.value
      val buildOptions = teavmBuildOption.value
      val wasmFile = buildOptions.targetDirectory / buildOptions.targetFileName
      val command = Seq(teavmRunOption.value.wasiCommand, wasmFile.getAbsolutePath) ++ args
      val log = streams.value.log
      log.info(command.mkString("run: '", " ", "'"))
      sys.process.Process(command).!(runLogger) match {
        case 0 => // success
        case exitCode =>
          val msg = s"failed ${teavmWasi.key.label}/${taskName}. exit code = ${exitCode}"
          log.error(msg)
          sys.error(msg)
      }
    }

  val wasm: TeavmRun = (
    args: Seq[String],
    build: Def.Initialize[Task[BuildResult]],
    taskName: String,
    runLogger: ProcessLogger,
  ) =>
    Def.task {
      val _ = build.value
      val runtimeJsSuffix = "-runtime.js"
      val buildOptions = teavmBuildOption.value
      val wasm = IO.readBytes(
        buildOptions.targetDirectory / buildOptions.targetFileName
      )
      val runtimeJS = IO.read(
        buildOptions.targetDirectory / (buildOptions.targetFileName + runtimeJsSuffix)
      )
      val log = streams.value.log
      val html = teavmWasmHtmlFile.value.apply(args)

      SbtTeaVMServer.withServer(
        args = SbtTeaVMServer.Args(
          html = html,
          wasm = wasm,
          runtimeJS = runtimeJS,
          log = s => log.info(s)
        ),
        portOpt = teavmRunOption.value.portNumber
      ) { server =>
        IO.withTemporaryDirectory { tmp =>
          val puppeteer = tmp / "puppeteer.js"
          IO.write(
            puppeteer,
            teavmPuppeteerFile.value.apply(s"http://127.0.0.1:${server.ports.head}", args)
          )

          def run[A](command: Seq[String], f: sys.process.ProcessBuilder => A): A = {
            log.info(command.mkString("run: '", " ", "'"))
            f(sys.process.Process(command, tmp))
          }

          if (teavmRunOption.value.openBrowser) {
            unfiltered.util.Browser.open(server.portBindings.head.url)
          }

          run(Seq("npm", "install", s"puppeteer@${teavmRunOption.value.puppeteerVersion}"), _.!)
          run(Seq("node", puppeteer.getAbsolutePath), _ ! runLogger) match {
            case 0 => // success
            case exitCode =>
              val msg = s"failed ${teavmWasm.key.label}/${taskName}. exit code = ${exitCode}"
              log.error(msg)
              sys.error(msg)
          }
        }
      }
    }
}
