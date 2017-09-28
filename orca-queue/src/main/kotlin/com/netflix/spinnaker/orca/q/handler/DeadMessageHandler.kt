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

package com.netflix.spinnaker.orca.q.handler

import com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.q.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component open class DeadMessageHandler {
  private val log = LoggerFactory.getLogger(javaClass)

  open fun handle(queue: Queue, message: Message) {
    log.error("Dead message: $message")
    terminationMessageFor(message)
      ?.let {
        it.setAttribute(DeadMessageAttribute)
        queue.push(it)
      }
  }

  private fun terminationMessageFor(message: Message): Message? {
    if (message.hasAttribute<DeadMessageAttribute>()) {
      log.warn("Already sent $message to DLQ")
      return null
    }
    return when (message) {
      is TaskLevel -> CompleteTask(message, TERMINAL)
      is StageLevel -> CompleteStage(message, TERMINAL)
      is ExecutionLevel -> CompleteExecution(message)
      else -> {
        log.error("Unhandled message type ${message.javaClass}")
        null
      }
    }
  }
}

internal object DeadMessageAttribute : Attribute
