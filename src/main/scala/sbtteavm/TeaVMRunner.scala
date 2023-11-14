package sbtteavm

import java.io.File
import sbt.*

sealed abstract class TeaVMRunner extends Product with Serializable {
  private[sbtteavm] def withPath[A](f: File => A): A = {
    this match {
      case TeaVMRunner.Script(value) =>
        IO.withTemporaryDirectory { tmp =>
          val x = tmp / "run.sh"
          IO.write(x, value)
          x.setExecutable(true)
          f(x)
        }
      case TeaVMRunner.FilePath(value) =>
        f(value)
    }
  }
}

object TeaVMRunner {
  final case class Script(value: String) extends TeaVMRunner
  final case class FilePath(value: File) extends TeaVMRunner
}
