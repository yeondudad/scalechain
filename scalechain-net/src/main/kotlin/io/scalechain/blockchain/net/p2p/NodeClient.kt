package io.scalechain.blockchain.net

import com.typesafe.scalalogging.Logger
import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory

import java.io.BufferedReader
import java.io.InputStreamReader

import io.scalechain.util.ExceptionUtil
import io.scalechain.util.StackUtil
import org.slf4j.LoggerFactory


/**
  * Simple SSL chat client.
  */
class NodeClient(peerSet : PeerSet) : AutoCloseable {
  private val logger = LoggerFactory.getLogger(NodeClient::class.java)

  protected<net> val group : EventLoopGroup = NioEventLoopGroup()

  fun connect(address : String, port : Int) : ChannelFuture {
    // Configure SSL.
    val sslCtx : SslContext = SslContextBuilder.forClient()
      // TODO : BUGBUG : Do not use an insecure trust manager.
      // From the comment of InsecureTrustManagerFactory :
      //   An insecure that trusts all X.509 certificates without any verification.
      .trustManager(InsecureTrustManagerFactory.INSTANCE).build()

    val b : Bootstrap = Bootstrap()
       b.group(group)
      .channel(classOf<NioSocketChannel>)
      .option(ChannelOption.SO_KEEPALIVE, Boolean.box(true))
      .handler(LoggingHandler(LogLevel.INFO))
      .handler(NodeClientInitializer(sslCtx, address, port, peerSet))

    // Start the connection attempt.
    //val channel : Channel = b.connect(address, port).sync().channel()
    val channelFuture : ChannelFuture = b.connect(address, port)

    channelFuture.addListener(ChannelFutureListener() {
      fun operationComplete(future:ChannelFuture) {
        assert( future.isDone )
        if (future.isSuccess) { // completed successfully
          logger.info(s"Successfully connected to ${address}:${port}")
        }

        if (future.cause() != null) { // completed with failure
          val causeDescription = ExceptionUtil.describe( future.cause.getCause )

          logger.info(s"Failed to connect to ${address}:${port}. Exception : ${future.cause.getMessage}")
        }

        if (future.isCancelled) { // completed by cancellation
          logger.info(s"Canceled to connect to ${address}:${port}")
        }
      }
    })
  }

  fun close() : Unit {
    group.shutdownGracefully()
  }
}