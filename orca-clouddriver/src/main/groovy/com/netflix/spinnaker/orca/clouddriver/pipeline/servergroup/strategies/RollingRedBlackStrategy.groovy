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

import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.DisableClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.DetermineTargetServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver
import com.netflix.spinnaker.orca.front50.pipeline.PipelineStage
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy
import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.newStage

@Slf4j
@Component
class RollingRedBlackStrategy implements Strategy, ApplicationContextAware {
  final String name = "rollingredblack"

  @Autowired
  DisableClusterStage disableClusterStage

  @Autowired
  ResizeServerGroupStage resizeServerGroupStage

  @Autowired
  WaitStage waitStage

  @Autowired
  PipelineStage pipelineStage

  @Autowired
  DetermineTargetServerGroupStage determineTargetServerGroupStage

  @Autowired
  TargetServerGroupResolver targetServerGroupResolver

  @Override
  <T extends Execution<T>> List<Stage<T>> composeFlow(Stage<T> stage) {
    def stages = []
    def stageData = stage.mapTo(RollingRedBlackStageData)
    def cleanupConfig = AbstractDeployStrategyStage.CleanupConfig.fromStage(stage)

    Map baseContext = [
      (cleanupConfig.location.singularType()): cleanupConfig.location.value,
      cluster                                : cleanupConfig.cluster,
      credentials                            : cleanupConfig.account,
      cloudProvider                          : cleanupConfig.cloudProvider,
    ]

    if (stageData.targetSize) {
      stage.context.targetSize = 0
    }

    if (stage.context.useSourceCapacity) {
      stage.context.useSourceCapacity = false
    }

    stage.context.capacity = [
      min    : 0,
      max    : 0,
      desired: 0
    ]

    def targetPercentages = stageData.getTargetPercentages()
    if (targetPercentages.size() == 0 || targetPercentages[-1] != 100) {
      log.info("Inserting implicit 100% final target percentage...")
      targetPercentages.add(100)
    }

    def findContext = baseContext + [
      target        : TargetServerGroup.Params.Target.current_asg_dynamic,
      targetLocation: cleanupConfig.location,
    ]

    stages << newStage(
      stage.execution,
      determineTargetServerGroupStage.type,
      "Determine Deployed Server Group",
      findContext,
      stage,
      SyntheticStageOwner.STAGE_AFTER
    )

    // java .forEach rather than groovy .each, since the nested .each closure sometimes omits parent context
    targetPercentages.forEach({ p ->
      def source = getSource(targetServerGroupResolver, stageData, baseContext)
      def resizeContext = baseContext + [
        target        : TargetServerGroup.Params.Target.current_asg_dynamic,
        action        : ResizeStrategy.ResizeAction.scale_to_server_group,
        source        : source,
        targetLocation: cleanupConfig.location,
        scalePct      : p,
        pinCapacity   : p < 100 // if p = 100, capacity should be unpinned
      ]

      def resizeStage = newStage(
        stage.execution,
        resizeServerGroupStage.type,
        "Grow to $p% Desired Size",
        resizeContext,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
      stages << resizeStage

      // an expression to grab newly deployed server group at runtime (ie. the server group being resized up)
      def deployedServerGroupName = '${' + "#stage('${resizeStage.id}')['context']['asgName']" + '}'.toString()
      stages.addAll(getBeforeCleanupStages(stage, stageData, source, deployedServerGroupName, p))

      def disableContext = baseContext + [
        desiredPercentage           : p,
        remainingEnabledServerGroups: 1,
        preferLargerOverNewer       : false
      ]

      stages << newStage(
        stage.execution,
        disableClusterStage.type,
        "Disable $p% of Traffic",
        disableContext,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
    })

    return stages
  }

  List<Stage> getBeforeCleanupStages(Stage parentStage,
                                     RollingRedBlackStageData stageData,
                                     ResizeStrategy.Source source,
                                     String deployedServerGroupName,
                                     int percentageComplete) {
    def stages = []

    if (stageData.getDelayBeforeCleanup()) {
      def waitContext = [waitTime: stageData.getDelayBeforeCleanup()]
      stages << newStage(
        parentStage.execution,
        waitStage.type,
        "wait",
        waitContext,
        parentStage,
        SyntheticStageOwner.STAGE_AFTER
      )
    }

    if (stageData.pipelineBeforeCleanup?.application && stageData.pipelineBeforeCleanup?.pipelineId) {
      def serverGroupCoordinates = [
        region         : source.region,
        serverGroupName: source.serverGroupName,
        account        : source.credentials,
        cloudProvider  : source.cloudProvider
      ]

      def pipelineContext = [
        pipelineApplication: stageData.pipelineBeforeCleanup.application,
        pipelineId         : stageData.pipelineBeforeCleanup.pipelineId,
        pipelineParameters : [
          "deployedServerGroup": serverGroupCoordinates + [
            serverGroupName: deployedServerGroupName
          ],
          "sourceServerGroup"  : serverGroupCoordinates + [
            serverGroupName: source.serverGroupName
          ],
          "percentageComplete" : percentageComplete
        ]
      ]

      stages << newStage(
        parentStage.execution,
        pipelineStage.type,
        "Run Validation Pipeline",
        pipelineContext,
        parentStage,
        SyntheticStageOwner.STAGE_AFTER
      )
    }

    return stages
  }

  static ResizeStrategy.Source getSource(TargetServerGroupResolver targetServerGroupResolver,
                                         RollingRedBlackStageData stageData,
                                         Map baseContext) {
    if (stageData.source) {
      return new ResizeStrategy.Source(
        region: stageData.source.region,
        serverGroupName: stageData.source.serverGroupName ?: stageData.source.asgName,
        credentials: stageData.credentials ?: stageData.account,
        cloudProvider: stageData.cloudProvider
      )
    }

    // no source server group specified, lookup current server group
    TargetServerGroup target = targetServerGroupResolver.resolve(
      new Stage(null, null, null, baseContext + [target: TargetServerGroup.Params.Target.current_asg_dynamic])
    )?.get(0)

    if (!target) {
      throw new IllegalStateException("No target server groups found (${baseContext})")
    }

    return new ResizeStrategy.Source(
      region: target.getLocation().value,
      serverGroupName: target.getName(),
      credentials: stageData.credentials ?: stageData.account,
      cloudProvider: stageData.cloudProvider
    )
  }

  ApplicationContext applicationContext
}
