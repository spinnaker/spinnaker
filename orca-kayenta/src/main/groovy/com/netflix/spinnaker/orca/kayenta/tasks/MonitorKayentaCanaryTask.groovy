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
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kayenta.KayentaService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull
import java.time.Instant
import java.util.concurrent.TimeUnit

@Slf4j
@Component
class MonitorKayentaCanaryTask implements OverridableTimeoutRetryableTask {

  long backoffPeriod = 1000
  long timeout = TimeUnit.HOURS.toMillis(12)

  @Autowired
  KayentaService kayentaService

  @Override
  TaskResult execute(@Nonnull Stage stage) {
    Map<String, Object> context = stage.getContext()
    String canaryPipelineExecutionId = (String)context.get("canaryPipelineExecutionId")
    Pipeline canaryPipelineExecution = kayentaService.getPipelineExecution(canaryPipelineExecutionId)

    if (canaryPipelineExecution.status == ExecutionStatus.SUCCEEDED) {
      Map<String, String> scoreThresholds = context.get("scoreThresholds")
      Stage canaryJudgeStage = canaryPipelineExecution.namedStage("canaryJudge")
      Map<String, Object> canaryJudgeContext = canaryJudgeStage.context
      double canaryScore = canaryJudgeContext.result.score.score
      long lastUpdatedMs = canaryJudgeStage.endTime.toLong()
      String lastUpdatedIso = Instant.ofEpochMilli(lastUpdatedMs).toString()
      String durationString = canaryJudgeContext.durationString

      if (scoreThresholds?.marginal == null && scoreThresholds?.pass == null) {
        return new TaskResult(ExecutionStatus.SUCCEEDED, [canaryPipelineStatus: ExecutionStatus.SUCCEEDED,
                                                          lastUpdated         : lastUpdatedMs,
                                                          lastUpdatedIso      : lastUpdatedIso,
                                                          durationString      : durationString,
                                                          canaryScore         : canaryScore,
                                                          canaryScoreMessage  : "No score thresholds were specified."])
      } else if (scoreThresholds?.marginal == null) {
        return new TaskResult(ExecutionStatus.SUCCEEDED, [canaryPipelineStatus: ExecutionStatus.SUCCEEDED,
                                                          lastUpdated         : lastUpdatedMs,
                                                          lastUpdatedIso      : lastUpdatedIso,
                                                          durationString      : durationString,
                                                          canaryScore         : canaryScore,
                                                          canaryScoreMessage  : "No marginal score threshold was specified."])
      } else if (canaryScore <= scoreThresholds.marginal.toDouble()) {
        return new TaskResult(ExecutionStatus.TERMINAL,  [canaryPipelineStatus: ExecutionStatus.SUCCEEDED,
                                                          lastUpdated         : lastUpdatedMs,
                                                          lastUpdatedIso      : lastUpdatedIso,
                                                          durationString      : durationString,
                                                          canaryScore         : canaryScore,
                                                          canaryScoreMessage  : "Canary score is not above the marginal score threshold."])
      } else {
        return new TaskResult(ExecutionStatus.SUCCEEDED, [canaryPipelineStatus: ExecutionStatus.SUCCEEDED,
                                                          lastUpdated         : lastUpdatedMs,
                                                          lastUpdatedIso      : lastUpdatedIso,
                                                          durationString      : durationString,
                                                          canaryScore         : canaryScore])
      }
    }

    if (canaryPipelineExecution.status.halt) {
      Map<String, Object> stageOutputs = [canaryPipelineStatus: canaryPipelineExecution.status]

      // Propagate the first canary pipeline exception we can locate.
      Stage stageWithException = canaryPipelineExecution.stages.find { it.context.exception }

      if (stageWithException) {
        stageOutputs.exception = stageWithException.context.exception
      }

      // Indicates a failure of some sort.
      return new TaskResult(ExecutionStatus.TERMINAL, stageOutputs)
    }

    return new TaskResult(ExecutionStatus.RUNNING, [canaryPipelineStatus: canaryPipelineExecution.status])
  }
}
