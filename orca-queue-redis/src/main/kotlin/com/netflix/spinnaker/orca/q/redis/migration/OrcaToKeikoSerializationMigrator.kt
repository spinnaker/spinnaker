/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.orca.q.redis.migration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.q.migration.SerializationMigrator

internal val orcaToKeikoTypes = mapOf(
  ".StartTask" to "startTask",
  ".CompleteTask" to "completeTask",
  ".PauseTask" to "pauseTask",
  ".ResumeTask" to "resumeTask",
  ".RunTask" to "runTask",
  ".StartStage" to "startStage",
  ".ContinueParentStage" to "continueParentStage",
  ".CompleteStage" to "completeStage",
  ".SkipStage" to "skipStage",
  ".AbortStage" to "abortStage",
  ".PauseStage" to "pauseStage",
  ".RestartStage" to "restartStage",
  ".ResumeStage" to "resumeStage",
  ".CancelStage" to "cancelStage",
  ".StartExecution" to "startExecution",
  ".RescheduleExecution" to "rescheduleExecution",
  ".CompleteExecution" to "completeExecution",
  ".ResumeExecution" to "resumeExecution",
  ".CancelExecution" to "cancelExecution",
  ".InvalidExecutionId" to "invalidExecutionId",
  ".InvalidStageId" to "invalidStageId",
  ".InvalidTaskId" to "invalidTaskId",
  ".InvalidTaskType" to "invalidTaskType",
  ".NoDownstreamTasks" to "noDownstreamTasks",
  ".TotalThrottleTimeAttribute" to "totalThrottleTime",
  ".handler.DeadMessageAttribute" to "deadMessage",
  ".MaxAttemptsAttribute" to "maxAttempts",
  ".AttemptsAttribute" to "attempts"
)

class OrcaToKeikoSerializationMigrator(
  private val mapper: ObjectMapper
) : SerializationMigrator {

  override fun migrate(json: String): String {
    val m = mapper.readValue<MutableMap<String, Any?>>(json)

    val replaceKind = fun (target: MutableMap<String, Any?>) {
      if (target.containsKey("@class")) {
        target["kind"] = orcaToKeikoTypes[target["@class"]]
        target.remove("@class")
      }
    }

    replaceKind(m)
    if (m.containsKey("attributes")) {
      (m["attributes"] as List<MutableMap<String, Any?>>).forEach(replaceKind)
    }

    return mapper.writeValueAsString(m)
  }
}
