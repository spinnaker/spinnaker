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

import com.fasterxml.jackson.annotation.JsonTypeName
import com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.q.AbortStage
import com.netflix.spinnaker.orca.q.CompleteExecution
import com.netflix.spinnaker.orca.q.CompleteTask
import com.netflix.spinnaker.orca.q.ExecutionLevel
import com.netflix.spinnaker.orca.q.StageLevel
import com.netflix.spinnaker.orca.q.TaskLevel
import com.netflix.spinnaker.q.Attribute
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.Queue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component class DeadMessageHandler {
  private val log = LoggerFactory.getLogger(javaClass)

  fun handle(queue: Queue, message: Message) {
    log.error("Dead message: $message")
    terminationMessageFor(message)
      ?.let {
        it.setAttribute(DeadMessageAttribute)
        queue.push(it)
      }
  }

  private fun terminationMessageFor(message: Message): Message? {
    if (message.getAttribute<DeadMessageAttribute>() != null) {
      log.warn("Already sent $message to DLQ")
      return null
    }
    return when (message) {
      is TaskLevel -> CompleteTask(message, TERMINAL)
      is StageLevel -> AbortStage(message)
      is ExecutionLevel -> CompleteExecution(message)
      else -> {
        log.error("Unhandled message type ${message.javaClass}")
        null
      }
    }
  }
}

@JsonTypeName("deadMessage") internal object DeadMessageAttribute : Attribute
