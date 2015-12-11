package replaydb.service

import java.net.InetSocketAddress
import java.util.concurrent.Executors

import com.typesafe.scalalogging.Logger
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.handler.logging.LoggingHandler
import org.jboss.netty.logging.{Slf4JLoggerFactory, InternalLoggerFactory, InternalLogLevel}
import org.slf4j.LoggerFactory
import replaydb.runtimedev.RuntimeStats
import replaydb.service.driver.{KryoCommands, Command}
import replaydb.util.{GarbageCollectorStats, PerfLogger, NetworkStats}

import scala.collection.mutable.ArrayBuffer

abstract class ServerBase(port: Int, stats: RuntimeStats) {
  InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory)
  val logger = Logger(LoggerFactory.getLogger(classOf[ServerBase]))
  val networkStats = new NetworkStats()
  val garbageCollectorStats = new GarbageCollectorStats()
  var f: Channel = _
  var closeRunnable: Runnable = _

  networkStats.reset()
  garbageCollectorStats.reset()

  def getHandler(): ChannelHandler

  def run(): Unit = {
    logger.info(s"starting server on port $port")
    val b = new ServerBootstrap(new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()))
    b.setPipelineFactory(new ChannelPipelineFactory {
      override def getPipeline: ChannelPipeline = {
        val p = org.jboss.netty.channel.Channels.pipeline()
        p.addLast("Logger", new LoggingHandler(InternalLogLevel.DEBUG))
        val encoder = new KryoCommandEncoder(networkStats)
        val decoder = new KryoCommandDecoder(networkStats)
        encoder.registerTypes(stats.getKryoTypes)
        decoder.registerTypes(stats.getKryoTypes)
        p.addLast("KryoEncoder", encoder)

        // Decode commands received from clients
        p.addLast("KryoDecoder", decoder)
        p.addLast("Handler", getHandler())
        p
      }
    })
    b.setOption("tcpNoDelay", true)
//    b.setOption("")
//      .option(ChannelOption.SO_BACKLOG.asInstanceOf[ChannelOption[Any]], 100)
    f = b.bind(new InetSocketAddress(port))
    closeRunnable = new Runnable() {
      override def run(): Unit = {
        try {
          f.close().sync()
        } finally {
          b.releaseExternalResources()
        }
        PerfLogger.log(s"Garbage collection stats: $garbageCollectorStats")
      }
    }
  }

  def close(): Unit = {
    closeRunnable.run()
  }

  def write(c: Command): Unit = {
    f.write(c) //.sync()
  }
}
