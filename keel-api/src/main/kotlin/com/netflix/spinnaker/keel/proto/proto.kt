/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
