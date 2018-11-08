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

import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.ext.mapTo
import com.netflix.spinnaker.orca.kayenta.model.KayentaCanaryContext
import com.netflix.spinnaker.orca.kayenta.pipeline.RunCanaryPipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AggregateCanaryResultsTask : Task {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun execute(stage: Stage): TaskResult {
    val canaryConfig = stage.mapTo<KayentaCanaryContext>("/canaryConfig")
    val intervalStageId = stage.context["intervalStageId"] as String
    val runCanaryStages = stage
      .execution
      .stages
      .filter { it -> it.type == RunCanaryPipelineStage.STAGE_TYPE && it.parentStageId == intervalStageId }
      .sortedBy { it.name.substringAfterLast("#").toInt() }
    val runCanaryScores = runCanaryStages
      .map { it -> it.mapTo<Number>("/canaryScore") }
      .map(Number::toDouble)
    val finalCanaryScore = runCanaryScores[runCanaryScores.size - 1]

    return if (canaryConfig.scoreThresholds?.marginal == null && canaryConfig.scoreThresholds?.pass == null) {
      TaskResult(SUCCEEDED, mapOf(
        "canaryScores" to runCanaryScores,
        "canaryScoreMessage" to "No score thresholds were specified."
      ))
    } else if (canaryConfig.scoreThresholds.marginal != null && finalCanaryScore <= canaryConfig.scoreThresholds.marginal) {
      TaskResult(TERMINAL, mapOf(
        "canaryScores" to runCanaryScores,
        "canaryScoreMessage" to "Final canary score $finalCanaryScore is not above the marginal score threshold."
      ))
    } else if (canaryConfig.scoreThresholds.pass == null) {
      TaskResult(SUCCEEDED, mapOf(
        "canaryScores" to runCanaryScores,
        "canaryScoreMessage" to "No pass score threshold was specified."
      ))
    } else if (finalCanaryScore < canaryConfig.scoreThresholds.pass) {
      TaskResult(TERMINAL, mapOf(
        "canaryScores" to runCanaryScores,
        "canaryScoreMessage" to "Final canary score $finalCanaryScore is below the pass score threshold."
      ))
    } else {
      TaskResult(SUCCEEDED, mapOf(
        "canaryScores" to runCanaryScores,
        "canaryScoreMessage" to "Final canary score $finalCanaryScore met or exceeded the pass score threshold."
      ))
    }
  }
}
