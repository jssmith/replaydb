package replaydb.exec.spam

import java.io.FileInputStream

import replaydb.io.SocialNetworkStorage
import replaydb.runtimedev.PrintSpamCounter
import replaydb.runtimedev.serialImpl.ReplayStateFactory
import replaydb.util.ProgressMeter

/**
 * Single-threaded implementation of spam detector
 */
object SerialSpamDetector extends App {

  if (args.length != 1) {
    println("Usage: SerialSpamDetector filename")
    System.exit(1)
  }

  val inputFilename = args(0)
  val eventStorage = new SocialNetworkStorage
  val stats = new SpamDetectorStats(new ReplayStateFactory)
  val si = stats.getRuntimeInterface
  var lastTimestamp = 0L
  val pm = new ProgressMeter(printInterval = 1000000, () => { si.updateAllPhases(new PrintSpamCounter(lastTimestamp)); ""})
  val r = eventStorage.readEvents(new FileInputStream(inputFilename), e => {
    si.updateAllPhases(e)
    lastTimestamp = e.ts
    pm.increment()
  })
  pm.finished()
  println("Final spam count: " + stats.spamCounter.get(Long.MaxValue))
}
