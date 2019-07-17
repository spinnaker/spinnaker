package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.dynamicconfig.SpringDynamicConfigService
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ScaleDownClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.DisableServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.DetermineTargetServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard
import com.netflix.spinnaker.orca.front50.pipeline.PipelineStage
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy
import com.netflix.spinnaker.orca.locks.LockingConfigurationProperties
import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import org.springframework.mock.env.MockEnvironment
import spock.lang.Specification
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage


class RollingRedBlackStrategySpec extends Specification {
  def env = new MockEnvironment()
  def config = new LockingConfigurationProperties(new SpringDynamicConfigService(environment: env))
  def dynamicConfigService = Mock(DynamicConfigService)
  def trafficGuard = Mock(TrafficGuard)
  def disableServerGroupStage = new DisableServerGroupStage(dynamicConfigService)
  def scaleDownClusterStage = new ScaleDownClusterStage(trafficGuard, config, dynamicConfigService)
  def resizeServerGroupStage = new ResizeServerGroupStage()
  def waitStage = new WaitStage()
  def pipelineStage = Mock(PipelineStage)
  def determineTargetServerGroupStage = new DetermineTargetServerGroupStage()

  def "should compose flow"() {
    given:
    Moniker moniker = new Moniker(app: "unit", stack: "tests")
    def fallbackCapacity = [min: 1, desired: 2, max: 3]

    def pipeline = pipeline {
      application = "orca"
      stage {
        refId = "1-create"
        type = "createServerGroup"
        context = [
          refId: "stage_createASG"
        ]

        stage {
          refId = "2-deploy1"
          parent
          context = [
            refId                        : "stage",
            account                      : "testAccount",
            application                  : "unit",
            stack                        : "tests",
            moniker                      : moniker,
            cloudProvider                : "aws",
            region                       : "north",
            availabilityZones            : [north: ["pole-1a"]],
            source                       : [:],  // pretend there is no pre-existing server group
            capacity                     : fallbackCapacity,
            targetHealthyDeployPercentage: 90
          ]
        }

        stage {
          refId = "2-deploy2"
          parent
          context = [
            refId                        : "stage",
            account                      : "testAccount",
            application                  : "unit",
            stack                        : "tests",
            moniker                      : moniker,
            cloudProvider                : "aws",
            region                       : "north",
            availabilityZones            : [north: ["pole-1a"]],
            source                       : [:],  // pretend there is no pre-existing server group
            capacity                     : fallbackCapacity,
            targetHealthyDeployPercentage: 90,
            targetPercentages            : [50]
          ]
        }
      }
    }

    def strat = new RollingRedBlackStrategy(
      scaleDownClusterStage: scaleDownClusterStage,
      disableServerGroupStage: disableServerGroupStage,
      resizeServerGroupStage: resizeServerGroupStage,
      waitStage: waitStage,
      pipelineStage: pipelineStage,
      determineTargetServerGroupStage: determineTargetServerGroupStage,
    )

    when: 'planning with no targetPercentages'
    def stage = pipeline.stageByRef("2-deploy1")
    def syntheticStages = strat.composeFlow(stage)
    def beforeStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
    def afterStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then: 'default to rolling out at 100%'
    // we expect the output context to have a capacity of 0 so that the new asg gets created at with 0 instances
    stage.context.capacity == [min: 0, desired: 0, max: 0]

    beforeStages.isEmpty()
    afterStages.size() == 2
    afterStages.first().type == determineTargetServerGroupStage.type

    // check that we roll out at 100% of the fallback capacity
    afterStages.last().type == resizeServerGroupStage.type
    afterStages.last().name == "Grow to 100% of Desired Size"
    afterStages.last().context.action == ResizeStrategy.ResizeAction.scale_exact
    afterStages.last().context.capacity == fallbackCapacity


    when: 'planning with [targetPercentages: [50]'
    stage = pipeline.stageByRef("2-deploy2")
    syntheticStages = strat.composeFlow(stage)
    beforeStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
    afterStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then: 'we roll out at 50% and then 100%'
    beforeStages.isEmpty()
    afterStages.size() == 3
    afterStages.first().type == determineTargetServerGroupStage.type

    // check that we grow to 50% of the fallback capacity and add a 100% step
    afterStages.get(1).type == resizeServerGroupStage.type
    afterStages.get(1).name == "Grow to 50% of Desired Size"
    afterStages.get(1).context.action == ResizeStrategy.ResizeAction.scale_exact
    afterStages.get(1).context.capacity == fallbackCapacity

    // also verify that targetHealthyDeployPercentage from the base stage context percolates down to the resize context
    afterStages.get(1).context.targetHealthyDeployPercentage == stage.context.targetHealthyDeployPercentage

    afterStages.get(2).type == resizeServerGroupStage.type
    afterStages.get(2).name == "Grow to 100% of Desired Size"
    afterStages.get(2).context.action == ResizeStrategy.ResizeAction.scale_exact
    afterStages.get(2).context.capacity == fallbackCapacity

  }
}
