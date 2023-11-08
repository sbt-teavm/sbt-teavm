package sbtteavm

import org.teavm.tooling.TeaVMToolLog
import sbt.internal.util.ManagedLogger

private[sbtteavm] class TeaVMLog(log: ManagedLogger) extends TeaVMToolLog {
  override def info(s: String): Unit =
    log.info(s)

  override def debug(s: String): Unit =
    log.debug(s)

  override def warning(s: String): Unit =
    log.warn(s)

  override def error(s: String): Unit =
    log.error(s)

  override def info(s: String, e: Throwable): Unit = {
    e.printStackTrace()
    log.info(s)
  }

  override def debug(s: String, e: Throwable): Unit = {
    e.printStackTrace()
    log.debug(s)
  }

  override def warning(s: String, e: Throwable): Unit = {
    e.printStackTrace()
    log.warn(s)
  }

  override def error(s: String, e: Throwable): Unit = {
    e.printStackTrace()
    log.error(s)
  }
}
