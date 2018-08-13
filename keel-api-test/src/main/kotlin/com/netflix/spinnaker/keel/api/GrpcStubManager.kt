package com.netflix.spinnaker.keel.api

import com.netflix.appinfo.InstanceInfo
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.stub.AbstractStub
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.SECONDS

class GrpcStubManager<S : AbstractStub<S>>(private val newStub: (ManagedChannel) -> S) {
  private var server: Server? = null

  fun startServer(config: ServerBuilder<*>.() -> Unit) {
    server = ServerBuilder.forPort(0).apply(config).build().start()
  }

  fun stopServer() {
    server?.shutdownWithin(5, SECONDS)
  }

  fun <R> withChannel(block: (S) -> R): R =
    server?.let { server ->
      val channel = ManagedChannelBuilder
        .forTarget("localhost:${server.port}")
        .usePlaintext()
        .build()
      val stub = newStub(channel)

      try {
        block(stub)
      } finally {
        channel.shutdownWithin(5, SECONDS)
      }
    }
      ?: throw IllegalStateException("You need to start the server before opening a channel")

  val port: Int
    get() = server?.port ?: throw IllegalStateException("server is not started")

}

/**
 * Eureka-style address for the GRPC server.
 */
val GrpcStubManager<*>.instanceInfo: InstanceInfo
  get() = InstanceInfo.Builder
    .newBuilder()
    .setAppName("grpc")
    .setIPAddr("localhost")
    .setPort(port)
    .build()

fun Server.shutdownWithin(timeout: Long, unit: TimeUnit) {
  shutdown()
  try {
    assert(awaitTermination(timeout, unit)) { "Server cannot be shut down gracefully" }
  } finally {
    shutdownNow()
  }
}

fun ManagedChannel.shutdownWithin(timeout: Long, unit: TimeUnit) {
  shutdown()
  try {
    assert(awaitTermination(timeout, unit)) { "Channel cannot be shut down gracefully" }
  } finally {
    shutdownNow()
  }
}
