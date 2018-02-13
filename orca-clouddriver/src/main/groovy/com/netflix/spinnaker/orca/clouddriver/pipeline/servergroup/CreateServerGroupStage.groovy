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

import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.clouddriver.FeaturesService
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.RollbackClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.AbstractDeployStrategyStage
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForUpInstancesTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.CreateServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.AddServerGroupEntityTagsTask
import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull
import java.util.concurrent.TimeUnit

@Slf4j
@Component
class CreateServerGroupStage extends AbstractDeployStrategyStage {
  public static final String PIPELINE_CONFIG_TYPE = "createServerGroup"

  @Autowired
  private FeaturesService featuresService

  @Autowired
  private RollbackClusterStage rollbackClusterStage

  CreateServerGroupStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  protected List<TaskNode.TaskDefinition> basicTasks(Stage stage) {
    def taggingEnabled = featuresService.isStageAvailable("upsertEntityTags")

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
  List<Stage> onFailureStages(@Nonnull Stage stage) {
    def stageData = stage.mapTo(StageData)

    if (!stageData.rollback?.onFailure) {
      // rollback on failure is not enabled
      return []
    }

    if (!stageData.getServerGroup()) {
      // did not get far enough to create a new server group
      log.warn("No server group was created, skipping rollback! (executionId: ${stage.execution.id}, stageId: ${stage.id})")

      return []
    }

    return [
      newStage(
        stage.execution,
        rollbackClusterStage.type,
        "Rollback ${stageData.getCluster()}",
        [
          "credentials"   : stageData.getCredentials(),
          "cloudProvider" : stageData.getCloudProvider(),
          "regions"       : [stageData.getRegion()],
          "serverGroup"   : stageData.getServerGroup(),
          "stageTimeoutMs": TimeUnit.MINUTES.toMillis(30) // timebox a rollback to 30 minutes
        ],
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
    ]
  }

  private static class StageData {
    String application
    String account
    String credentials
    String cloudProvider
    Moniker moniker

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
  }
}
