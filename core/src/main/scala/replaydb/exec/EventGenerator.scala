package replaydb.exec

import java.io.{InputStream, BufferedOutputStream, FileOutputStream}

import org.apache.commons.math3.random.MersenneTwister
import replaydb.io.SocialNetworkStorage
import replaydb.util.ProgressMeter
import replaydb.{TunableEventSource, UniformEventSource, util}

/**
 * Simple spam simulation event-generator functionality
 */
object EventGenerator extends App {

  object Generators extends Enumeration {
    type Generator = Value
    val Uniform = Value("uniform")
    val Tunable = Value("tunable")
  }

  if (args.length < 3 || args.length > 4) {
    println("Usage: EventGenerator ( uniform | tunable ) numUsers numEvents [ numSplits ]")
    System.exit(1)
  }

  val words = scala.io.Source.fromInputStream( getClass.getResourceAsStream("/US.txt") ).getLines.toArray

  val generator = Generators.withName(args(0))
  val numUsers = Integer.parseInt(args(1))
  val numEvents = Integer.parseInt(args(2))
  var startTime = util.Date.df.parse("2015-01-01 00:00:00.000").getTime
  val numSplits = if (args.length == 4) { Integer.parseInt(args(3)) } else { 1 }
  val rnd = new MersenneTwister(903485435L)
  val eventSource = generator match {
    case Generators.Uniform => new UniformEventSource(startTime, numUsers, rnd)
    case Generators.Tunable => new TunableEventSource(startTime, numUsers, rnd, words)
  }
  val eventStorage = new SocialNetworkStorage
  val pm = new ProgressMeter(printInterval = generator match {
    case Generators.Uniform => 1000000
    case Generators.Tunable => 1000000
  })
  val w = eventStorage.getSplitEventWriter("/tmp/events.out", numSplits)
  try {
    eventSource.genEvents(numEvents, e => {
      w.write(e)
      pm.increment()
    })
  } finally {
    w.close()
  }
  pm.finished()
}
