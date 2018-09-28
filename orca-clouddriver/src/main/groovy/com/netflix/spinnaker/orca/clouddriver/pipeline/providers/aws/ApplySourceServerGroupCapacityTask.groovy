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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.AbstractServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.getType
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE

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
      TargetServerGroupContext operationContext = getTargetServerGroupContext(stage)

      def context = operationContext.context
      def sourceServerGroupCapacitySnapshot = operationContext.sourceServerGroupCapacitySnapshot

      def targetServerGroup = oortHelper.getTargetServerGroup(
        context.credentials as String,
        context.serverGroupName as String,
        context.region as String,
        context.cloudProvider as String
      ).get()

      def minCapacity = Math.min(
        sourceServerGroupCapacitySnapshot.min as Long,
        targetServerGroup.capacity.min as Long
      )

      if (context.cloudProvider == "aws") {
        // aws is the only cloud provider supporting partial resizes
        // updating anything other than 'min' could result in instances being
        // unnecessarily destroyed or created if autoscaling has occurred
        context.capacity = [min: minCapacity]
      } else {
        context.capacity = targetServerGroup.capacity + [
          min: minCapacity
        ]
      }

      log.info("Restoring min capacity of ${context.region}/${targetServerGroup.name} to ${minCapacity} (currentMin: ${targetServerGroup.capacity.min}, snapshotMin: ${sourceServerGroupCapacitySnapshot.min})")

      return context
    } catch (CannotFindAncestorStage e) {
      log.warn("Unable to apply source server group capacity (executionId: ${stage.execution.id})")
      return null
    } catch (Exception e) {
      log.error("Unable to apply source server group capacity (executionId: ${stage.execution.id})", e)
      return null
    }
  }

  @Override
  Moniker convertMoniker(Stage stage) {
    // Used in AbstractServerGroupTask.execute() but not needed here.
    return null
  }

  /**
   * Fetch target server group coordinates and source server group capacity snapshot.
   *
   * This may exist either on the current stage (if a Rollback) or on an upstream deploy stage (if a standard deploy)
   */
  TargetServerGroupContext getTargetServerGroupContext(Stage stage) {
    if (stage.context.target) {
      // target server group coordinates have been explicitly provided (see RollbackServerGroupStage)
      def ancestorCaptureStage = getAncestorSnapshotStage(stage)

      TargetServerGroupCoordinates targetServerGroupCoordinates = stage.mapTo(
        "/target", TargetServerGroupCoordinates
      )

      return new TargetServerGroupContext(
        context: [
          credentials    : targetServerGroupCoordinates.account,
          region         : targetServerGroupCoordinates.region,
          asgName        : targetServerGroupCoordinates.serverGroupName,
          serverGroupName: targetServerGroupCoordinates.serverGroupName,
          cloudProvider  : targetServerGroupCoordinates.cloudProvider
        ],
        sourceServerGroupCapacitySnapshot: ancestorCaptureStage.context.sourceServerGroupCapacitySnapshot as Map<String, Long>
      )
    }

    // target server group coordinates must be retrieved up from the closest ancestral deploy stage
    def ancestorDeployStage = getAncestorDeployStage(executionRepository, stage)

    DeployStageData ancestorDeployStageData = ancestorDeployStage.mapTo(DeployStageData)

    return new TargetServerGroupContext(
      context: [
        credentials    : getCredentials(stage),
        region         : ancestorDeployStageData.region,
        asgName        : ancestorDeployStageData.deployedServerGroupName,
        serverGroupName: ancestorDeployStageData.deployedServerGroupName,
        cloudProvider  : getCloudProvider(ancestorDeployStage)
      ],
      sourceServerGroupCapacitySnapshot: ancestorDeployStageData.sourceServerGroupCapacitySnapshot
    )
  }

  /**
   * Look up the ancestor deploy stage that contains the source server group's capacity snapshot.
   *
   * This can either be in the current pipeline or a dependent child pipeline in the event of a 'custom' deploy strategy.
   */
  static Stage getAncestorDeployStage(ExecutionRepository executionRepository, Stage stage) {
    def deployStage = getAncestorSnapshotStage(stage)
    if (deployStage.context.strategy == "custom") {
      def pipelineStage = stage.execution.stages.find {
        it.type == "pipeline" && it.parentStageId == deployStage.id
      }
      def pipeline = executionRepository.retrieve(PIPELINE, pipelineStage.context.executionId as String)
      deployStage = pipeline.stages.find {
        it.context.type == "createServerGroup" && it.context.containsKey("deploy.server.groups")
      }
    }

    return deployStage
  }

  /**
   * Find an ancestor (or synthetic sibling) stage w/ `sourceServerGroupCapacitySnapshot` in it's context.
   */
  static Stage getAncestorSnapshotStage(Stage stage) {
    def ancestors = stage.ancestors().findAll { ancestorStage ->
      ancestorStage.context.containsKey("sourceServerGroupCapacitySnapshot")
    }

    Stage ancestor = (ancestors != null && !ancestors.isEmpty()) ? ancestors[0] : stage.execution.stages.find {
      // find a synthetic sibling w/ 'sourceServerGroupCapacitySnapshot' in the event of there being no suitable
      // ancestors (ie. rollback stages)
      it.context.containsKey("sourceServerGroupCapacitySnapshot") && it.parentStageId == stage.parentStageId
    }

    if (ancestor == null) {
      throw new CannotFindAncestorStage("No stages with sourceServerGroupCapacitySnapshot context defined in execution ${stage.execution.id}")
    }

    return ancestor
  }

  private static class DeployStageData extends StageData {
    @JsonProperty("deploy.server.groups")
    Map<String, Set<String>> deployServerGroups = [:]

    Map sourceServerGroupCapacitySnapshot
    String zone

    @JsonIgnore
    String getDeployedServerGroupName() {
      return deployServerGroups.values().flatten().first()
    }
  }

  private static class TargetServerGroupCoordinates {
    String region
    String asgName
    String serverGroupName
    String account
    String cloudProvider

    String getAsgName() {
      return getServerGroupName()
    }

    String getServerGroupName() {
      return serverGroupName ?: asgName
    }
  }

  private static class TargetServerGroupContext {
    Map<String, Object> context
    Map<String, Long> sourceServerGroupCapacitySnapshot
  }

  private static class CannotFindAncestorStage extends IllegalStateException {
    CannotFindAncestorStage(String message) {
      super(message)
    }
  }
}
