/*
 * Copyright 2018 Netflix, Inc.
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

import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.ext.mapTo
import com.netflix.spinnaker.orca.kayenta.KayentaService
import com.netflix.spinnaker.orca.kayenta.Thresholds
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit.HOURS

@Component
class MonitorKayentaCanaryTask(
  private val kayentaService: KayentaService
) : OverridableTimeoutRetryableTask {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun getBackoffPeriod() = 1000L

  override fun getTimeout() = HOURS.toMillis(12)

  data class MonitorKayentaCanaryContext(
    val canaryPipelineExecutionId: String,
    val storageAccountName: String?,
    val scoreThresholds: Thresholds
  )

  override fun execute(stage: Stage): TaskResult {
    val context = stage.mapTo<MonitorKayentaCanaryContext>()
    val canaryResults = kayentaService.getCanaryResults(context.storageAccountName, context.canaryPipelineExecutionId)

    if (canaryResults.executionStatus == SUCCEEDED) {
      val canaryScore = canaryResults.result!!.judgeResult.score.score

      return if (canaryScore <= context.scoreThresholds.marginal) {
        val resultStatus = if (stage.context["continuePipeline"] == true) FAILED_CONTINUE else TERMINAL
        TaskResult(resultStatus, mapOf(
          "canaryPipelineStatus" to SUCCEEDED,
          "lastUpdated" to canaryResults.endTimeIso?.toEpochMilli(),
          "lastUpdatedIso" to canaryResults.endTimeIso,
          "durationString" to canaryResults.result.canaryDuration.toString(),
          "canaryScore" to canaryScore,
          "canaryScoreMessage" to "Canary score is not above the marginal score threshold."
        ))
      } else {
        TaskResult(SUCCEEDED, mapOf(
          "canaryPipelineStatus" to SUCCEEDED,
          "lastUpdated" to canaryResults.endTimeIso?.toEpochMilli(),
          "lastUpdatedIso" to canaryResults.endTimeIso,
          "durationString" to canaryResults.result.canaryDuration.toString(),
          "canaryScore" to canaryScore
        ))
      }
    }

    if (canaryResults.executionStatus.isHalt) {
      val stageOutputs = mutableMapOf<String, Any>("canaryPipelineStatus" to canaryResults.executionStatus)

      if (canaryResults.executionStatus == CANCELED) {
        stageOutputs["exception"] = mapOf("details" to mapOf("errors" to listOf("Canary execution was canceled.")))
      } else {
        canaryResults.exception?.let { stageOutputs["exception"] = it }
      }

      // Indicates a failure of some sort.
      return TaskResult(TERMINAL, stageOutputs)
    }

    return TaskResult(RUNNING, mapOf("canaryPipelineStatus" to canaryResults.executionStatus))
  }
}
