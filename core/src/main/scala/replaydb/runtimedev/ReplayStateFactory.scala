package replaydb.runtimedev

import scala.reflect.ClassTag

trait ReplayStateFactory {
  val useParallel: Boolean

  def getReplayMap[K, V : ClassTag](default: => V): ReplayMap[K, V]

  def getReplayCounter: ReplayCounter

  def getReplayTimestampLocalMap[K, V](default: => V): ReplayTimestampLocalMap[K, V]
}
