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
package com.netflix.spinnaker.orca.log

import com.netflix.spinnaker.orca.events.*
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

@Component
class ExecutionLogListener(
  private val executionLogRepository: ExecutionLogRepository,
  private val currentInstanceId: String
) : ApplicationListener<ExecutionEvent> {

  override fun onApplicationEvent(event: ExecutionEvent) {
    executionLogRepository.save(event.toLogEntry())
  }

  private fun ExecutionEvent.toLogEntry(): ExecutionLogEntry {
    val details = when (this) {
      is ExecutionComplete -> mapOf("status" to status.name)
      is StageStarted -> mapOf("stageId" to stageId, "type" to stageType, "name" to stageName)
      is StageComplete -> mapOf("stageId" to stageId, "type" to stageType, "name" to stageName, "status" to status.name)
      is TaskStarted -> mapOf("stageId" to stageId, "taskId" to taskId, "type" to taskType, "name" to taskName)
      is TaskComplete -> mapOf("stageId" to stageId, "taskId" to taskId, "type" to taskType, "name" to taskName, "status" to status.name)
      else -> emptyMap()
    }
    return ExecutionLogEntry(
      executionId,
      timestamp(),
      javaClass.simpleName,
      currentInstanceId,
      details
    )
  }
}
