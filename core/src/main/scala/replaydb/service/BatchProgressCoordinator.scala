package replaydb.service

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.collection.mutable

class BatchProgressCoordinator(startTimestamp: Long, batchTimeInterval: Long) {
  val logger = Logger(LoggerFactory.getLogger(classOf[BatchProgressCoordinator]))
  trait ThreadCoordinator {
    def awaitAdvance(ts: Long): Unit
  }

  def tsToBatch(ts: Long): String = {
    s"$ts:B(${(ts - startTimestamp) / batchTimeInterval})"
  }

  def update(phaseId: Int, maxTimestamp: Long): Unit = {
    x(phaseId).update(maxTimestamp)
  }

  class PhaseLimit(phaseId: Int) extends ThreadCoordinator {
    var maxTimestamp: Long = startTimestamp
    def update(maxTimestamp: Long): Unit = {
      logger.info(s"update limit at phase $phaseId to $maxTimestamp")
      this.synchronized {
        if (maxTimestamp > this.maxTimestamp) {
          this.maxTimestamp = maxTimestamp
          notifyAll()
        }
      }
    }
    def awaitAdvance(ts: Long): Unit = {
      this.synchronized {
        while (ts > maxTimestamp) {
          logger.info(s"awaiting advance for phase $phaseId, time requested ${tsToBatch(ts)}, time allowed ${tsToBatch(maxTimestamp)}")
          wait()
        }
//        logger.info(s"test condition(post): ${(if (phaseId == 1) ts - batchTimeInterval else ts) < maxTimestamp} $phaseId $ts $batchTimeInterval $ts")
        logger.info(s"advancing for phase $phaseId, time requested ${tsToBatch(ts)}, time allowed ${tsToBatch(maxTimestamp)}")
      }
    }
  }

  val x = mutable.HashMap[Int, PhaseLimit]()

  def getCoordinator(phaseId: Int): ThreadCoordinator = {
    if (!x.contains(phaseId)) {
      x += phaseId -> new PhaseLimit(phaseId)
    }
    x(phaseId)
  }
}
