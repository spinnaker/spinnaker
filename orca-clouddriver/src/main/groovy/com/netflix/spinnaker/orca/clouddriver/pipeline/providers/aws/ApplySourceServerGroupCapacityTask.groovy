/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws

import groovy.util.logging.Slf4j
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.AbstractServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.getType

@Slf4j
@Component
class ApplySourceServerGroupCapacityTask extends AbstractServerGroupTask {
  String serverGroupAction = getType(ResizeServerGroupStage)

  @Autowired
  OortHelper oortHelper

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  ExecutionRepository executionRepository

  @Override
  Map convert(Stage stage) {
    try {
      def ancestorDeployStage = getAncestorDeployStage(executionRepository, stage)
      def ancestorDeployStageData = ancestorDeployStage.mapTo(DeployStageData)

      def deployServerGroup = oortHelper.getTargetServerGroup(
        ancestorDeployStageData.account,
        ancestorDeployStageData.deployedServerGroupName,
        ancestorDeployStageData.region,
        getCloudProvider(ancestorDeployStage)
      ).get()

      def minCapacity = Math.min(
        ancestorDeployStageData.sourceServerGroupCapacitySnapshot.min as Long,
        deployServerGroup.capacity.min as Long
      )

      log.info("Restoring capacity of ${ancestorDeployStageData.region}/${deployServerGroup.name} to ${minCapacity} (currentMin: ${deployServerGroup.capacity.min}, snapshotMin: ${ancestorDeployStageData.sourceServerGroupCapacitySnapshot.min})")

      return [
        credentials    : getCredentials(stage),
        regions        : [ancestorDeployStageData.region],
        region         : ancestorDeployStageData.region,
        asgName        : deployServerGroup.name,
        serverGroupName: deployServerGroup.name,
        capacity       : deployServerGroup.capacity + [
          // only update the min capacity, desired + max should be inherited from the current server group
          min: minCapacity
        ]
      ]
    } catch (Exception e) {
      log.error("Unable to apply source server group capacity (executionId: ${stage.execution.id})", e)
      return null
    }
  }

  /**
   * Look up the ancestor deploy stage that contains the source server group's capacity snapshot.
   *
   * This can either be in the current pipeline or a dependent child pipeline in the event of a 'custom' deploy strategy.
   */
  static Stage getAncestorDeployStage(ExecutionRepository executionRepository, Stage stage) {
    def deployStage = stage.ancestors { Stage ancestorStage, StageDefinitionBuilder stageBuilder ->
      ancestorStage.context.containsKey("sourceServerGroupCapacitySnapshot")
    }[0].stage

    if (deployStage.context.strategy == "custom") {
      def pipelineStage = stage.execution.stages.find {
        it.type == "pipeline" && it.parentStageId == deployStage.id
      }
      def pipeline = executionRepository.retrievePipeline(pipelineStage.context.executionId as String)
      deployStage = pipeline.stages.find {
        it.context.type == "createServerGroup" && it.context.containsKey("deploy.server.groups")
      }
    }

    return deployStage
  }

  static class DeployStageData extends StageData {
    @JsonProperty("deploy.server.groups")
    Map<String, Set<String>> deployServerGroups = [:]

    Map sourceServerGroupCapacitySnapshot
    String zone

    @JsonIgnore
    String getDeployedServerGroupName() {
      return deployServerGroups.values().flatten().first()
    }
  }
}
