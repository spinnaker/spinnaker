/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.q

import com.netflix.spinnaker.orca.events.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component
import java.io.Serializable

@Component
open class ExecutionLogListener
@Autowired constructor(
  private val executionLogRepository: ExecutionLogRepository,
  private val currentInstanceId: String
) : ApplicationListener<ExecutionEvent> {

  override fun onApplicationEvent(event: ExecutionEvent) {
    executionLogRepository.save(event.run {
      when (this) {
        is ExecutionStarted -> toLogEntry(event, emptyMap())
        is ExecutionComplete -> toLogEntry(event, hashMapOf("status" to status.name))
        is StageStarted -> toLogEntry(event, emptyMap())
        is StageComplete -> toLogEntry(event, hashMapOf("status" to status.name))
        is TaskStarted -> toLogEntry(event, emptyMap())
        is TaskComplete -> toLogEntry(event, hashMapOf("status" to status.name))
        else -> throw IllegalArgumentException("Unknown event type $javaClass")
      }
    })
  }

  private fun toLogEntry(event: ExecutionEvent, details: Map<String, Serializable>): ExecutionLogEntry = event.run {
    ExecutionLogEntry(
      executionId,
      timestamp(),
      javaClass.simpleName,
      details,
      currentInstanceId
    )
  }
}
