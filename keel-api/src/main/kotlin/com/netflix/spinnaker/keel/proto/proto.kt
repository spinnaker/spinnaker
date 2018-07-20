package com.netflix.spinnaker.keel.proto

import com.google.protobuf.Message
import io.grpc.ManagedChannel
import io.grpc.Server
import java.util.concurrent.TimeUnit

/*
 * Extensions for gRPC / Protobuf
 */

/**
 * Name collision with [kotlin.Any] is way too confusing otherwise.
 */
typealias AnyMessage = com.google.protobuf.Any

inline fun <reified T : Message> AnyMessage.isA() = `is`(T::class.java)
inline fun <reified T : Message> AnyMessage.unpack(): T = unpack(T::class.java)
fun Message.pack(): AnyMessage = AnyMessage.pack(this)

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
