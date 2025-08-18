package sbtteavm

import java.io.File
import xsbti.FileConverter

private[sbtteavm] object SbtTeaVMCompat {
  def toFile(file: File, converter: FileConverter): File = file

  implicit class DefOps(private val self: sbt.Def.type) extends AnyVal {
    def uncached[A](a: A): A = a
  }
}
