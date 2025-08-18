package sbtteavm

import java.io.File
import xsbti.FileConverter
import xsbti.VirtualFileRef

private[sbtteavm] object SbtTeaVMCompat {
  def toFile(file: VirtualFileRef, converter: FileConverter): File =
    converter.toPath(file).toFile
}
