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
import com.netflix.spinnaker.orca.api.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.mine.pipeline.CanaryDestroyClusterStage
import com.netflix.spinnaker.orca.mine.pipeline.CanaryStage
import com.netflix.spinnaker.orca.mine.tasks.DisableCanaryTask
import com.netflix.spinnaker.orca.pipeline.WaitStage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

import static com.netflix.spinnaker.orca.mine.pipeline.CanaryStage.DEFAULT_CLUSTER_DISABLE_WAIT_TIME

@Component
class CanaryDisableClusterStage implements StageDefinitionBuilder, CancellableStage {
  public static final String STAGE_TYPE = "canaryDisableCluster"
  @Autowired CanaryStage canaryStage
  @Autowired WaitStage waitStage
  @Autowired CanaryDestroyClusterStage canaryDestroyClusterStage

  @Override
  void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    builder
        .withTask("disableCanaryCluster", DisableCanaryTask)
        .withTask("monitorDisable", MonitorKatoTask)
        .withTask("disableBaselineCluster", DisableCanaryTask)
        .withTask("monitorDisable", MonitorKatoTask)
  }

  @Override
  void afterStages(StageExecution parent, StageGraphBuilder graph) {
    Integer waitTime = parent.context.clusterDisableWaitTime != null ? stage.context.clusterDisableWaitTime : DEFAULT_CLUSTER_DISABLE_WAIT_TIME
    graph.append {
      it.type = waitStage.type
      it.name = "Disable Canary and Baseline"
      it.context = ["waitTime": waitTime]
    }
    graph.append {
      it.type = canaryDestroyClusterStage.type
      it.name = "Tear down Canary and Baseline"
      it.context = parent.context
    }
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
