package replaydb.runtimedev

import replaydb.runtimedev.CoordinatorInterface

trait ReplayValue[T] extends ReplayState {
  def merge(ts: Long, value: T => T)(implicit coordinator: CoordinatorInterface): Unit
  def get(ts: Long)(implicit coordinator: CoordinatorInterface): Option[T]
}
