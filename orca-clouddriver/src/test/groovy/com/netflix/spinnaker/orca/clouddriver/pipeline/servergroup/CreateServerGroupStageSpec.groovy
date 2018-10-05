/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup

import com.netflix.spinnaker.kork.dynamicconfig.SpringDynamicConfigService
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.DeployStagePreProcessor
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard
import com.netflix.spinnaker.orca.locks.LockingConfigurationProperties
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Task
import org.springframework.mock.env.MockEnvironment

import java.util.concurrent.TimeUnit
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.RollbackClusterStage
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class CreateServerGroupStageSpec extends Specification {
  def deployStagePreProcessor = Mock(DeployStagePreProcessor)
  def trafficGuard = Stub(TrafficGuard)
  def env = new MockEnvironment()
  def lockingConfig = new LockingConfigurationProperties(new SpringDynamicConfigService(environment: env))

  @Subject
  def createServerGroupStage = new CreateServerGroupStage(
    rollbackClusterStage: new RollbackClusterStage(trafficGuard, lockingConfig),
    destroyServerGroupStage: new DestroyServerGroupStage(),
    deployStagePreProcessors: [ deployStagePreProcessor ]
  )

  @Unroll
  def "should build RollbackStage when 'rollbackOnFailure' is enabled"() {
    given:
    def stage = stage {
      context = [
        "deploy.server.groups": deployServerGroups,
        "application"         : "myapplication",
        "account"             : "test",
        "cloudProvider"       : "aws",
        "strategy"            : strategy
      ]
      tasks = [
        new Task(status: failedTask ? ExecutionStatus.TERMINAL : ExecutionStatus.SUCCEEDED)
      ]
    }

    if (shouldRollbackOnFailure) {
      stage.context.rollback = [
        onFailure: true
      ]
    }

    when:
    def graph = StageGraphBuilder.afterStages(stage)
    createServerGroupStage.onFailureStages(stage, graph)
    def onFailureStageContexts = graph.build()*.getContext()

    then:
    onFailureStageContexts == expectedOnFailureStageContexts

    1 * deployStagePreProcessor.supports(_) >> { return true }
    1 * deployStagePreProcessor.onFailureStageDefinitions(_) >> {
      def stageDefinition = new DeployStagePreProcessor.StageDefinition()
      stageDefinition.stageDefinitionBuilder = Mock(StageDefinitionBuilder)
      stageDefinition.context = [source: "parent"]
      return [stageDefinition]
    }

    where:
    shouldRollbackOnFailure | strategy          | deployServerGroups                          | failedTask || expectedOnFailureStageContexts
    false                   | "rollingredblack" | null                                        | false      || [[source: "parent"]]
    true                    | "rollingredblack" | null                                        | false      || [[source: "parent"]]
    false                   | "rollingredblack" | ["us-west-1": ["myapplication-stack-v001"]] | false      || [[source: "parent"]]
    true                    | "redblack"        | ["us-west-1": ["myapplication-stack-v001"]] | false      || [[source: "parent"]]      // only rollback if task has failed
    true                    | "highlander"      | ["us-west-1": ["myapplication-stack-v001"]] | false      || [[source: "parent"]]      // highlander is not supported
    true                    | "rollingredblack" | ["us-west-1": ["myapplication-stack-v001"]] | false      || [expectedRollbackContext([enableAndDisableOnly: true]), [source: "parent"]]
    true                    | "redblack"        | ["us-west-1": ["myapplication-stack-v001"]] | true       || [expectedRollbackContext([disableOnly: true]), [source: "parent"]]
  }

  def "should build DestroyStage when 'rollbackDestroyLatest' is enabled"() {
    given:
    def stage = stage {
      context = [
        "deploy.server.groups": deployServerGroups,
        "application"         : "myapplication",
        "account"             : "test",
        "cloudProvider"       : "aws",
        "strategy"            : strategy
      ]
    }

    if (shouldDestroyOnFailure) {
      stage.context.rollback = [
        onFailure: true,
        destroyLatest: true
      ]
    }

    when:
    def graph = StageGraphBuilder.afterStages(stage)
    createServerGroupStage.onFailureStages(stage, graph)
    def onFailureStageContexts = graph.build()*.getContext()

    then:
    onFailureStageContexts == expectedOnFailureStageContexts

    where:
    shouldDestroyOnFailure  | strategy          | deployServerGroups                          || expectedOnFailureStageContexts
    true                    | null              | null                                        || []
    false                   | null              | ["us-west-1": ["myapplication-stack-v001"]] || []
    true                    | null              | ["us-west-1": ["myapplication-stack-v001"]] || [expectedDestroyContext()]
    false                   | "rollingredblack" | ["us-west-1": ["myapplication-stack-v001"]] || []
    true                    | "highlander"      | ["us-west-1": ["myapplication-stack-v001"]] || [expectedDestroyContext()] // highlander does not support rollback
    true                    | "rollingredblack" | ["us-west-1": ["myapplication-stack-v001"]] || [expectedRollbackContext([enableAndDisableOnly: true]), expectedDestroyContext()]
  }

  Map expectedRollbackContext(Map<String, Object> additionalRollbackContext) {
    return [
      regions                  : ["us-west-1"],
      serverGroup              : "myapplication-stack-v001",
      credentials              : "test",
      cloudProvider            : "aws",
      stageTimeoutMs           : TimeUnit.MINUTES.toMillis(30),
      additionalRollbackContext: additionalRollbackContext
    ]
  }

  Map expectedDestroyContext() {
    return [
      cloudProvider    : "aws",
      cloudProviderType: "aws",
      cluster          : "myapplication-stack",
      credentials      : "test",
      region           : "us-west-1",
      serverGroupName  : "myapplication-stack-v001",
      stageTimeoutMs   : TimeUnit.MINUTES.toMillis(5)
    ]
  }
}
