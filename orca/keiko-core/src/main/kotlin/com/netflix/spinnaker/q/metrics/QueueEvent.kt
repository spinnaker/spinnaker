/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.q.metrics

import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.Queue
import java.time.Duration
import java.time.Instant

/**
 * Events that may be emitted by a [Queue].
 */
sealed class QueueEvent

/**
 * A sub-type of [QueueEvent] that includes a message.
 */
sealed class PayloadQueueEvent(val payload: Message) : QueueEvent()

object QueuePolled : QueueEvent()
object RetryPolled : QueueEvent()
data class MessagePushed(val payload: Message) : QueueEvent()
object MessageAcknowledged : QueueEvent()
object MessageRetried : QueueEvent()
data class MessageProcessing(val payload: Message, val lag: Duration) : QueueEvent() {
  constructor(payload: Message, scheduledTime: Instant, now: Instant) :
    this(payload, Duration.between(scheduledTime, now))
}

object MessageDead : QueueEvent()
data class MessageDuplicate(val payload: Message) : QueueEvent()
object LockFailed : QueueEvent()
object NoHandlerCapacity : QueueEvent()
data class MessageRescheduled(val payload: Message) : QueueEvent()
data class MessageNotFound(val payload: Message) : QueueEvent()
data class HandlerThrewError(val payload: Message) : QueueEvent()
