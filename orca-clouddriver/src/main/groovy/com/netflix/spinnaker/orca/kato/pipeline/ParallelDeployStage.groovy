/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.kato.pipeline

import javax.annotation.Nonnull
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.CloneServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.CreateServerGroupStage
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.PipelineTrigger
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Trigger
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE

@Component
@Slf4j
@CompileStatic
class ParallelDeployStage implements StageDefinitionBuilder {

  @Deprecated
  public static final String PIPELINE_CONFIG_TYPE = "deploy"

  @Override
  String getType() {
    return PIPELINE_CONFIG_TYPE
  }

  @Override
  void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder.withTask("completeParallelDeploy", CompleteParallelDeployTask)
  }

  @Nonnull List<Stage> parallelStages(@Nonnull Stage stage) {
    parallelContexts(stage).collect { context ->
      def type = isClone(stage) ? CloneServerGroupStage.PIPELINE_CONFIG_TYPE : CreateServerGroupStage.PIPELINE_CONFIG_TYPE
      newStage(stage.execution, type, context.name as String, context, stage, STAGE_BEFORE)
    }
  }

  @CompileDynamic
  protected Collection<Map<String, Object>> parallelContexts(Stage stage) {
    def defaultStageContext = new HashMap(stage.context)
    if (stage.execution.type == PIPELINE) {
      Trigger trigger = stage.execution.trigger
      if (trigger.strategy && trigger instanceof PipelineTrigger) {
        // NOTE: this is NOT the actual parent stage, it's the grandparent which is the top level deploy stage
        Stage parentDeployStage = trigger.parentExecution.stageById(trigger.parameters.parentStageId)
        Map cluster = new HashMap(parentDeployStage.context as Map)
        cluster.strategy = 'none'
        if (!cluster.amiName && trigger.parameters.amiName) {
          cluster.amiName = trigger.parameters.amiName
        }
        if (!cluster.image && trigger.parameters.imageId) {
          // GCE uses 'image' as the ID key to clouddriver.
          cluster.image = trigger.parameters.imageId
        }
        // the strategy can set it's own enable / disable traffic settings to override the one in advanced settings
        if (stage.context.trafficOptions && stage.context.trafficOptions != 'inherit') {
          if (!cluster.containsKey("suspendedProcesses")) {
            cluster.suspendedProcesses = []
          }

          String addToLoadBalancer = 'AddToLoadBalancer'.toString()
          if (stage.context.trafficOptions == 'enable') {
            // explicitly enable traffic
            if (cluster.suspendedProcesses.contains(addToLoadBalancer)) {
              cluster.suspendedProcesses.remove(addToLoadBalancer)
            }
          }
          if (stage.context.trafficOptions == 'disable') {
            // explicitly disable traffic
            if (!cluster.suspendedProcesses.contains(addToLoadBalancer)) {
              cluster.suspendedProcesses.add(addToLoadBalancer)
            }
          }
        }

        // Avoid passing 'stageEnabled' configuration on to the deploy stage in a strategy pipeline
        cluster.remove("stageEnabled")

        defaultStageContext.clusters = [cluster as Map<String, Object>]
      }
    }

    List<Map<String, Object>> clusters = []

    if (defaultStageContext.cluster) {
      clusters.add(defaultStageContext.cluster as Map<String, Object>)
    }
    defaultStageContext.remove("cluster")

    if (defaultStageContext.clusters) {
      clusters.addAll(defaultStageContext.clusters as List<Map<String, Object>>)
    }
    defaultStageContext.remove("clusters")

    if (!clusters && !defaultStageContext.isEmpty()) {
      // support invoking this stage as an orchestration without nested target cluster details
      clusters.add(defaultStageContext)
      defaultStageContext = [:]
    }

    return clusters.collect {
      clusterContext(stage, defaultStageContext, it)
    }
  }

  @CompileDynamic
  protected Map<String, Object> clusterContext(Stage stage, Map defaultStageContext, Map cluster) {
    def type = isClone(stage) ? CloneServerGroupStage.PIPELINE_CONFIG_TYPE : CreateServerGroupStage.PIPELINE_CONFIG_TYPE

    if (cluster.providerType && !(cluster.providerType in ['aws', 'titus'])) {
      type += "_$cluster.providerType"
    }

    String baseName = isClone(stage) ? 'Clone' : 'Deploy'
    String name = cluster.region ? "$baseName in ${cluster.region}" : "$baseName in ${(cluster.availabilityZones as Map).keySet()[0]}"

    return defaultStageContext + [
      account: cluster.account ?: cluster.credentials ?: stage.context.account ?: stage.context.credentials,
      cluster: cluster,
      type   : type,
      name   : name
    ]
  }

  @CompileDynamic
  private boolean isClone(Stage stage) {
    if (stage.execution.type == PIPELINE) {
      Trigger trigger = stage.execution.trigger

      if (trigger?.parameters?.clone == true) {
        return true
      }
    }

    return false
  }

  @Component
  @Slf4j
  @CompileStatic
  static class CompleteParallelDeployTask implements Task {
    TaskResult execute(Stage stage) {
      log.info("Completed Parallel Deploy")
      new TaskResult(ExecutionStatus.SUCCEEDED, [:], [:])
    }
  }
}
