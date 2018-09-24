/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.kato.pipeline.strategy.Strategy

import javax.annotation.Nonnull
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.clouddriver.FeaturesService
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.RollbackClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.DestroyServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.AbstractDeployStrategyStage
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForUpInstancesTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.AddServerGroupEntityTagsTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.CreateServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import static java.util.concurrent.TimeUnit.MINUTES

@Slf4j
@Component
class CreateServerGroupStage extends AbstractDeployStrategyStage {
  public static final String PIPELINE_CONFIG_TYPE = "createServerGroup"

  @Autowired
  private FeaturesService featuresService

  @Autowired
  private RollbackClusterStage rollbackClusterStage

  @Autowired
  private DestroyServerGroupStage destroyServerGroupStage

  CreateServerGroupStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  protected List<TaskNode.TaskDefinition> basicTasks(Stage stage) {
    def taggingEnabled = featuresService.areEntityTagsAvailable()

    def tasks = [
      TaskNode.task("createServerGroup", CreateServerGroupTask),
      TaskNode.task("monitorDeploy", MonitorKatoTask),
      TaskNode.task("forceCacheRefresh", ServerGroupCacheForceRefreshTask),
    ]

    if (taggingEnabled) {
      tasks << TaskNode.task("tagServerGroup", AddServerGroupEntityTagsTask)
    }

    tasks << TaskNode.task("waitForUpInstances", WaitForUpInstancesTask)
    tasks << TaskNode.task("forceCacheRefresh", ServerGroupCacheForceRefreshTask)

    return tasks
  }

  @Override
  void onFailureStages(@Nonnull Stage stage, StageGraphBuilder graph) {
    def stageData = stage.mapTo(StageData)
    if (!stageData.rollback?.onFailure) {
      super.onFailureStages(stage, graph)

      // rollback on failure is not enabled
      return
    }

    if (!stageData.getServerGroup()) {
      super.onFailureStages(stage, graph)

      // did not get far enough to create a new server group
      log.warn("No server group was created, skipping rollback! (executionId: ${stage.execution.id}, stageId: ${stage.id})")
      return
    }

    def strategySupportsRollback = false
    def additionalRollbackContext = [:]

    def strategy = Strategy.fromStrategy(stageData.strategy)
    if (strategy == Strategy.ROLLING_RED_BLACK) {
      // rollback is always supported regardless of where the failure occurred
      strategySupportsRollback = true
      additionalRollbackContext.enableAndDisableOnly = true
    } else if (strategy == Strategy.RED_BLACK) {
      // rollback is only supported if the failure occurred launching the new server group
      // no rollback should be attempted if the failure occurs while tearing down the old server group
      strategySupportsRollback = stage.tasks.any { it.status == ExecutionStatus.TERMINAL }
      additionalRollbackContext.disableOnly = true
    }

    if (strategySupportsRollback) {
      graph.add {
        it.type = rollbackClusterStage.type
        it.name = "Rollback ${stageData.cluster}"
        it.context = [
          "credentials"              : stageData.credentials,
          "cloudProvider"            : stageData.cloudProvider,
          "regions"                  : [stageData.region],
          "serverGroup"              : stageData.serverGroup,
          "stageTimeoutMs"           : MINUTES.toMillis(30), // timebox a rollback to 30 minutes
          "additionalRollbackContext": additionalRollbackContext
        ]
      }
    }

    if (stageData.rollback?.destroyLatest) {
      graph.add {
        it.type = destroyServerGroupStage.type
        it.name = "Destroy ${stageData.serverGroup}"
        it.context = [
          "cloudProvider"     : stageData.cloudProvider,
          "cloudProviderType" : stageData.cloudProvider,
          "cluster"           : stageData.cluster,
          "credentials"       : stageData.credentials,
          "region"            : stageData.region,
          "serverGroupName"   : stageData.serverGroup,
          "stageTimeoutMs"    : MINUTES.toMillis(5) // timebox a destroy to 5 minutes
        ]
      }
    }

    // any on-failure stages from the parent should be executed _after_ the rollback completes
    super.onFailureStages(stage, graph)
  }

  private static class StageData {
    String application
    String account
    String credentials
    String cloudProvider
    Moniker moniker

    String strategy
    Rollback rollback

    @JsonProperty("deploy.server.groups")
    Map<String, List<String>> deployedServerGroups = [:]

    String getCredentials() {
      return account ?: credentials
    }

    String getRegion() {
      return deployedServerGroups?.keySet()?.getAt(0)
    }

    String getServerGroup() {
      return deployedServerGroups.values().flatten().getAt(0)
    }

    String getCluster() {
      return moniker?.cluster ?: MonikerHelper.friggaToMoniker(getServerGroup()).cluster
    }
  }

  private static class Rollback {
    Boolean onFailure
    Boolean destroyLatest
  }
}
