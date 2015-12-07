package replaydb.exec.spam

import org.scalatest.FlatSpec

/**
 *
 * Example commands for generating datafiles:
 *
 * runMain replaydb.exec.EventGenerator tunable 100000 500000 /Users/johann/tmp/events-500k-split-1/events.out 1
 * runMain replaydb.exec.EventGenerator tunable 100000 500000 /Users/johann/tmp/events-500k-split-2/events.out 2
 * runMain replaydb.exec.EventGenerator tunable 100000 500000 /Users/johann/tmp/events-500k-split-4/events.out 4
 *
 */
class DistributedSpamDetectorSpec extends FlatSpec with TestRunner {

//  it should "run with a two-way split" in {
//    runSpamDetector(2, classOf[SpamDetectorStats], DataDesc.SHORT)
//  }

}
