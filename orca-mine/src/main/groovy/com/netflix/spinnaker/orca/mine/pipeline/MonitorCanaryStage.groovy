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

import groovy.util.logging.Slf4j
import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.mine.tasks.CleanupCanaryTask
import com.netflix.spinnaker.orca.mine.tasks.CompleteCanaryTask
import com.netflix.spinnaker.orca.mine.tasks.MonitorCanaryTask
import com.netflix.spinnaker.orca.mine.tasks.RegisterCanaryTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class MonitorCanaryStage implements StageDefinitionBuilder, CancellableStage {
  @Autowired
  MineService mineService

  @Override
  <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
    builder
      .withTask("registerCanary", RegisterCanaryTask)
      .withTask("monitorCanary", MonitorCanaryTask)
      .withTask("cleanupCanary", CleanupCanaryTask)
      .withTask("monitorCleanup", MonitorKatoTask)
      .withTask("completeCanary", CompleteCanaryTask)
  }

  @Override
  CancellableStage.Result cancel(Stage stage) {
    def cancelCanaryResults = [:]
    def canaryId = stage.context.canary?.id as String

    try {
      if (canaryId) {
        // will not have a `canaryId` if the failure occured prior to registration
        cancelCanaryResults = mineService.cancelCanary(canaryId, "Pipeline execution (${stage.execution?.id}) canceled")
        log.info("Canceled canary in mine (canaryId: ${canaryId}, stageId: ${stage.id}, executionId: ${stage.execution.id})")
      }
    } catch (Exception e) {
      log.error("Unable to cancel canary '${canaryId}' in mine", e)
    }

    def canary = stage.ancestors { Stage s, StageDefinitionBuilder stageBuilder ->
      stageBuilder instanceof CanaryStage
    } first()
    def cancelResult = ((CancellableStage) canary.stageBuilder).cancel(canary.stage)
    cancelResult.details.put("canary", cancelCanaryResults)

    return cancelResult
  }
}
