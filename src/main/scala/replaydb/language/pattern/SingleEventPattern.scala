package replaydb.language.pattern

import replaydb.language.bindings.TimeIntervalBinding
import replaydb.language.{Match}
import replaydb.language.event.Event

// Matches a single event (not a sequence) though you may specify multiple
// match possibilities
class SingleEventPattern[T <: Event](event: T, otherEvents: T*) extends Pattern {

  def events = Set(otherEvents:_*) + event
  var _interval: TimeIntervalBinding = _
  def interval = _interval
  def interval_=(binding: TimeIntervalBinding) = _interval = binding

  def followedBy(p: SingleEventPattern[_ <: Event]): SequencePatternWithoutInterval = {
    val sp = new SequencePattern(this)
    sp followedBy p
  }

  def or[S >: T <: Event](p: S): SingleEventPattern[S] = {
    new SingleEventPattern(p, events.toSeq:_*)
  }

  override def getMatches: Seq[Match[T]] = {
//    EventStore.get.filter(_.equals(event))
    Seq()
  }

  override def toString: String = {
    if (events.size == 1)
      event.toString + " " + _interval
    else
      events.mkString("(", " OR ", ")") + " " + _interval
  }
}
