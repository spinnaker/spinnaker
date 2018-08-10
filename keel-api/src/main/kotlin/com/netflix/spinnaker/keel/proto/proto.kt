package com.netflix.spinnaker.keel.proto

import com.google.protobuf.Message

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
