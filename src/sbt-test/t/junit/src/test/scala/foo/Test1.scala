package foo

import org.teavm.junit.SkipJVM
import org.teavm.junit.TeaVMTestRunner
import org.junit.runner.RunWith
import org.junit.Test
import org.teavm.classlib.PlatformDetector.*

@SkipJVM
@RunWith(classOf[TeaVMTestRunner])
class Test1 {

  private def showInfo(): String = Seq(
    "os" -> System.getProperty("os.name"),
    "class" -> this.getClass.getName,
    "isWebAssembly" -> isWebAssembly(),
    "isJavaScript" -> isJavaScript(),
    "isC" -> isC(),
  ).mkString(",")

  @Test
  def x1(): Unit = {
    println(showInfo())
  }
}
