package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.dynamicconfig.SpringDynamicConfigService
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.DisableClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ScaleDownClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ShrinkClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.DetermineTargetServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard
import com.netflix.spinnaker.orca.front50.pipeline.PipelineStage
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy
import com.netflix.spinnaker.orca.locks.LockingConfigurationProperties
import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.env.MockEnvironment
import spock.lang.Specification

class RollingRedBlackStrategySpec extends Specification {
  def env = new MockEnvironment()
  def config = new LockingConfigurationProperties(new SpringDynamicConfigService(environment: env))
  def dynamicConfigService = Mock(DynamicConfigService)
  def trafficGuard = Mock(TrafficGuard)
  def disableClusterStage = new DisableClusterStage(trafficGuard, config, dynamicConfigService)
  def scaleDownClusterStage = new ScaleDownClusterStage(trafficGuard, config, dynamicConfigService)
  def resizeServerGroupStage = new ResizeServerGroupStage()
  def waitStage = new WaitStage()
  def pipelineStage = Mock(PipelineStage)
  def determineTargetServerGroupStage = new DetermineTargetServerGroupStage()
  def targetServerGroupResolver = Stub(TargetServerGroupResolver)

  def "should compose flow"() {
    given:
    Moniker moniker = new Moniker(app: "unit", stack: "tests")
    def fallbackCapacity = [min: 1, desired: 2, max: 3]
    def ctx = [
      account          : "testAccount",
      application      : "unit",
      stack            : "tests",
      moniker          : moniker,
      cloudProvider    : "aws",
      region           : "north",
      availabilityZones: [ north: ["pole-1a"] ],
      source           : [:],  // pretend there is no pre-existing server group
      capacity         : fallbackCapacity
    ]

    def stage = new Stage(Execution.newPipeline("orca"), "whatever", ctx)
    def strat = new RollingRedBlackStrategy(
      scaleDownClusterStage: scaleDownClusterStage,
      disableClusterStage: disableClusterStage,
      resizeServerGroupStage: resizeServerGroupStage,
      waitStage: waitStage,
      pipelineStage: pipelineStage,
      determineTargetServerGroupStage: determineTargetServerGroupStage,
      targetServerGroupResolver: targetServerGroupResolver
    )

    when: 'planning with no targetPercentages'
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
    stage = new Stage(Execution.newPipeline("orca"), "whatever", ctx + [targetPercentages: [50]])
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

    afterStages.get(2).type == resizeServerGroupStage.type
    afterStages.get(2).name == "Grow to 100% of Desired Size"
    afterStages.get(2).context.action == ResizeStrategy.ResizeAction.scale_exact
    afterStages.get(2).context.capacity == fallbackCapacity
  }
}
