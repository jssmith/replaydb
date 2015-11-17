package replaydb.exec.spam

import replaydb.runtimedev.MemoryStats
import replaydb.runtimedev.threadedImpl.{MultiReaderEventSource, RunProgressCoordinator}
import replaydb.util.ProgressMeter

object ParallelSpamDetector extends App {

  if (args.length != 5) {
    println(
      """Usage: ParallelSpamDetector baseFilename numPartitions coordinationInterval maxInProgressEvents gcInterval
        |  Suggested values: numPartitions = 4, coordinationInterval = 2000, maxInProgressEvents = 20000, gcInterval = 500000
      """.stripMargin)
    System.exit(1)
  }
  val partitionFnBase = args(0)
  val numPartitions = args(1).toInt
  val coordinationInterval = args(2).toInt
  val maxInProgressEvents = args(3).toInt
  val gcInterval = args(4).toInt

  val stats = new SpamDetectorStats(true)
  val si = stats.getRuntimeInterface
  val numPhases = si.numPhases
  val barrier = new RunProgressCoordinator(numPartitions = numPartitions, numPhases = numPhases, maxInProgressEvents = maxInProgressEvents)
  barrier.registerReplayStates(stats.getAllReplayStates)
  val overallProgressMeter = new ProgressMeter(1000000, name = Some("Overall Progress"))
  val readerThreads = (for (partitionId <- 0 until numPartitions) yield {
    new MultiReaderEventSource(s"$partitionFnBase-$partitionId", numPhases, bufferSize = 100000)
  }).toArray
  val threads =
    for (partitionId <- 0 until numPartitions; phaseId <- 0 until si.numPhases) yield {
      new Thread(new Runnable {
        val b = barrier.getCoordinatorInterface(partitionId, phaseId)
        override def run(): Unit = {
          var lastTimestamp = 0L
          val pm = new ProgressMeter(printInterval = 1000000, () => s"${MemoryStats.getStats()}", name = Some(s"$partitionId-$phaseId"))
          var ct = 0L
          var limitTs = b.requestProgress(0, ct)
          readerThreads(partitionId).readEvents(e => {
            while (e.ts > limitTs) {
              limitTs = b.requestProgress(e.ts, ct)
            }
            si.update(phaseId, e)
            ct += 1
            if (ct % coordinationInterval == 0) {
              if (phaseId == numPhases - 1) {
                overallProgressMeter.synchronized { overallProgressMeter.add(coordinationInterval) }
              }
              b.update(e.ts - 1, ct)
            }
            lastTimestamp = e.ts
            pm.increment()
            if (partitionId == numPartitions - 1 && phaseId == numPhases - 1) {
              if (ct % gcInterval == 0) {
                b.gcAllReplayState()
              }
              if (ct % (1000 * numPartitions) == 1000 * partitionId) {
                si.update(new PrintSpamCounter(lastTimestamp))
              }
            }
          })
          if (phaseId == numPhases - 1) {
            overallProgressMeter.synchronized { overallProgressMeter.add((ct % coordinationInterval).toInt) }
          }
          b.finished()
          pm.finished()
        }
      }, s"process-events-$partitionId-$phaseId")
    }
  readerThreads.foreach(_.start())
  threads.foreach(_.start())
  readerThreads.foreach(_.join())
  threads.foreach(_.join())
  overallProgressMeter.synchronized{ overallProgressMeter.finished() }
  println("Final spam count: " + stats.spamCounter.get(Long.MaxValue))
}
