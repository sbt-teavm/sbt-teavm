package foo

import org.teavm.junit.TeaVMTestRunner
import org.junit.runner.RunWith
import org.junit.Test
import org.junit.Assert.*
import org.teavm.classlib.PlatformDetector.*

@RunWith(classOf[TeaVMTestRunner])
class Test1 {
  @Test
  def x1(): Unit = {
    println(
      "Scala test " +
      System.getProperty("os.name") +
      " isWebAssembly = " + isWebAssembly() +
      " isJavaScript = " + isJavaScript() +
      " isC = " + isC() +
      " isLowLevel = " + isLowLevel() +
      ""
    )
  }
}
