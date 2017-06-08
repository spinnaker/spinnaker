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

package com.netflix.spinnaker.orca.q.metrics

import com.netflix.spinnaker.orca.time.toInstant
import org.springframework.context.ApplicationEvent

sealed class QueueEvent(source: MonitorableQueue) : ApplicationEvent(source) {
  val instant
    get() = timestamp.toInstant()
}

class QueuePolled(source: MonitorableQueue) : QueueEvent(source)
class RetryPolled(source: MonitorableQueue) : QueueEvent(source)
class MessagePushed(source: MonitorableQueue) : QueueEvent(source)
class MessageAcknowledged(source: MonitorableQueue) : QueueEvent(source)
class MessageRetried(source: MonitorableQueue) : QueueEvent(source)
class MessageDead(source: MonitorableQueue) : QueueEvent(source)
class MessageDuplicate(source: MonitorableQueue) : QueueEvent(source)

