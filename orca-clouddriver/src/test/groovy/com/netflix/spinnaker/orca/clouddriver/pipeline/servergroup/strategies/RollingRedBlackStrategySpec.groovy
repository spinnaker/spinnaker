package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ScaleDownClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.DisableServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.PinServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.DetermineTargetServerGroupStage
import com.netflix.spinnaker.orca.front50.pipeline.PipelineStage
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy
import com.netflix.spinnaker.orca.pipeline.WaitStage
import spock.lang.Specification
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage


class RollingRedBlackStrategySpec extends Specification {
  def dynamicConfigService = Mock(DynamicConfigService)
  def disableServerGroupStage = new DisableServerGroupStage(dynamicConfigService)
  def scaleDownClusterStage = new ScaleDownClusterStage(dynamicConfigService)
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
            capacity                     : fallbackCapacity,
            targetHealthyDeployPercentage: 90,
            targetPercentages            : [50]
          ]
        }

        stage {
          refId = "2-deploy3"
          parent
          context = [
            refId                        : "stage",
            account                      : "testAccount",
            application                  : "unit",
            stack                        : "tests",
            moniker                      : moniker,
            cloudProvider                : "aws",
            region                       : "north",
            capacity                     : fallbackCapacity,
            targetHealthyDeployPercentage: 90,
            targetPercentages            : [50, 100],
            pipelineBeforeCleanup        : [
              application: "serverlabmvulfson",
              pipelineId: "d054a10b-79fd-498b-8b0d-52b339e5643e"
            ]
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
    def beforeStages = strat.composeBeforeStages(stage)
    def afterStages = strat.composeAfterStages(stage)

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
    beforeStages = strat.composeBeforeStages(stage)
    afterStages = strat.composeAfterStages(stage)

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

    when: 'planning with cleanup pipeline'
    stage = pipeline.stageByRef("2-deploy3")
    afterStages = strat.composeAfterStages(stage)

    then:
    noExceptionThrown()
    afterStages.size() == 5
    afterStages.first().type == determineTargetServerGroupStage.type
    afterStages[2].type == pipelineStage.type
    afterStages[2].context.pipelineApplication == stage.context.pipelineBeforeCleanup.application
    afterStages[2].context.pipeline == stage.context.pipelineBeforeCleanup.pipelineId
    afterStages[4].type == pipelineStage.type
    afterStages[4].context.pipelineApplication == stage.context.pipelineBeforeCleanup.application
    afterStages[4].context.pipeline == stage.context.pipelineBeforeCleanup.pipelineId
  }

  def "should correctly determine source during pin/unpin"() {
    given:
    Moniker moniker = new Moniker(app: "unit", stack: "tests")
    def fallbackCapacity = [min: 1, desired: 2, max: 3]

    def pipeline = pipeline {
      application = "orca"
      stage {
        refId = "1-create"
        type = "createServerGroup"
        context = [
          refId: "stage_createASG",
          source: [
            serverGroupName: "test-dev-v005",
            asgName: "test-dev-v005",
            region: "us-east-1",
            useSourceCapacity: false,
            account: "test"
          ]
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
            capacity                     : fallbackCapacity,
            targetHealthyDeployPercentage: 90,
            scaleDown                    : true,
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

    when: 'using scale down old ASG'
    def stage = pipeline.stageByRef("2-deploy1")
    def beforeStages = strat.composeBeforeStages(stage)
    def afterStages = strat.composeAfterStages(stage)

    then: 'should plan pin but not unpin'
    beforeStages.isEmpty()
    afterStages.size() == 5
    afterStages[1].type == PinServerGroupStage.TYPE
    afterStages[1].context.serverGroupName == "test-dev-v005"
    afterStages[4].type == scaleDownClusterStage.type

    when: 'without scaling down old ASG'
    stage.context.scaleDown = false
    beforeStages = strat.composeBeforeStages(stage)
    afterStages = strat.composeAfterStages(stage)

    then: 'should plan pin and unpin'
    beforeStages.isEmpty()
    afterStages.size() == 5
    afterStages[1].type == PinServerGroupStage.TYPE
    afterStages[1].context.serverGroupName == "test-dev-v005"
    afterStages[4].type == PinServerGroupStage.TYPE
    afterStages[4].context.serverGroupName == "test-dev-v005"
  }

  def "should compose unpin onFailure"() {
    given:
    Moniker moniker = new Moniker(app: "unit", stack: "tests")
    def fallbackCapacity = [min: 1, desired: 2, max: 3]

    def pipeline = pipeline {
      application = "orca"
      stage {
        refId = "1-create"
        type = "createServerGroup"
        context = [
          refId: "stage_createASG",
          source: [
            serverGroupName: "test-dev-v005",
            asgName: "test-dev-v005",
            region: "us-east-1",
            useSourceCapacity: false,
            account: "test"
          ]
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
            capacity                     : fallbackCapacity,
            targetHealthyDeployPercentage: 90,
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

    when:
    def stage = pipeline.stageByRef("2-deploy1")
    def failureStages = strat.composeOnFailureStages(stage)

    then: 'should plan pin but not unpin'
    failureStages.size() == 1
    failureStages[0].type == PinServerGroupStage.TYPE
  }
}
