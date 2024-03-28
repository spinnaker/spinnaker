/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.mine.pipeline

import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.orca.api.pipeline.CancellableStage
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.mine.tasks.CompleteCanaryTask
import com.netflix.spinnaker.orca.mine.tasks.MonitorAcaTaskTask
import com.netflix.spinnaker.orca.mine.tasks.RegisterAcaTaskTask
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

import static org.springframework.http.HttpStatus.CONFLICT

@Slf4j
@Component
class AcaTaskStage implements StageDefinitionBuilder, CancellableStage {
  @Autowired
  MineService mineService

  @Override
  void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    builder
      .withTask("registerGenericCanary", RegisterAcaTaskTask)
      .withTask("monitorGenericCanary", MonitorAcaTaskTask)
      .withTask("completeCanary", CompleteCanaryTask)
  }

  @Override
  void prepareStageForRestart(@Nonnull StageExecution stage) {
    if (stage.context.canary) {
      def previousCanary = stage.context.canary.clone()
      if (!stage.context.restartDetails) stage.context.restartDetails = [:]
      stage.context.restartDetails << [previousCanary: previousCanary]
      cancelCanary(stage, "Restarting AcaTaskStage for execution (${stage.execution?.id}) ")

      stage.context.canary.remove("id")
      stage.context.canary.remove("launchDate")
      stage.context.canary.remove("endDate")
      stage.context.canary.remove("canaryResult")
      stage.context.canary.remove("status")
      stage.context.canary.remove("health")
    }
  }

  @Override
  CancellableStage.Result cancel(StageExecution stage) {
    log.info("Cancelling stage (stageId: ${stage.id}, executionId: ${stage.execution.id}, context: ${stage.context as Map})")
    def cancelCanaryResults = cancelCanary(stage, "Pipeline execution (${stage.execution?.id}) canceled");
    log.info("Canceled AcaTaskStage for pipeline: ${stage.execution?.id} with results: ${cancelCanaryResults}")
    return new CancellableStage.Result(stage, ["canary": cancelCanaryResults])
  }

  Map cancelCanary(StageExecution stage, String reason)  {
    if(stage?.context?.canary?.id) {
      try {
        def cancelCanaryResults = mineService.cancelCanary(stage.context.canary.id as String, reason)
        log.info("Canceled canary in mine (canaryId: ${stage.context.canary.id}, stageId: ${stage.id}, executionId: ${stage.execution.id}): ${reason}")
        return cancelCanaryResults
      } catch (SpinnakerHttpException e) {
        if (e.responseCode == CONFLICT.value()) {
          log.info("Canary (canaryId: ${stage.context.canary.id}, stageId: ${stage.id}, executionId: ${stage.execution.id}) has already ended")
          return [:]
        } else {
          throw e
        }
      }
    }
  }
}
