/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies

import com.netflix.spinnaker.config.DeploymentMonitorDefinition
import com.netflix.spinnaker.config.DeploymentMonitorServiceProvider
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.RollbackClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.monitoreddeploy.EvaluateDeploymentHealthStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.monitoreddeploy.NotifyDeployCompletedStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.monitoreddeploy.NotifyDeployStartingStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.DestroyServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.DisableServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.PinServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.DetermineTargetServerGroupStage
import spock.lang.Specification

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class MonitoredDeployStrategySpec extends Specification {
  def "should fixup context while planning beforeStages"() {
    given:
    def pipeline = configureBasicPipeline()
    def strategy = createStrategy()
    def stage = pipeline.stageByRef("2-deploy1")

    when:
    stage.context.rollback = [
      onFailure: true
    ]
    def beforeStages = strategy.composeBeforeStages(stage)

    then:
    noExceptionThrown()
    beforeStages.size() == 0
    stage.context.capacity == [
      min    : 0,
      desired: 0,
      max    : 0,
    ]
    stage.context.savedCapacity == [
      min    : 1,
      desired: 2,
      max    : 3,
    ]
    stage.context.rollback == null

    when: 'the invalid deployment monitor specified'
    stage.context.deploymentMonitor.id = "invalid"
    strategy.composeBeforeStages(stage)

    then: 'we throw'
    thrown(NotFoundException)
  }

  def "composes happy path flow"() {
    given:
    def pipeline = configureBasicPipeline()
    def strategy = createStrategy()
    def stage = pipeline.stageByRef("2-deploy1")
    def sourceServerGroupName = pipeline.stageByRef("1-create").context.source.serverGroupName

    when: 'no steps are provided'
    def afterStages = strategy.composeAfterStages(stage)

    then: 'goes straight to 100%'
    afterStages.size() == 8
    afterStages[0].type == DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE
    afterStages[1].type == PinServerGroupStage.TYPE
    afterStages[2].type == NotifyDeployStartingStage.PIPELINE_CONFIG_TYPE
    afterStages[3].type == ResizeServerGroupStage.TYPE
    afterStages[3].context.scalePct == 100
    afterStages[4].type == DisableServerGroupStage.PIPELINE_CONFIG_TYPE
    afterStages[4].context.desiredPercentage == 100
    afterStages[5].type == EvaluateDeploymentHealthStage.PIPELINE_CONFIG_TYPE
    afterStages[6].type == PinServerGroupStage.TYPE
    afterStages[7].type == NotifyDeployCompletedStage.PIPELINE_CONFIG_TYPE

    when: 'only 50% step is provided'
    stage.context.deploySteps = [50]
    afterStages = strategy.composeAfterStages(stage)

    then: 'adds 100% step'
    afterStages.size() == 11
    afterStages[3].type == ResizeServerGroupStage.TYPE
    afterStages[3].context.scalePct == 50
    afterStages[4].type == DisableServerGroupStage.PIPELINE_CONFIG_TYPE
    afterStages[4].context.desiredPercentage == 50
    afterStages[6].type == ResizeServerGroupStage.TYPE
    afterStages[6].context.scalePct == 100
    afterStages[7].type == DisableServerGroupStage.PIPELINE_CONFIG_TYPE
    afterStages[7].context.desiredPercentage == 100

    when: 'no deployment monitor specified'
    stage.context.remove("deploymentMonitor")
    afterStages = strategy.composeAfterStages(stage)

    then:
    noExceptionThrown()
    afterStages.size() == 7
  }

  def "composes correct failure flow when no deployment monitor specified"() {
    given:
    def pipeline = configureBasicPipeline()
    def strategy = createStrategy()
    def stage = pipeline.stageByRef("2-deploy1")
    stage.context.remove("deploymentMonitor")
    def sourceServerGroupName = pipeline.stageByRef("1-create").context.source.serverGroupName

    when:
    def failureStages = strategy.composeOnFailureStages(stage)

    then: 'should unpin source'
    failureStages.size() == 1
    failureStages[0].type == PinServerGroupStage.TYPE
    failureStages[0].context.serverGroupName == sourceServerGroupName
    failureStages[0].name == "Unpin ${sourceServerGroupName}"
  }

  def "composes correct failure flow when no rollback requested"() {
    given:
    def pipeline = configureBasicPipeline()
    def strategy = createStrategy()

    when: 'rollback not explicitly specified'
    def stage = pipeline.stageByRef("2-deploy1")
    def failureStages = strategy.composeOnFailureStages(stage)

    then: 'should unpin source and notify monitor of completion'
    failureStages.size() == 2
    failureStages[0].type == PinServerGroupStage.TYPE
    failureStages[1].type == NotifyDeployCompletedStage.PIPELINE_CONFIG_TYPE

    when: 'no rollback is explicitly specified'
    stage.context.failureActions = [
      destroyInstances: false,
      rollback: "None"
    ]
    failureStages = strategy.composeOnFailureStages(stage)

    then: 'should unpin source and notify monitor of completion'
    failureStages.size() == 2
    failureStages[0].type == PinServerGroupStage.TYPE
    failureStages[1].type == NotifyDeployCompletedStage.PIPELINE_CONFIG_TYPE
  }

  def "composes correct failure flow when automatic rollback is requested"() {
    given:
    def pipeline = configureBasicPipeline()
    def strategy = createStrategy()
    def deployedServerGroupMoniker = "test-dev"
    def deployedServerGroupName = "${deployedServerGroupMoniker}-v006".toString()

    when: 'no target server group is created (yet)'
    def stage = pipeline.stageByRef("2-deploy1")
    stage.context.failureActions = [
      destroyInstances: false,
      rollback: "Automatic"
    ]
    def failureStages = strategy.composeOnFailureStages(stage)

    then: 'should unpin source and notify monitor of completion'
    failureStages.size() == 2
    failureStages[0].type == PinServerGroupStage.TYPE
    failureStages[1].type == NotifyDeployCompletedStage.PIPELINE_CONFIG_TYPE

    when: 'a target server group has been created'
    stage.context."deploy.server.groups" = [
      "us-east-1": [deployedServerGroupName]
    ]
    failureStages = strategy.composeOnFailureStages(stage)

    then: 'should rollback, unpin source, and then notify monitor of completion'
    failureStages.size() == 3
    failureStages[0].type == RollbackClusterStage.PIPELINE_CONFIG_TYPE
    failureStages[0].context.serverGroup == deployedServerGroupName
    failureStages[0].name == "Rollback ${deployedServerGroupMoniker}"
    failureStages[1].type == PinServerGroupStage.TYPE
    failureStages[2].type == NotifyDeployCompletedStage.PIPELINE_CONFIG_TYPE

    when: 'the user also requested to destroy the new server group'
    stage.context.failureActions.destroyInstances = true
    failureStages = strategy.composeOnFailureStages(stage)

    then: 'should rollback, unpin source, and then notify monitor of completion'
    failureStages.size() == 4
    failureStages[0].type == RollbackClusterStage.PIPELINE_CONFIG_TYPE
    failureStages[1].type == DestroyServerGroupStage.PIPELINE_CONFIG_TYPE
    failureStages[1].name == "Destroy ${deployedServerGroupName} due to rollback"
    failureStages[1].context.serverGroupName == deployedServerGroupName
    failureStages[2].type == PinServerGroupStage.TYPE
    failureStages[3].type == NotifyDeployCompletedStage.PIPELINE_CONFIG_TYPE
  }

  private def configureBasicPipeline() {
    Moniker moniker = new Moniker(app: "unit", stack: "tests")
    def fallbackCapacity = [min: 1, desired: 2, max: 3]

    def pipeline = pipeline {
      application = "testapp"
      stage {
        refId = "1-create"
        type = "createServerGroup"
        context = [
          refId : "stage_createASG",
          source: [
            serverGroupName  : "test-dev-v005",
            asgName          : "test-dev-v005",
            region           : "us-east-1",
            useSourceCapacity: false,
            account          : "test"
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
            deploymentMonitor: [
              id: "simpletestmonitor"
            ]
          ]
        }
      }
    }

    return pipeline
  }

  private def createStrategy() {
    def serviceProviderStub = Stub(DeploymentMonitorServiceProvider) {
      getDefinitionById(_ as String) >> { id ->
        if (id[0] == "simpletestmonitor") {
          def deploymentMonitor = new DeploymentMonitorDefinition()
          return deploymentMonitor
        }

        throw new NotFoundException()
      }
    }

    def strategy = new MonitoredDeployStrategy(
      deploymentMonitorServiceProvider: serviceProviderStub
    )

    return strategy
  }
}
