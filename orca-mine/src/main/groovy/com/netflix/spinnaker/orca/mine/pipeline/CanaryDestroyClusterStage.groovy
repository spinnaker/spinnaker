/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.orca.mine.pipeline


import com.netflix.spinnaker.orca.api.pipeline.CancellableStage
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.mine.tasks.CleanupCanaryTask
import com.netflix.spinnaker.orca.mine.tasks.CompleteCanaryTask
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@Component
class CanaryDestroyClusterStage implements StageDefinitionBuilder, CancellableStage {
  public static final String STAGE_TYPE = "canaryDisableCluster"
  @Autowired CanaryStage canaryStage

  @Override
  void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    builder
        .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
        .withTask("cleanupCanary", CleanupCanaryTask)
        .withTask("monitorCleanup", MonitorKatoTask)
        .withTask("completeCanary", CompleteCanaryTask)
  }

  @Override
  Result cancel(StageExecution stage) {
    StageExecution canaryStageInstance = stage.ancestors().find {
      it.type == CanaryStage.PIPELINE_CONFIG_TYPE
    }

    if (!canaryStageInstance) {
      throw new IllegalStateException("No upstream canary stage found (stageId: ${stage.id}, executionId: ${stage.execution.id})")
    }

    return canaryStage.cancel(canaryStageInstance)
  }
}
