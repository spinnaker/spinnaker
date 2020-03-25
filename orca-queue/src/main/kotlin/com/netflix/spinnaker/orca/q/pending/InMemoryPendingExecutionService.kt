/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.orca.q.pending

import com.netflix.spinnaker.q.Message
import java.util.Deque
import java.util.LinkedList

class InMemoryPendingExecutionService : PendingExecutionService {

  private val pending: MutableMap<String, Deque<Message>> = mutableMapOf()

  override fun enqueue(pipelineConfigId: String, message: Message) {
    pendingFor(pipelineConfigId).addLast(message)
  }

  override fun popOldest(pipelineConfigId: String): Message? {
    return pendingFor(pipelineConfigId).let {
      if (it.isEmpty()) {
        null
      } else {
        it.removeFirst()
      }
    }
  }

  override fun popNewest(pipelineConfigId: String): Message? {
    return pendingFor(pipelineConfigId).let {
      if (it.isEmpty()) {
        null
      } else {
        it.removeLast()
      }
    }
  }

  override fun purge(pipelineConfigId: String, callback: (Message) -> Unit) {
    pendingFor(pipelineConfigId).let {
      while (it.isNotEmpty()) {
        it.removeFirst()?.let(callback)
      }
    }
  }

  override fun depth(pipelineConfigId: String): Int {
    return pendingFor(pipelineConfigId).size
  }

  private fun pendingFor(pipelineConfigId: String): Deque<Message> =
    pending.computeIfAbsent(pipelineConfigId) { LinkedList() }
}
