package replaydb.runtimedev.threadedImpl

import replaydb.runtimedev.{ReplayDelta, ReplayCounter}


class ReplayCounterImpl extends ReplayCounter {

  val replayValue = new ReplayValueImpl[Long](0L)

  override def add(value: Long, ts: Long): Unit = {
    replayValue.merge(ts, _ + value)
  }
  override def get(ts: Long): Long = {
    replayValue.getOption(ts) match {
      case Some(x) => x
      case None => 0
    }
  }

  override def gcOlderThan(ts: Long): Int = {
    replayValue.gcOlderThan(ts)
  }

  override def merge(rd: ReplayDelta): Unit = {
    val delta = rd.asInstanceOf[ReplayCounterDelta]
    replayValue.merge(delta.replayDelta)

  }

  override def getDelta: ReplayCounterDelta = {
    new ReplayCounterDelta
  }
}
