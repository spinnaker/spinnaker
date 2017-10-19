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

import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.mine.tasks.CleanupCanaryTask
import com.netflix.spinnaker.orca.mine.tasks.CompleteCanaryTask
import com.netflix.spinnaker.orca.mine.tasks.DisableCanaryTask
import com.netflix.spinnaker.orca.mine.tasks.MonitorCanaryTask
import com.netflix.spinnaker.orca.mine.tasks.RegisterCanaryTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.tasks.WaitTask
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class MonitorCanaryStage implements StageDefinitionBuilder, CancellableStage {

  @Autowired MineService mineService
  @Autowired CanaryStage canaryStage

  @Override
  <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
    builder
      .withTask("registerCanary", RegisterCanaryTask)
      .withTask("monitorCanary", MonitorCanaryTask)
      .withTask("disableCanaryCluster", DisableCanaryTask)
      .withTask("monitorDisable", MonitorKatoTask)
      .withTask("waitBeforeCleanup", WaitTask)
      .withTask("disableBaselineCluster", DisableCanaryTask)
      .withTask("monitorDisable", MonitorKatoTask)
      .withTask("waitBeforeCleanup", WaitTask)
      .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
      .withTask("cleanupCanary", CleanupCanaryTask)
      .withTask("monitorCleanup", MonitorKatoTask)
      .withTask("completeCanary", CompleteCanaryTask)
  }

  @Override
  CancellableStage.Result cancel(Stage stage) {
    def cancelCanaryResults = [:]
    def canaryId = stage.context.canary?.id as String

    if (canaryId && !stage.execution.canceled) {
      /*
       * If the canary was registered and pipeline not explicitly canceled, do not automatically cleanup the canary.
       *
       * Normally, the `CleanupCanaryTask` will cleanup a canary if it was successfully registered and `TERMINATE` is an
       * allowed `actionsForUnhealthyCanary` action.
       */
      return null
    }

    try {
      if (canaryId) {
        // will not have a `canaryId` if the failure occurred prior to registration
        cancelCanaryResults = mineService.cancelCanary(canaryId, "Pipeline execution (${stage.execution?.id}) canceled")
        log.info("Canceled canary in mine (canaryId: ${canaryId}, stageId: ${stage.id}, executionId: ${stage.execution.id})")
      }
    } catch (Exception e) {
      log.error("Unable to cancel canary '${canaryId}' in mine", e)
    }

    Stage canaryStageInstance = stage.ancestors().find {
      it.type == CanaryStage.PIPELINE_CONFIG_TYPE
    }

    if (!canaryStageInstance) {
      throw new IllegalStateException("No upstream canary stage found (stageId: ${stage.id}, executionId: ${stage.execution.id})")
    }

    def cancelResult = canaryStage.cancel(canaryStageInstance)
    cancelResult.details.put("canary", cancelCanaryResults)

    return cancelResult
  }
}
