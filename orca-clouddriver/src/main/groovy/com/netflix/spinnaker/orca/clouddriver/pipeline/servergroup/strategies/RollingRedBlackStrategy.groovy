package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies

import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.DisableClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.DetermineTargetServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver
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

    Map originalCapacity = stageData.originalCapacity ?: stageData.capacity
    if (!originalCapacity) {
      originalCapacity = [
          min: stageData.targetSize,
          max: stageData.targetSize,
          desired: stageData.targetSize
      ]
    }

    if (stageData.targetSize) {
      stage.context.targetSize = 0
    }

    if (stage.context.useSourceCapacity) {
      List<TargetServerGroup> target = targetServerGroupResolver.resolve(new Stage(null, null, null, baseContext + [target: TargetServerGroup.Params.Target.current_asg_dynamic]))
      if (target.size() > 0) {
        originalCapacity = target.get(0).capacity
      }
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
        target: TargetServerGroup.Params.Target.current_asg_dynamic,
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
      def resizeContext = baseContext + [
          target: TargetServerGroup.Params.Target.current_asg_dynamic,
          targetLocation: cleanupConfig.location,
          capacity: makeIncrementalCapacity(originalCapacity, p)
      ]

      stages << newStage(
          stage.execution,
          resizeServerGroupStage.type,
          "Grow to $p% Desired Size",
          resizeContext,
          stage,
          SyntheticStageOwner.STAGE_AFTER
      )

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

  private Map makeIncrementalCapacity(Map originalCapacity, Integer p) {

    if (p == 100) {
      return originalCapacity
    }

    def desired = (Integer) originalCapacity.desired * (p / 100d)
    return [
        min: desired,
        max: desired,
        desired: desired
    ]
  }

  ApplicationContext applicationContext
}
