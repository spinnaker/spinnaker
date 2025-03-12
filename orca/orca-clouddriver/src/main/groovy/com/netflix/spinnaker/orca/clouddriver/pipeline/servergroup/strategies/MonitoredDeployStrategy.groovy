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

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
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
import com.netflix.spinnaker.orca.pipeline.StageExecutionFactory
import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import java.util.concurrent.TimeUnit

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
  List<StageExecution> composeBeforeStages(StageExecution stage) {
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
  List<StageExecution> composeAfterStages(StageExecution stage) {
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

    stages << StageExecutionFactory.newStage(
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
      def pinContext = new HashMap<String, Object>(baseContext)
      pinContext.putAll([
        serverGroupName   : source.serverGroupName,
        action            : ResizeStrategy.ResizeAction.scale_to_server_group,
        source            : source,
        useNameAsLabel    : true,     // hint to deck that it should _not_ override the name
        pinMinimumCapacity: true
      ])

      stages << StageExecutionFactory.newStage(
        stage.execution,
        PinServerGroupStage.TYPE,
        "Pin ${pinContext.serverGroupName}",
        pinContext,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
    }

    if (stageData.deploymentMonitor?.id) {
      def notifyDeployStartingStage = StageExecutionFactory.newStage(
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

    def resizeContextBase = baseContext + [
        target                       : TargetServerGroup.Params.Target.current_asg_dynamic,
        targetLocation               : cleanupConfig.location,
        scalePct                     : 0,
        pinCapacity                  : true,
        unpinMinimumCapacity         : false,
        pinMinimumCapacity           : true,
        useNameAsLabel               : true,     // hint to deck that it should _not_ override the name
        targetHealthyDeployPercentage: stage.context.targetHealthyDeployPercentage
    ]

    if (source) {
      resizeContextBase = resizeContextBase + [
          action: ResizeStrategy.ResizeAction.scale_to_server_group,
          source: source
      ]
    } else {
      resizeContextBase = resizeContextBase + [
          action: ResizeStrategy.ResizeAction.scale_exact,
      ]
      resizeContextBase.capacity = savedCapacity // will scale to a percentage of that static capacity
    }

    // java .forEach rather than groovy .each, since the nested .each closure sometimes omits parent context
    deploySteps.forEach({ p ->
      Map resizeContext = new HashMap<String, Object>(resizeContextBase)
      resizeContext.scalePct = p

      log.info("Adding `Grow ${internalStageData.newServerGroup} to $p% of Desired Size` stage with context $resizeContext [executionId=${stage.execution.id}]")

      def resizeStage = StageExecutionFactory.newStage(
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

        def disablePortionStage = StageExecutionFactory.newStage(
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

        StageExecution evaluateHealthStage = StageExecutionFactory.newStage(
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

    // Unpin target
    def unpinTargetContext = new HashMap<String, Object>(resizeContextBase)
    unpinTargetContext.putAll([
        serverGroupName     : internalStageData.newServerGroup,
        scalePct            : 100,
        useNameAsLabel      : true,     // hint to deck that it should _not_ override the name
        unpinMinimumCapacity: true,
        pinMinimumCapacity  : false,
        pinCapacity         : false,
    ])

    stages << StageExecutionFactory.newStage(
        stage.execution,
        PinServerGroupStage.TYPE,
        "Unpin ${internalStageData.newServerGroup}",
        unpinTargetContext,
        stage,
        SyntheticStageOwner.STAGE_AFTER
    )

    // only scale down if we have a source server group to scale down
    if (source && stageData.scaleDown) {
      if (stageData?.getDelayBeforeScaleDown()) {
        def waitContext = [waitTime: stageData?.getDelayBeforeScaleDown()]
        stages << StageExecutionFactory.newStage(
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
      StageExecution scaleDownStage = StageExecutionFactory.newStage(
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

    // Only unpin source if we have a source ASG and we didn't scale it down
    if (source && !stageData.scaleDown) {
      def unpinSourceContext = new HashMap<String, Object>(baseContext)
      unpinSourceContext.putAll([
        serverGroupName     : source.serverGroupName,
        action              : ResizeStrategy.ResizeAction.scale_to_server_group,
        source              : source,
        useNameAsLabel      : true,     // hint to deck that it should _not_ override the name
        unpinMinimumCapacity: true
      ])

      stages << StageExecutionFactory.newStage(
        stage.execution,
        PinServerGroupStage.TYPE,
        "Unpin ${unpinSourceContext.serverGroupName}",
          unpinSourceContext,
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
      StageExecution shrinkClusterStage = StageExecutionFactory.newStage(
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
      StageExecution notifyDeployCompletedStage = StageExecutionFactory.newStage(
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
  List<StageExecution> composeOnFailureStages(StageExecution parent) {
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

    stages.addAll(composeRollbackStages(parent, source))

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

    stages << StageExecutionFactory.newStage(
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
      stages << StageExecutionFactory.newStage(
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

  List<StageExecution> composeRollbackStages(StageExecution parent, ResizeStrategy.Source source) {
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

    List<StageExecution> stages = new ArrayList<>()

    stages << StageExecutionFactory.newStage(
      parent.execution,
      RollbackClusterStage.PIPELINE_CONFIG_TYPE,
      "Rollback ${stageData.cluster}",
      [
        credentials              : stageData.credentials,
        cloudProvider            : stageData.cloudProvider,
        regions                  : [stageData.region],
        serverGroup              : stageData.serverGroup,
        originalServerGroup      : source.serverGroupName,
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
      stages << StageExecutionFactory.newStage(
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

  ResizeStrategy.Source lookupSourceServerGroup(StageExecution stage) {
    ResizeStrategy.Source source = null
    StageData.Source sourceServerGroup

    StageExecution parentCreateServerGroupStage = stage.directAncestors()
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
