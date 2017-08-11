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
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kayenta.pipeline.RunCanaryPipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@Slf4j
@Component
class AggregateCanaryResultsTask implements Task {

  @Override
  TaskResult execute(@Nonnull Stage stage) {
    Map<String, String> scoreThresholds = stage.context.canaryConfig.get("scoreThresholds")
    List<Stage> runCanaryStages = stage.execution.stages.findAll { it.type == RunCanaryPipelineStage.STAGE_TYPE }
    List<Double> runCanaryScores = runCanaryStages.collect { (Double)it.context.canaryScore }
    double finalCanaryScore = runCanaryScores.last()

    if (scoreThresholds?.marginal == null && scoreThresholds?.pass == null) {
      return new TaskResult(ExecutionStatus.SUCCEEDED, [canaryScores      : runCanaryScores,
                                                        canaryScoreMessage: "No score thresholds were specified."])
    } else if (scoreThresholds?.marginal != null && finalCanaryScore <= scoreThresholds.marginal.toDouble()) {
      return new TaskResult(ExecutionStatus.TERMINAL,  [canaryScores      : runCanaryScores,
                                                        canaryScoreMessage: "Final canary score $finalCanaryScore is not above the marginal score threshold.".toString()])
    } else if (scoreThresholds?.pass == null) {
      return new TaskResult(ExecutionStatus.SUCCEEDED, [canaryScores      : runCanaryScores,
                                                        canaryScoreMessage: "No pass score threshold was specified."])
    } else if (finalCanaryScore < scoreThresholds.pass.toDouble()) {
      return new TaskResult(ExecutionStatus.TERMINAL,  [canaryScores      : runCanaryScores,
                                                        canaryScoreMessage: "Final canary score $finalCanaryScore is below the pass score threshold.".toString()])
    } else {
      return new TaskResult(ExecutionStatus.SUCCEEDED, [canaryScores      : runCanaryScores,
                                                        canaryScoreMessage: "Final canary score $finalCanaryScore met or exceeded the pass score threshold.".toString()])
    }
  }
}
