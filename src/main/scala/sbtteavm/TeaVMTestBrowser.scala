package sbtteavm

sealed abstract class TeaVMBrowser(private[sbtteavm] val value: String) extends Product with Serializable

object TeaVMBrowser {
  case object Custom extends TeaVMBrowser("browser")
  case object Chrome extends TeaVMBrowser("browser-chrome")
  case object Firefox extends TeaVMBrowser("browser-firefox")
}
