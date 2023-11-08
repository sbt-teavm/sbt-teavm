package sbtteavm

import org.teavm.tooling.daemon.DaemonLog
import sbt.internal.util.ManagedLogger

private[sbtteavm] final class DaemonLogImpl(log: ManagedLogger) extends DaemonLog {
  def error(message: String): Unit = {
    log.error(message)
  }

  def error(message: String, e: Throwable): Unit = {
    log.error(message)
    e.printStackTrace()
  }

  def info(message: String): Unit = {
    log.info(message)
  }
}
