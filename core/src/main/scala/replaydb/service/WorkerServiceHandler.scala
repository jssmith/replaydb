package replaydb.service

import java.io.FileInputStream

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory
import replaydb.io.SocialNetworkStorage
import replaydb.runtimedev.{HasRuntimeInterface, ReplayState}
import replaydb.runtimedev.distributedImpl.ReplayStateFactory
import replaydb.service.driver._
import com.typesafe.scalalogging.Logger


class WorkerServiceHandler(server: Server) extends ChannelInboundHandlerAdapter {
  val logger = Logger(LoggerFactory.getLogger(classOf[WorkerServiceHandler]))
  val stateFactory = new ReplayStateFactory
  var batchProgressCoordinator: BatchProgressCoordinator = null

  override def channelRead(ctx: ChannelHandlerContext, msg: Object): Unit = {
    logger.info(s"received message $msg")
    msg.asInstanceOf[Command] match {
      case c : InitReplayCommand[_] => {
        val constructor = Class.forName(c.programClass).getConstructor(classOf[replaydb.runtimedev.ReplayStateFactory])
        val program = constructor.newInstance(stateFactory)
        val runtime = program.asInstanceOf[HasRuntimeInterface].getRuntimeInterface
        var deltaMap = Map[ReplayState, ReplayState]()
        batchProgressCoordinator = new BatchProgressCoordinator(c.startTimestamp, c.batchTimeInterval)

        logger.info(s"launching threads... number of partitions: ${c.files.size}, number of phases ${runtime.numPhases}")
        val threads =
          for ((partitionId, partitionFn) <- c.files; phaseId <- 1 to runtime.numPhases) yield {
            new Thread(s"replay-$partitionId-$phaseId") {
              val progressCoordinator = batchProgressCoordinator.getCoordinator(phaseId)
              override def run(): Unit = {
                // TODO
                //  - separate reader thread
                try {
                  logger.info(s"starting replay on partition $partitionId (phase $phaseId), will update at interval ${c.printProgressInterval}")
                  var batchEndTimestamp = c.startTimestamp
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
                      }
                    })
                  }
                  eventStorage.readEvents(new FileInputStream(partitionFn), e => {
                    if (e.ts >= batchEndTimestamp) {
                      logger.info(s"reached batch end on partition $partitionId phase $phaseId")
                      if (ct > 0) {
                        sendProgress()
                      }
                      batchEndTimestamp += c.batchTimeInterval
                      logger.info(s"advancing endtime $batchEndTimestamp on partition $partitionId phase $phaseId")
                      progressCoordinator.awaitAdvance(batchEndTimestamp)
                    }
                    lastTimestamp = e.ts
//                    runtime.update(partitionId, phaseId, e, null)
                    ct += 1
                    if (ct % c.printProgressInterval == 0) {
                      logger.info(s"progress on partition $partitionId: $ct ${lastTimestamp-1}")
                    }
                  })
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
          batchProgressCoordinator.update(phaseId, maxTimestamp)
        }
      }

      case _ : CloseCommand => ctx.close()
    }
    // TODO is this needed?
    ReferenceCountUtil.release(msg)
  }

   override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
     cause.printStackTrace()
     ctx.close()
   }
 }