package sbtteavm

import java.io.File

sealed abstract class TeaVMJUnitOpt(val key: String) extends Product with Serializable {
  final def fullKey: String = s"teavm.junit.${key}"
  final def asString: String = s"-D${fullKey}=${asValue}"
  def asValue: String
}

object TeaVMJUnitOpt {
  final case class Target(value: File) extends TeaVMJUnitOpt("target") {
    override def asValue: String = value.getAbsolutePath
  }
  final case class JsRunner(value: TeaVMBrowser) extends TeaVMJUnitOpt("js.runner") {
    override def asValue: String = value.value
  }
  final case class WasmRunner(value: TeaVMBrowser) extends TeaVMJUnitOpt("wasm.runner") {
    override def asValue: String = value.value
  }
  final case class Js(value: Boolean) extends TeaVMJUnitOpt("js") {
    override def asValue: String = java.lang.Boolean.toString(value)
  }
  final case class JsDecodeStack(value: Boolean) extends TeaVMJUnitOpt("js.decodeStack") {
    override def asValue: String = java.lang.Boolean.toString(value)
  }
  final case class C(value: Boolean) extends TeaVMJUnitOpt("c") {
    override def asValue: String = java.lang.Boolean.toString(value)
  }
  final case class Wasm(value: Boolean) extends TeaVMJUnitOpt("wasm") {
    override def asValue: String = java.lang.Boolean.toString(value)
  }
  final case class Wasi(value: Boolean) extends TeaVMJUnitOpt("wasi") {
    override def asValue: String = java.lang.Boolean.toString(value)
  }
  final case class WasiRunner(value: File) extends TeaVMJUnitOpt("wasi.runner") {
    override def asValue: String = value.getAbsolutePath
  }
  final case class CCompiler(value: File) extends TeaVMJUnitOpt("c.compiler") {
    override def asValue: String = value.getAbsolutePath
  }
  final case class CLineNumbers(value: Boolean) extends TeaVMJUnitOpt("c.lineNumbers") {
    override def asValue: String = java.lang.Boolean.toString(value)
  }
  final case class Minified(value: Boolean) extends TeaVMJUnitOpt("minified") {
    override def asValue: String = java.lang.Boolean.toString(value)
  }
  final case class Optimized(value: Boolean) extends TeaVMJUnitOpt("optimized") {
    override def asValue: String = java.lang.Boolean.toString(value)
  }
  final case class SourceDirs(value: Seq[File]) extends TeaVMJUnitOpt("sourceDirs") {
    override def asValue: String = value.iterator.map(_.getAbsolutePath).mkString(File.pathSeparator)
  }
}
