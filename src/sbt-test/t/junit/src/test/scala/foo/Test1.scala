package foo

import org.teavm.junit.SkipJVM
import org.teavm.junit.TeaVMTestRunner
import org.junit.runner.RunWith
import org.junit.Test
import org.teavm.classlib.PlatformDetector.*

@SkipJVM
@RunWith(classOf[TeaVMTestRunner])
class Test1 {

  private def osname(): String = System.getProperty("os.name")
  private def showInfo(): String = Seq(
    "isWebAssembly" -> isWebAssembly(),
    "isJavaScript" -> isJavaScript(),
    "isC" -> isC(),
  ).filter(_._2).map(_._1).mkString(",")

  @Test
  def x1(): Unit = {
    println(showInfo())
    assert(isWebAssembly() || isJavaScript() || isC())
    assert(osname() == "TeaVM", osname())
  }
}
