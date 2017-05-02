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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.CloneServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.CreateServerGroupStage
import com.netflix.spinnaker.orca.pipeline.BranchingStageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Component
@Slf4j
@CompileStatic
class ParallelDeployStage implements BranchingStageDefinitionBuilder {

  @Deprecated
  public static final String PIPELINE_CONFIG_TYPE = "deploy"

  @Override
  String getType() {
    return PIPELINE_CONFIG_TYPE
  }

  @Override
  <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
  }

  @Override
  void postBranchGraph(Stage<?> stage, TaskNode.Builder builder) {
    builder.withTask("completeParallelDeploy", CompleteParallelDeployTask)
  }

  @Override
  String getChildStageType(Stage childStage) {
    return isClone(childStage) ? CloneServerGroupStage.PIPELINE_CONFIG_TYPE : PIPELINE_CONFIG_TYPE
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

  @Override
  @CompileDynamic
  <T extends Execution<T>> Collection<Map<String, Object>> parallelContexts(Stage<T> stage) {
    if (stage.execution instanceof Pipeline) {
      Map trigger = ((Pipeline) stage.execution).trigger
      if (trigger.parameters?.strategy == true) {
        Map parentStage = trigger.parentExecution.stages.find {
          it.id == trigger.parameters.parentStageId
        }
        Map cluster = parentStage.context as Map
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

        // Parent stage can be deploy or cloneServerGroup.
        stage.type = parentStage.type
        stage.context.clusters = [cluster as Map<String, Object>]
      }
    }

    def defaultStageContext = new HashMap(stage.context)

    List<Map<String, Object>> clusters = []

    if (stage.context.cluster) {
      clusters.add(stage.context.cluster as Map<String, Object>)
      defaultStageContext.remove("cluster")
    }
    if (stage.context.clusters) {
      clusters.addAll(stage.context.clusters as List<Map<String, Object>>)
      defaultStageContext.remove("clusters")
    }

    if (!stage.context.cluster && !stage.context.clusters) {
      // support invoking this stage as an orchestration without nested target cluster details
      clusters.add(stage.context)
      defaultStageContext.clear()
    }

    return clusters.collect {
      clusterContext(stage, defaultStageContext, it)
    }
  }

  @Override
  String parallelStageName(Stage<?> stage, boolean hasParallelFlows) {
    return isClone(stage) ? "Clone" : stage.name
  }

  @CompileDynamic
  private <T extends Execution<T>> boolean isClone(Stage<T> stage) {
    if (stage.execution instanceof Pipeline) {
      Map trigger = ((Pipeline) stage.execution).trigger

      if (trigger.parameters?.clone == true) {
        return true
      }
    }

    return false
  }

  @Component
  @Slf4j
  @CompileStatic
  public static class CompleteParallelDeployTask implements Task {
    TaskResult execute(Stage stage) {
      log.info("Completed Parallel Deploy")
      new TaskResult(ExecutionStatus.SUCCEEDED, [:], [:])
    }
  }
}
