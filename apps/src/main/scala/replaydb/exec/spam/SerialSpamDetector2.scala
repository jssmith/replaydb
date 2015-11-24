package replaydb.exec.spam

import replaydb.runtimedev.{ReplayState, ReplayDelta}
import replaydb.runtimedev.threadedImpl.MultiReaderEventSource
import replaydb.util.ProgressMeter

/**
 * Uses a separate thread to deserialize saved event history, increasing performance.
 */
object SerialSpamDetector2 extends App {

  if (args.length != 1) {
    println("Usage: SerialSpamDetector2 filename")
    System.exit(1)
  }

  val inputFilename = args(0)
  val stats = new SpamDetectorStatsParallel(false)
  val si = stats.getRuntimeInterface
  var lastTimestamp = 0L
  val deltaMap: Map[ReplayState, ReplayDelta] = Map().withDefault(_.getDelta)
  val pm = new ProgressMeter(printInterval = 1000000, () => { si.update(0, new PrintSpamCounter(lastTimestamp), deltaMap); ""})
  val es = new MultiReaderEventSource(inputFilename, 1, 100000)
  es.start()
  es.readEvents(e => {
    si.update(0, e, deltaMap)
    lastTimestamp = e.ts
    pm.increment()
  })
  pm.finished()
  println("Final spam count: " + stats.spamCounter.get(Long.MaxValue))
}
