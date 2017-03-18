/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline.strategy

import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.clouddriver.pipeline.AbstractCloudProviderAwareStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.DisableClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ScaleDownClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ShrinkClusterStage
import com.netflix.spinnaker.orca.deprecation.DeprecationRegistry
import com.netflix.spinnaker.orca.front50.pipeline.PipelineStage
import com.netflix.spinnaker.orca.kato.pipeline.ModifyAsgLaunchConfigurationStage
import com.netflix.spinnaker.orca.kato.pipeline.RollingPushStage
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import groovy.transform.CompileDynamic
import groovy.transform.Immutable
import org.springframework.beans.factory.annotation.Autowired

import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.newStage

/**
 * DEPRECATED - Use AbstractDeployStrategyStage instead.
 */
@Deprecated
abstract class DeployStrategyStage extends AbstractCloudProviderAwareStage {

  @Autowired SourceResolver sourceResolver
  @Autowired ModifyAsgLaunchConfigurationStage modifyAsgLaunchConfigurationStage
  @Autowired RollingPushStage rollingPushStage
  @Autowired DeprecationRegistry deprecationRegistry
  @Autowired(required = false) PipelineStage pipelineStage

  @Autowired ShrinkClusterStage shrinkClusterStage
  @Autowired ScaleDownClusterStage scaleDownClusterStage
  @Autowired DisableClusterStage disableClusterStage

  DeployStrategyStage(String name) {
    super(name)
  }

  /**
   * @return the steps for the stage excluding whatever cleanup steps will be
   * handled by the deployment strategy.
   */
  protected
  abstract List<TaskNode.TaskDefinition> basicTasks(Stage stage)

  /**
   * @param stage the stage configuration.
   * @return the details of the cluster that you are deploying to.
   */
  protected CleanupConfig determineClusterForCleanup(Stage stage) {
    def stageData = stage.mapTo(StageData)
    new CleanupConfig(stageData.account, stageData.cluster, stageData.region, stageData.cloudProvider)
  }

  /**
   * @param stage the stage configuration.
   * @return the strategy parameter.
   */
  protected Strategy strategy(Stage stage) {
    def stageData = stage.mapTo(StageData)
    Strategy.fromStrategy(stageData.strategy)
  }

  @Override
  <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
    correctContext(stage)
    def strategy = strategy(stage)

    builder
      .withTask("determineSourceServerGroup", DetermineSourceServerGroupTask)
    if (!strategy.replacesBasicSteps()) {
      basicTasks(stage).each {
        builder.withTask(it.name, it.implementingClass)
      }
    }
  }

  @Override
  <T extends Execution<T>> List<Stage<T>> aroundStages(Stage<T> stage) {
    correctContext(stage)
    def strategy = strategy(stage)
    return strategy.composeFlow(this, stage)
  }

  /**
   * This nasty method is here because of an unfortunate misstep in pipeline configuration that introduced a nested
   * "cluster" key, when in reality we want all of the parameters to be derived from the top level. To preserve
   * functionality (and not break any contracts), this method is employed to move the nested data back to the context's
   * top-level
   */
  private static void correctContext(Stage stage) {
    if (stage.context.containsKey("cluster")) {
      stage.context.putAll(stage.context.cluster as Map)
    }
    stage.context.remove("cluster")
  }

  @VisibleForTesting
  @CompileDynamic
  protected <T extends Execution<T>> List<Stage<T>> composeRedBlackFlow(Stage<T> stage) {
    def stages = []
    def stageData = stage.mapTo(StageData)
    def cleanupConfig = determineClusterForCleanup(stage)

    Map baseContext = [
      regions      : [cleanupConfig.region],
      cluster      : cleanupConfig.cluster,
      credentials  : cleanupConfig.account,
      cloudProvider: cleanupConfig.cloudProvider
    ]

    if (stageData?.maxRemainingAsgs && (stageData?.maxRemainingAsgs > 0)) {
      Map shrinkContext = baseContext + [
        shrinkToSize         : stageData.maxRemainingAsgs,
        allowDeleteActive    : false,
        retainLargerOverNewer: false
      ]
      stages << newStage(
        stage.execution,
        shrinkClusterStage.type,
        "shrinkCluster",
        shrinkContext,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
    }

    injectAfter(stage, "disableCluster", disableClusterStage, baseContext + [
      remainingEnabledServerGroups: 1,
      preferLargerOverNewer       : false
    ])

    if (stageData.scaleDown) {
      def scaleDown = baseContext + [
        allowScaleDownActive         : false,
        remainingFullSizeServerGroups: 1,
        preferLargerOverNewer        : false
      ]
      stages << newStage(
        stage.execution,
        scaleDownClusterStage.type,
        "scaleDown",
        scaleDown,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
    }

    return stages
  }

  protected <T extends Execution<T>> List<Stage<T>> composeRollingPushFlow(Stage<T> stage) {
    def stages = []
    def source = sourceResolver.getSource(stage)

    def modifyCtx = stage.context + [
      region                : source.region,
      regions               : [source.region],
      asgName               : source.asgName,
      'deploy.server.groups': [(source.region): [source.asgName]],
      useSourceCapacity     : true,
      credentials           : source.account,
      source                : [
        asgName          : source.asgName,
        account          : source.account,
        region           : source.region,
        useSourceCapacity: true
      ]
    ]

    stages << newStage(
      stage.execution,
      modifyAsgLaunchConfigurationStage.type,
      "modifyLaunchConfiguration",
      modifyCtx,
      stage,
      SyntheticStageOwner.STAGE_AFTER
    )

    def terminationConfig = stage.mapTo("/termination", TerminationConfig)
    if (terminationConfig.relaunchAllInstances || terminationConfig.totalRelaunches > 0) {
      stages << newStage(
        stage.execution,
        rollingPushStage.type,
        "rollingPush",
        modifyCtx,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
    }

    return stages
  }

  protected <T extends Execution<T>> List<Stage<T>> composeCustomFlow(Stage stage) {
    def stages = []
    def cleanupConfig = determineClusterForCleanup(stage)

    Map parameters = [
      application     : stage.context.application,
      credentials     : cleanupConfig.account,
      cluster         : cleanupConfig.cluster,
      region          : cleanupConfig.region,
      cloudProvider   : cleanupConfig.cloudProvider,
      strategy        : true,
      parentPipelineId: stage.execution.id,
      parentStageId   : stage.id
    ]

    if (stage.context.pipelineParameters) {
      parameters.putAll(stage.context.pipelineParameters as Map)
    }

    Map modifyCtx = [
      application        : stage.context.application,
      pipelineApplication: stage.context.strategyApplication,
      pipelineId         : stage.context.strategyPipeline,
      pipelineParameters : parameters
    ]

    stages << newStage(
      stage.execution,
      pipelineStage.type,
      "pipeline",
      modifyCtx,
      stage,
      SyntheticStageOwner.STAGE_AFTER
    )

    return stages
  }

  @CompileDynamic
  protected <T extends Execution<T>> List<Stage<T>> composeHighlanderFlow(Stage stage) {
    def cleanupConfig = determineClusterForCleanup(stage)
    Map shrinkContext = [
      regions              : [cleanupConfig.region],
      cluster              : cleanupConfig.cluster,
      credentials          : cleanupConfig.account,
      cloudProvider        : cleanupConfig.cloudProvider,
      shrinkToSize         : 1,
      allowDeleteActive    : true,
      retainLargerOverNewer: false
    ]

    return [
      newStage(
        stage.execution,
        shrinkClusterStage.type,
        "shrinkCluster",
        shrinkContext,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
    ]
  }

  @Immutable
  static class CleanupConfig {
    String account
    String cluster
    String region
    String cloudProvider
  }

  @Immutable
  static class TerminationConfig {
    String order
    boolean relaunchAllInstances
    int concurrentRelaunches
    int totalRelaunches
  }
}
