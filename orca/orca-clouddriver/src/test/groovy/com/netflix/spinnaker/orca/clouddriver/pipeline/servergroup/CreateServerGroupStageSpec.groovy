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

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.FeaturesService
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.DeployStagePreProcessor
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.TaskExecutionImpl
import org.springframework.mock.env.MockEnvironment

import java.util.concurrent.TimeUnit
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.RollbackClusterStage
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilderImpl
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class CreateServerGroupStageSpec extends Specification {
  def deployStagePreProcessor = Mock(DeployStagePreProcessor)
  def trafficGuard = Stub(TrafficGuard)
  def env = new MockEnvironment()

  @Subject
  CreateServerGroupStage createServerGroupStage

  def setup() {
    def dynamicConfigService = Mock(DynamicConfigService)
    createServerGroupStage = new CreateServerGroupStage(Mock(FeaturesService),
        new RollbackClusterStage(),
        new DestroyServerGroupStage(dynamicConfigService),
        dynamicConfigService)
    createServerGroupStage.deployStagePreProcessors = [ deployStagePreProcessor ]
  }

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
        new TaskExecutionImpl(status: failedTask ? ExecutionStatus.TERMINAL : ExecutionStatus.SUCCEEDED)
      ]
    }

    if (shouldRollbackOnFailure) {
      stage.context.rollback = [
        onFailure: true
      ]
    }

    when:
    def graph = StageGraphBuilderImpl.afterStages(stage)
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
    true                    | "rollingredblack" | ["us-west-1": ["myapplication-stack-v001"]] | false      || [expectedRollbackContext([enableAndDisableOnly: true, onlyEnabledServerGroups: true]), [source: "parent"]]
    true                    | "redblack"        | ["us-west-1": ["myapplication-stack-v001"]] | true       || [expectedRollbackContext([disableOnly: true, onlyEnabledServerGroups:true]), [source: "parent"]]
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
    def graph = StageGraphBuilderImpl.afterStages(stage)
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
    true                    | "rollingredblack" | ["us-west-1": ["myapplication-stack-v001"]] || [expectedRollbackContext([enableAndDisableOnly: true, onlyEnabledServerGroups: true]), expectedDestroyContext()]
  }

  @Unroll
  def "appends waitForEcsNativeServiceDeployment only for the ecs-native cloud provider (cloudProvider=#cloudProvider)"() {
    given:
    def featuresService = Mock(FeaturesService) {
      areEntityTagsAvailable() >> false
    }
    // A plain Mock returns false for isEnabled(...) by default, which disables the optional
    // force-cache-refresh tasks and keeps the task list deterministic for this assertion.
    def dynamicConfigService = Mock(DynamicConfigService)
    def stageForProvider = new CreateServerGroupStage(
        featuresService,
        new RollbackClusterStage(),
        new DestroyServerGroupStage(dynamicConfigService),
        dynamicConfigService)

    def stage = stage {
      context = [
        "application"  : "myapplication",
        "account"      : "test",
        "cloudProvider": cloudProvider,
      ]
    }

    when:
    def taskNames = stageForProvider.basicTasks(stage)*.name

    then:
    taskNames.contains("waitForUpInstances")
    taskNames.contains("waitForEcsNativeServiceDeployment") == expectWait

    where:
    cloudProvider | expectWait
    "ecs-native"  | true
    "ecs"         | false
    "aws"         | false
    null          | false
  }

  def "for ecs-native, waitForEcsNativeServiceDeployment runs before waitForUpInstances so the trailing forceCacheRefresh captures the settled rollout state"() {
    given:
    def featuresService = Mock(FeaturesService) {
      areEntityTagsAvailable() >> false
    }
    // Enable force-cache-refresh so both refresh tasks are present and we can assert the ecs-native
    // wait lands between them.
    def dynamicConfigService = Mock(DynamicConfigService) {
      isEnabled("stages.create-server-group-stage.force-cache-refresh.enabled", true) >> true
    }
    def stageForProvider = new CreateServerGroupStage(
        featuresService,
        new RollbackClusterStage(),
        new DestroyServerGroupStage(dynamicConfigService),
        dynamicConfigService)

    def stage = stage {
      context = [
        "application"  : "myapplication",
        "account"      : "test",
        "cloudProvider": "ecs-native",
      ]
    }

    when:
    def taskNames = stageForProvider.basicTasks(stage)*.name

    then:
    // waitForEcsNativeServiceDeployment must precede waitForUpInstances...
    taskNames.indexOf("waitForEcsNativeServiceDeployment") < taskNames.indexOf("waitForUpInstances")
    // ...and there must be a forceCacheRefresh after it, so the settled COMPLETED state is cached.
    taskNames.lastIndexOf("forceCacheRefresh") > taskNames.indexOf("waitForEcsNativeServiceDeployment")
    // Two refresh tasks bracket the ecs-native wait + waitForUpInstances.
    taskNames.count { it == "forceCacheRefresh" } == 2
    taskNames.indexOf("forceCacheRefresh") < taskNames.indexOf("waitForEcsNativeServiceDeployment")
  }

  @Unroll
  def "sets ecsNativeWaitForLifecycleGate only for ecs-native BLUE_GREEN with a pause stage (cloudProvider=#cloudProvider, deploymentStrategy=#deploymentStrategy, pauseStage=#pauseStage)"() {
    given:
    // basicTasks() sets the flag on the stage context as a side effect of building the graph, so the
    // flag has to be read back from the context. If it is missing for blue/green, the wait task would
    // poll for final SUCCESSFUL and never reach the Continue stage.
    def dynamicConfigService = Mock(DynamicConfigService)
    def stageForProvider = new CreateServerGroupStage(
        Mock(FeaturesService) { areEntityTagsAvailable() >> false },
        new RollbackClusterStage(),
        new DestroyServerGroupStage(dynamicConfigService),
        dynamicConfigService)

    def stage = stage {
      context = [
        "application"       : "myapplication",
        "account"           : "test",
        "cloudProvider"     : cloudProvider,
        "deploymentStrategy": deploymentStrategy,
        "lifecyclePauseStage": pauseStage,
      ]
    }

    when:
    stageForProvider.basicTasks(stage)

    then:
    stage.context.ecsNativeWaitForLifecycleGate == expectedGate

    where:
    cloudProvider | deploymentStrategy | pauseStage                || expectedGate
    "ecs-native"  | "BLUE_GREEN"       | "POST_TEST_TRAFFIC_SHIFT" || true
    "ecs-native"  | "blue_green"       | "POST_TEST_TRAFFIC_SHIFT" || true
    "ecs-native"  | "BLUE_GREEN"       | null                      || null
    "ecs-native"  | "ROLLING"          | "POST_TEST_TRAFFIC_SHIFT" || null
    "ecs-native"  | null               | null                      || null
    "ecs"         | "BLUE_GREEN"       | "POST_TEST_TRAFFIC_SHIFT" || null
    "aws"         | "BLUE_GREEN"       | "POST_TEST_TRAFFIC_SHIFT" || null
  }

  @Unroll
  def "ecs-native rejects a non-None Spinnaker deployment strategy (strategy=#strategy)"() {
    given:
    def featuresService = Mock(FeaturesService) {
      areEntityTagsAvailable() >> false
    }
    def dynamicConfigService = Mock(DynamicConfigService)
    def stageForProvider = new CreateServerGroupStage(
        featuresService,
        new RollbackClusterStage(),
        new DestroyServerGroupStage(dynamicConfigService),
        dynamicConfigService)

    def stage = stage {
      context = [
        "application"  : "myapplication",
        "account"      : "test",
        "cloudProvider": "ecs-native",
        "strategy"     : strategy,
      ]
    }

    when:
    stageForProvider.basicTasks(stage)

    then:
    def e = thrown(IllegalStateException)
    e.message.contains("ecs-native")
    e.message.contains("None")

    where:
    strategy << ["redblack", "rollingredblack", "highlander"]
  }

  @Unroll
  def "ecs-native accepts the None strategy (strategy=#strategy)"() {
    given:
    def featuresService = Mock(FeaturesService) {
      areEntityTagsAvailable() >> false
    }
    def dynamicConfigService = Mock(DynamicConfigService)
    def stageForProvider = new CreateServerGroupStage(
        featuresService,
        new RollbackClusterStage(),
        new DestroyServerGroupStage(dynamicConfigService),
        dynamicConfigService)

    def stage = stage {
      context = [
        "application"  : "myapplication",
        "account"      : "test",
        "cloudProvider": "ecs-native",
        "strategy"     : strategy,
      ]
    }

    when:
    def taskNames = stageForProvider.basicTasks(stage)*.name

    then:
    notThrown(IllegalStateException)
    taskNames.contains("waitForEcsNativeServiceDeployment")

    where:
    strategy << [null, "", "none"]
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
