/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies

import com.netflix.spinnaker.orca.deploymentmonitor.DeploymentMonitorServiceProvider
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.RollbackClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ScaleDownClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ShrinkClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.monitoreddeploy.NotifyDeployCompletedStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.monitoreddeploy.NotifyDeployStartingStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.monitoreddeploy.EvaluateDeploymentHealthStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.CloneServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.CreateServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.DestroyServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.DisableServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.PinServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.DetermineTargetServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.deploymentmonitor.models.DeploymentMonitorStageConfig
import com.netflix.spinnaker.orca.deploymentmonitor.models.MonitoredDeployInternalStageData
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import java.util.concurrent.TimeUnit

import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.newStage
import static com.netflix.spinnaker.orca.kato.pipeline.strategy.Strategy.MONITORED
import static java.util.concurrent.TimeUnit.MINUTES

@Slf4j
@Component
@ConditionalOnProperty(value = "monitored-deploy.enabled")
class MonitoredDeployStrategy implements Strategy {
  final String name = MONITORED.key

  @Autowired
  DeploymentMonitorServiceProvider deploymentMonitorServiceProvider

  @Override
  List<Stage> composeBeforeStages(Stage stage) {
    def stageData = stage.mapTo(MonitoredDeployStageData)

    if (stageData.deploymentMonitor?.id) {
      // Before we begin deploy, just validate that the given deploy monitor is registered
      // Note: getDefinitionById will throw if no monitor is registered with the given ID
      deploymentMonitorServiceProvider.getDefinitionById(stageData.deploymentMonitor.id)
    }

    if (stage.context.useSourceCapacity) {
      stage.context.useSourceCapacity = false
    }

    // we expect a capacity object if a fixed capacity has been requested or as a fallback value when we are copying
    // the capacity from the current server group
    def savedCapacity = stage.context.savedCapacity ?: stage.context.capacity?.clone()
    stage.context.savedCapacity = savedCapacity

    stage.context.capacity = [
      min    : 0,
      max    : 0,
      desired: 0
    ]

    // Don't allow old-school "rollback" key in the deploy stage, we handle our own rollback
    stage.context.remove("rollback")

    return Collections.emptyList()
  }

  @Override
  List<Stage> composeAfterStages(Stage stage) {
    def stages = []
    def stageData = stage.mapTo(MonitoredDeployStageData)
    def cleanupConfig = AbstractDeployStrategyStage.CleanupConfig.fromStage(stage)

    Map baseContext = [
      (cleanupConfig.location.singularType()): cleanupConfig.location.value,
      cluster                                : cleanupConfig.cluster,
      moniker                                : cleanupConfig.moniker,
      credentials                            : cleanupConfig.account,
      cloudProvider                          : cleanupConfig.cloudProvider,
    ]

    // we expect a capacity object if a fixed capacity has been requested or as a fallback value when we are copying
    // the capacity from the current server group
    def savedCapacity = stage.context.savedCapacity

    def deploySteps = stageData.getDeploySteps()
    if (deploySteps.isEmpty() || deploySteps[-1] != 100) {
      deploySteps.add(100)
    }

    // Get source ASG from prior determineSourceServerGroupTask
    def source = null

    try {
      source = lookupSourceServerGroup(stage)
    } catch (Exception e) {
      // This probably means there was no parent CreateServerGroup stage - which should never happen
      throw new IllegalStateException("Failed to determine source server group from parent stage while planning Monitored Deploy flow", e)
    }

    // TODO(mvulfson): I don't love this
    MonitoredDeployInternalStageData internalStageData = new MonitoredDeployInternalStageData()
    internalStageData.account = baseContext.credentials
    internalStageData.cloudProvider = baseContext.cloudProvider
    internalStageData.region = baseContext.region
    internalStageData.oldServerGroup = source?.serverGroupName
    internalStageData.deploymentMonitor = stageData.deploymentMonitor

    CreateServerGroupStage.StageData createServerStageData = stage.mapTo(CreateServerGroupStage.StageData)
    internalStageData.newServerGroup = createServerStageData.getServerGroup()

    def evalContext = internalStageData.toContextMap()

    def findContext = baseContext + [
      //target        : TargetServerGroup.Params.Target.current_asg_dynamic,
      serverGroupName: internalStageData.newServerGroup,
      targetLocation : cleanupConfig.location,
    ]

    stages << newStage(
      stage.execution,
      DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE,
      "Determine Deployed Server Group",
      findContext,
      stage,
      SyntheticStageOwner.STAGE_AFTER
    )

    if (source == null) {
      log.info("no source server group -- will perform Monitored Deploy to exact fallback capacity $savedCapacity with no disableCluster or scaleDownCluster stages")
    } else {
      def resizeContext = baseContext
      resizeContext.putAll([
        serverGroupName   : source.serverGroupName,
        action            : ResizeStrategy.ResizeAction.scale_to_server_group,
        source            : source,
        useNameAsLabel    : true,     // hint to deck that it should _not_ override the name
        pinMinimumCapacity: true
      ])

      stages << newStage(
        stage.execution,
        PinServerGroupStage.TYPE,
        "Pin ${resizeContext.serverGroupName}",
        resizeContext,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
    }

    if (stageData.deploymentMonitor?.id) {
      def notifyDeployStartingStage = newStage(
        stage.execution,
        NotifyDeployStartingStage.PIPELINE_CONFIG_TYPE,
        "Notify monitored deploy starting",
        evalContext,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
      notifyDeployStartingStage.setAllowSiblingStagesToContinueOnFailure(true)
      stages << notifyDeployStartingStage
    } else {
      log.warn("No deployment monitor specified, all monitoring will be skipped")
    }

    // java .forEach rather than groovy .each, since the nested .each closure sometimes omits parent context
    deploySteps.forEach({ p ->
      def resizeContext = baseContext + [
        target                       : TargetServerGroup.Params.Target.current_asg_dynamic,
        targetLocation               : cleanupConfig.location,
        scalePct                     : p,
        pinCapacity                  : p < 100,  // if p < 100, capacity should be pinned (min == max == desired)
        unpinMinimumCapacity         : p == 100, // if p == 100, min capacity should be restored to the original unpinned value from source
        pinMinimumCapacity           : p < 100,  // pinMinimumCapacity should be false when unpinMinimumCapacity is true
        useNameAsLabel               : true,     // hint to deck that it should _not_ override the name
        targetHealthyDeployPercentage: stage.context.targetHealthyDeployPercentage
      ]

      if (source) {
        resizeContext = resizeContext + [
          action: ResizeStrategy.ResizeAction.scale_to_server_group,
          source: source
        ]
      } else {
        resizeContext = resizeContext + [
          action: ResizeStrategy.ResizeAction.scale_exact,
        ]
        resizeContext.capacity = savedCapacity // will scale to a percentage of that static capacity
      }

      log.info("Adding `Grow ${internalStageData.newServerGroup} to $p% of Desired Size` stage with context $resizeContext [executionId=${stage.execution.id}]")

      def resizeStage = newStage(
        stage.execution,
        ResizeServerGroupStage.TYPE,
        "Grow ${internalStageData.newServerGroup} to $p% of Desired Size",
        resizeContext,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
      stages << resizeStage

      // only generate the "disable p% of traffic" stages if we have something to disable
      if (source) {
        def disableContext = baseContext + [
          desiredPercentage: p,
          serverGroupName  : source.serverGroupName,
          useNameAsLabel   : true,     // hint to deck that it should _not_ override the name
        ]

        log.info("Adding `Disable $p% of Traffic on ${source.serverGroupName}` stage with context $disableContext [executionId=${stage.execution.id}]")

        def disablePortionStage = newStage(
          stage.execution,
          DisableServerGroupStage.PIPELINE_CONFIG_TYPE,
          "Disable $p% of Traffic on ${source.serverGroupName}",
          disableContext,
          stage,
          SyntheticStageOwner.STAGE_AFTER
        )
        stages << disablePortionStage
      }

      if (stageData.deploymentMonitor?.id) {
        evalContext.currentProgress = p

        Stage evaluateHealthStage = newStage(
          stage.execution,
          EvaluateDeploymentHealthStage.PIPELINE_CONFIG_TYPE,
          "Evaluate health of deployed instances",
          evalContext,
          stage,
          SyntheticStageOwner.STAGE_AFTER
        )
        evaluateHealthStage.setAllowSiblingStagesToContinueOnFailure(true)

        stages << evaluateHealthStage
      }
    })

    // only scale down if we have a source server group to scale down
    if (source && stageData.scaleDown) {
      if (stageData?.getDelayBeforeScaleDown()) {
        def waitContext = [waitTime: stageData?.getDelayBeforeScaleDown()]
        stages << newStage(
          stage.execution,
          WaitStage.STAGE_TYPE,
          "Wait Before Scale Down",
          waitContext,
          stage,
          SyntheticStageOwner.STAGE_AFTER
        )
      }

      def scaleDown = baseContext + [
        allowScaleDownActive         : false,
        remainingFullSizeServerGroups: 1,
        preferLargerOverNewer        : false,

      ]
      Stage scaleDownStage = newStage(
        stage.execution,
        ScaleDownClusterStage.PIPELINE_CONFIG_TYPE,
        "scaleDown",
        scaleDown,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )

      scaleDownStage.setAllowSiblingStagesToContinueOnFailure(true)
      scaleDownStage.setContinuePipelineOnFailure(true)
      stages << scaleDownStage
    }

    // Only unpin if we have a source ASG and we didn't scale it down
    if (source && !stageData.scaleDown) {
      def resizeContext = baseContext
      resizeContext.putAll([
        serverGroupName     : source.serverGroupName,
        action              : ResizeStrategy.ResizeAction.scale_to_server_group,
        source              : source,
        useNameAsLabel      : true,     // hint to deck that it should _not_ override the name
        unpinMinimumCapacity: true
      ])

      stages << newStage(
        stage.execution,
        PinServerGroupStage.TYPE,
        "Unpin ${resizeContext.serverGroupName}",
        resizeContext,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
    }

    if (stageData?.maxRemainingAsgs && (stageData?.maxRemainingAsgs > 0)) {
      Map shrinkContext = baseContext + [
        shrinkToSize         : stageData.maxRemainingAsgs,
        allowDeleteActive    : false,
        retainLargerOverNewer: false
      ]
      Stage shrinkClusterStage = newStage(
        stage.execution,
        ShrinkClusterStage.STAGE_TYPE,
        "shrinkCluster",
        shrinkContext,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )

      shrinkClusterStage.setAllowSiblingStagesToContinueOnFailure(true)
      shrinkClusterStage.setContinuePipelineOnFailure(true)
      stages << shrinkClusterStage
    }

    if (stageData.deploymentMonitor?.id) {
      Stage notifyDeployCompletedStage = newStage(
        stage.execution,
        NotifyDeployCompletedStage.PIPELINE_CONFIG_TYPE,
        "Notify monitored deploy complete",
        evalContext + [hasDeploymentFailed: false],
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )

      notifyDeployCompletedStage.setAllowSiblingStagesToContinueOnFailure(true)
      notifyDeployCompletedStage.setContinuePipelineOnFailure(true)
      stages << notifyDeployCompletedStage
    }

    return stages
  }

  @Override
  List<Stage> composeOnFailureStages(Stage parent) {
    def source = null
    def stages = []

    try {
      source = lookupSourceServerGroup(parent)
    } catch (Exception e) {
      log.warn("Failed to lookup source server group during composeOnFailureStages", e)
    }

    // No source, nothing to unpin
    if (source == null) {
      return stages
    }

    stages.addAll(composeRollbackStages(parent))

    def cleanupConfig = AbstractDeployStrategyStage.CleanupConfig.fromStage(parent)

    Map baseContext = [
      (cleanupConfig.location.singularType()): cleanupConfig.location.value,
      cluster                                : cleanupConfig.cluster,
      moniker                                : cleanupConfig.moniker,
      credentials                            : cleanupConfig.account,
      cloudProvider                          : cleanupConfig.cloudProvider,
    ]

    def resizeContext = baseContext
    resizeContext.putAll([
      serverGroupName     : source.serverGroupName,
      action              : ResizeStrategy.ResizeAction.scale_to_server_group,
      source              : source,
      useNameAsLabel      : true,     // hint to deck that it should _not_ override the name
      unpinMinimumCapacity: true,
      // we want to specify a new timeout explicitly here, in case the deploy itself failed because of a timeout
      stageTimeoutMs      : TimeUnit.MINUTES.toMillis(20)
    ])

    stages << newStage(
      parent.execution,
      PinServerGroupStage.TYPE,
      "Unpin ${resizeContext.serverGroupName}",
      resizeContext,
      parent,
      SyntheticStageOwner.STAGE_AFTER
    )

    MonitoredDeployStageData stageData = parent.mapTo(MonitoredDeployStageData)
    if (stageData.deploymentMonitor?.id) {
      CreateServerGroupStage.StageData createServerStageData = parent.mapTo(CreateServerGroupStage.StageData)
      MonitoredDeployInternalStageData internalStageData = new MonitoredDeployInternalStageData()
      internalStageData.account = baseContext.credentials
      internalStageData.cloudProvider = baseContext.cloudProvider
      internalStageData.region = baseContext.region
      internalStageData.oldServerGroup = source?.serverGroupName
      internalStageData.newServerGroup = createServerStageData.getServerGroup()
      internalStageData.deploymentMonitor = stageData.deploymentMonitor
      internalStageData.hasDeploymentFailed = true;

      Map evalContext = internalStageData.toContextMap()
      stages << newStage(
        parent.execution,
        NotifyDeployCompletedStage.PIPELINE_CONFIG_TYPE,
        "Notify monitored deploy complete",
        evalContext,
        parent,
        SyntheticStageOwner.STAGE_AFTER
      )
    }

    return stages
  }

  List<Stage> composeRollbackStages(Stage parent) {
    CreateServerGroupStage.StageData stageData = parent.mapTo(CreateServerGroupStage.StageData)
    MonitoredDeployStageData monitoredDeployStageData = parent.mapTo(MonitoredDeployStageData)
    String deployedServerGroupName = stageData.getServerGroup()

    // Does the user want an automatic rollback?
    if ((monitoredDeployStageData.failureActions.rollback != FailureActions.RollbackType.Automatic) &&
      (monitoredDeployStageData.failureActions.rollback != FailureActions.RollbackType.Manual)) {
      log.warn("Not performing automatic rollback on failed deploy of ${deployedServerGroupName ?: '<NO SERVER GROUP CREATED>'} because no rollback was requested by user in pipeline config")
      return Collections.emptyList()
    }

    if (!deployedServerGroupName) {
      // did not get far enough to create a new server group
      log.warn("Not performing automatic rollback because the server group was not created")
      return Collections.emptyList()
    }

    List<Stage> stages = new ArrayList<>()

    stages << newStage(
      parent.execution,
      RollbackClusterStage.PIPELINE_CONFIG_TYPE,
      "Rollback ${stageData.cluster}",
      [
        credentials              : stageData.credentials,
        cloudProvider            : stageData.cloudProvider,
        regions                  : [stageData.region],
        serverGroup              : stageData.serverGroup,
        stageTimeoutMs           : MINUTES.toMillis(30), // timebox a rollback to 30 minutes
        additionalRollbackContext: [
          enableAndDisableOnly: true,
          // When initiating a rollback automatically as part of deployment failure handling, only rollback to a server
          // group that's enabled, as any disabled ones, even if newer, were likely manually marked so for being "bad"
          // (e.g. as part of a manual rollback).
          onlyEnabledServerGroups: true
        ]
      ],
      parent,
      SyntheticStageOwner.STAGE_AFTER
    )

    if (monitoredDeployStageData.failureActions.destroyInstances) {
      stages << newStage(
        parent.execution,
        DestroyServerGroupStage.PIPELINE_CONFIG_TYPE,
        "Destroy ${stageData.serverGroup} due to rollback",
        [
          cloudProvider    : stageData.cloudProvider,
          cloudProviderType: stageData.cloudProvider,
          cluster          : stageData.cluster,
          credentials      : stageData.credentials,
          region           : stageData.region,
          serverGroupName  : stageData.serverGroup,
          stageTimeoutMs   : MINUTES.toMillis(5) // timebox a destroy to 5 minutes
        ],
        parent,
        SyntheticStageOwner.STAGE_AFTER
      )
    }

    return stages
  }

  ResizeStrategy.Source lookupSourceServerGroup(Stage stage) {
    ResizeStrategy.Source source = null
    StageData.Source sourceServerGroup

    Stage parentCreateServerGroupStage = stage.directAncestors()
      .find() {
        it.type == CreateServerGroupStage.PIPELINE_CONFIG_TYPE || it.type == CloneServerGroupStage.PIPELINE_CONFIG_TYPE
      }

    StageData parentStageData = parentCreateServerGroupStage.mapTo(StageData)
    sourceServerGroup = parentStageData.source
    MonitoredDeployStageData stageData = stage.mapTo(MonitoredDeployStageData)

    if (sourceServerGroup != null && (sourceServerGroup.serverGroupName != null || sourceServerGroup.asgName != null)) {
      source = new ResizeStrategy.Source(
        region: sourceServerGroup.region,
        serverGroupName: sourceServerGroup.serverGroupName ?: sourceServerGroup.asgName,
        credentials: stageData.credentials ?: stageData.account,
        cloudProvider: stageData.cloudProvider
      )
    }

    return source
  }
}

// TODO(mvulfson): Move this someplace nice
class Capacity {
  int min
  int max
  int desired
}

class FailureActions {
  enum RollbackType {
    None,
    Automatic,
    Manual
  }

  RollbackType rollback
  boolean destroyInstances

  FailureActions() {
    rollback = RollbackType.None
    destroyInstances = false
  }
}

class MonitoredDeployStageData extends StageData {
  List<Integer> deploySteps = []
  Capacity targetCapacity
  int maxRemainingAsgs
  int scaleDownOldAsgs
  FailureActions failureActions = new FailureActions()
  DeploymentMonitorStageConfig deploymentMonitor

  //Capacity originalCapacity
}
