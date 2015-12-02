package replaydb.service

import java.io.FileInputStream
import java.util.concurrent.{TimeUnit, CountDownLatch}

import com.typesafe.scalalogging.Logger
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory
import replaydb.io.SocialNetworkStorage
import replaydb.runtimedev.{PrintSpamCounter, CoordinatorInterface, HasRuntimeInterface}
import replaydb.runtimedev.distributedImpl._
import replaydb.service.driver._


class WorkerServiceHandler(server: Server) extends ChannelInboundHandlerAdapter {
  val logger = Logger(LoggerFactory.getLogger(classOf[WorkerServiceHandler]))
  var batchProgressCoordinator: BatchProgressCoordinator = null
  var stateCommunicationService: StateCommunicationService = null

  val startLatch = new CountDownLatch(5) // TODO this should be numPhases

  override def channelRead(ctx: ChannelHandlerContext, msg: Object): Unit = {
    logger.info(s"received message $msg")
    msg.asInstanceOf[Command] match {
      case c : InitReplayCommand[_] => {
        if (stateCommunicationService != null) {
          stateCommunicationService.close()
        }
        stateCommunicationService = new StateCommunicationService(c.hostId, c.runConfiguration)
        val stateFactory = new ReplayStateFactory(stateCommunicationService)
        val constructor = Class.forName(c.programClass).getConstructor(classOf[replaydb.runtimedev.ReplayStateFactory])
        val runConfig = c.runConfiguration
        val program = constructor.newInstance(stateFactory)
        val runtime = program.asInstanceOf[HasRuntimeInterface].getRuntimeInterface
        batchProgressCoordinator = new BatchProgressCoordinator(runConfig.startTimestamp, runConfig.batchTimeInterval)

        val partitionId = c.partitionId
        logger.info(s"launching threads... number of phases ${runConfig.numPhases}")
        val threads =
          for (phaseId <- 1 to runtime.numPhases) yield {
            new Thread(s"replay-$partitionId-$phaseId") {
              val progressCoordinator = batchProgressCoordinator.getCoordinator(phaseId)
              // TODO
              implicit val coordinator = new CoordinatorInterface(partitionId, phaseId) {

                var bets: Long = 0

                def setBatchEndTs(ts: Long): Unit = {
                  bets = ts
                }

                override def batchEndTs: Long = bets

                override def gcAllReplayState(): Unit = ???

                // TODO should only have one of these two
                override def batchId: Int = ???

                override def reportCheckpoint(ts: Long, ct: Long): (Long, Long) = ???

                override def reportFinished(): Unit = ???
              }
              override def run(): Unit = {
                // TODO
                //  - separate reader thread
                try {
                  Thread.sleep(5000) // TODO
                  println(s"WORKER THREAD PHASE $phaseId STARTLATCH COUNT IS: " + startLatch.getCount)
//                  startLatch.countDown()
                  println(s"WORKER THREAD PHASE $phaseId AFTER COUNTDOWN STARTLATCH COUNT IS: " + startLatch.getCount)

                  logger.info(s"starting replay on partition $partitionId (phase $phaseId)")
                  var batchEndTimestamp = c.runConfiguration.startTimestamp
                  val eventStorage = new SocialNetworkStorage
                  var lastTimestamp = Long.MinValue
                  var ct = 0
                  def sendProgress(done: Boolean = false): Unit = {
                    val progressUpdateCommand = new ProgressUpdateCommand(partitionId, phaseId, ct, batchEndTimestamp, done)
                    logger.info(s"preparing progress update $progressUpdateCommand)")
                    ctx.executor.execute(new Runnable() {
                      override def run(): Unit = {
                        logger.info(s"sending progress update $progressUpdateCommand)")
                        ctx.writeAndFlush(progressUpdateCommand).sync()
                        logger.debug(s"finished sending progress update")
                      }
                    })
                  }
                  eventStorage.readEvents(new FileInputStream(c.filename), e => {
                    if (e.ts >= batchEndTimestamp) {
                      logger.info(s"reached batch end on partition $partitionId phase $phaseId")
                      // Send out StateUpdateCommands
                      stateCommunicationService.finalizeBatch(phaseId, batchEndTimestamp)
                      if (ct > 0) {
                        sendProgress()
                      }
                      batchEndTimestamp += runConfig.batchTimeInterval
                      coordinator.setBatchEndTs(batchEndTimestamp)
                      logger.info(s"advancing endtime $batchEndTimestamp on partition $partitionId phase $phaseId")
                      progressCoordinator.awaitAdvance(batchEndTimestamp)
                    }
                    lastTimestamp = e.ts
                    runtime.update(e)
                    ct += 1
                  })
                  // TODO need a better way to extract info than this
                  runtime.update(PrintSpamCounter(batchEndTimestamp - 1))
                  stateCommunicationService.finalizeBatch(phaseId, batchEndTimestamp)
                  logger.info(s"finished phase $phaseId on partition $partitionId")
                  // Send progress for all phases, but only set done flag when the last phase is done
                  sendProgress(phaseId == runtime.numPhases)
                } catch {
                  case e: Exception => e.printStackTrace()
                  case e: Throwable => e.printStackTrace()
                }
              }
            }
          }
        threads.foreach(_.start())
//        threads.foreach(_.join())
      }

      case uap: UpdateAllProgressCommand => {
        logger.info(s"have new progress marks ${uap.progressMarks}")
        for ((phaseId, maxTimestamp) <- uap.progressMarks) {
          if (batchProgressCoordinator != null) {
            batchProgressCoordinator.update(phaseId, maxTimestamp)
          }
        }
      }

      case srp: StateRequestResponse => {
        stateCommunicationService.handleStateRequestResponse(srp)
      }

      case suc: StateUpdateCommand => {
//        while (startLatch.getCount != 0) {
//          println("STARTLATCH COUNT IS: " + startLatch.getCount)
//          startLatch.await(5, TimeUnit.SECONDS)
//        }
        logger.info(s"STARTING TO HANDLE StateUpdateCommand: $suc")
        stateCommunicationService.handleStateUpdateCommand(suc)
        logger.info(s"FINISHED HANDLING StateUpdateCommand: $suc")
      }

      case _ : CloseWorkerCommand => {
        ctx.close()
      }

      case _ : CloseCommand => {
        stateCommunicationService.close()
        stateCommunicationService = null
        batchProgressCoordinator = null
        ctx.close()
      }
    }
    // TODO is this needed?
    ReferenceCountUtil.release(msg)
  }

   override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
     cause.printStackTrace()
     ctx.close()
   }
 }
