/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.orca.kayenta.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kayenta.KayentaService
import com.netflix.spinnaker.orca.kayenta.Thresholds
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.Collections.singletonMap
import java.util.concurrent.TimeUnit.HOURS

@Component
class MonitorKayentaCanaryTask(
  private val kayentaService: KayentaService
) : OverridableTimeoutRetryableTask {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun getBackoffPeriod() = 1000L

  override fun getTimeout() = HOURS.toMillis(12)

  override fun execute(stage: Stage): TaskResult {
    val context = stage.context
    val canaryPipelineExecutionId = context["canaryPipelineExecutionId"] as String
    val storageAccountName = context["storageAccountName"] as String
    val canaryResults = kayentaService.getCanaryResults(storageAccountName, canaryPipelineExecutionId)
    val status = ExecutionStatus.valueOf(canaryResults["status"].toString().toUpperCase())

    if (status == SUCCEEDED) {
      val (pass, marginal) = context["scoreThresholds"] as Thresholds
      // TODO: for the love of god can this be an actual type?
      val result = canaryResults["result"] as Map<String, Any>
      val canaryScore = (result["judgeResult"] as Map<String, Map<String, Double>>)["score"]!!["score"]!!
      val lastUpdatedMs = canaryResults["endTimeMillis"] as Long
      val lastUpdatedIso = canaryResults["endTimeIso"] as String
      val durationString = result["canaryDuration"] as String

      return if (marginal == null && pass == null) {
        TaskResult(SUCCEEDED, mapOf(
          "canaryPipelineStatus" to SUCCEEDED,
          "lastUpdated" to lastUpdatedMs,
          "lastUpdatedIso" to lastUpdatedIso,
          "durationString" to durationString,
          "canaryScore" to canaryScore,
          "canaryScoreMessage" to "No score thresholds were specified."
        ))
      } else if (marginal == null) {
        TaskResult(SUCCEEDED, mapOf(
          "canaryPipelineStatus" to SUCCEEDED,
          "lastUpdated" to lastUpdatedMs,
          "lastUpdatedIso" to lastUpdatedIso,
          "durationString" to durationString,
          "canaryScore" to canaryScore,
          "canaryScoreMessage" to "No marginal score threshold was specified."
        ))
      } else if (canaryScore <= marginal) {
        TaskResult(TERMINAL, mapOf(
          "canaryPipelineStatus" to SUCCEEDED,
          "lastUpdated" to lastUpdatedMs,
          "lastUpdatedIso" to lastUpdatedIso,
          "durationString" to durationString,
          "canaryScore" to canaryScore,
          "canaryScoreMessage" to "Canary score is not above the marginal score threshold."
        ))
      } else {
        TaskResult(SUCCEEDED, mapOf(
          "canaryPipelineStatus" to SUCCEEDED,
          "lastUpdated" to lastUpdatedMs,
          "lastUpdatedIso" to lastUpdatedIso,
          "durationString" to durationString,
          "canaryScore" to canaryScore
        ))
      }
    }

    if (status.isHalt) {
      val stageOutputs = singletonMap<String, Any>("canaryPipelineStatus", status)

      if (canaryResults["exception"] != null) {
        stageOutputs["exception"] = canaryResults["exception"]
      } else if (status == CANCELED) {
        stageOutputs["exception"] = singletonMap("details", singletonMap("errors", listOf("Canary execution was canceled.")))
      }

      // Indicates a failure of some sort.
      return TaskResult(TERMINAL, stageOutputs)
    }

    return TaskResult(RUNNING, singletonMap("canaryPipelineStatus", status))
  }
}
