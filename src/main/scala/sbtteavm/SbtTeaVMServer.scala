package sbtteavm

import jakarta.servlet.http.HttpServletRequest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import unfiltered.filter.Plan
import unfiltered.jetty.Server
import unfiltered.request.HttpRequest
import unfiltered.request.Path
import unfiltered.response.BaseContentType
import unfiltered.response.HtmlContent
import unfiltered.response.JsContent
import unfiltered.response.NotFound
import unfiltered.response.Ok
import unfiltered.response.ResponseBytes
import unfiltered.response.ResponseString

private[sbtteavm] object SbtTeaVMServer {
  def withServer[A](args: Args, portOpt: Option[Int])(f: Server => A): A = {
    val server = {
      val s = portOpt match {
        case Some(p) =>
          unfiltered.jetty.Server.local(p)
        case None =>
          unfiltered.jetty.Server.anylocal
      }
      args.log(s"server port = ${s.ports.toList.sorted}")
      s
    }.plan {
      new SbtTeaVMServer(args)
    }

    try {
      args.log("server start")
      server.start()
      args.log("server started")
      f(server)
    } finally {
      server.stop()
    }
  }
  case class Args(
    html: String,
    wasm: Array[Byte],
    runtimeJS: String,
    log: String => Unit
  )
}

private[sbtteavm] final class SbtTeaVMServer(args: SbtTeaVMServer.Args) extends unfiltered.filter.Plan {

  private[this] type Req = HttpRequest[HttpServletRequest]
  private[this] def logAll(request: Req): Unit = {
    args.log(s"${DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now())} ${request.method} ${request.uri}")
  }

  private[this] val wasmContentType: BaseContentType = _ => "application/wasm"

  private[this] val main: Plan.Intent = {
    case Path("/" | "/index.html") =>
      HtmlContent ~> ResponseString(args.html)
    case Path("/main.wasm") =>
      wasmContentType ~> ResponseBytes(args.wasm)
    case Path("/main.wasm-runtime.js") =>
      JsContent ~> ResponseString(args.runtimeJS)
    case Path("/favicon.ico") =>
      Ok
  }

  override val intent: Plan.Intent = { case x =>
    logAll(x)
    main.applyOrElse(x, (_: Req) => NotFound)
  }
}
