/*
 * Copyright 2026 Spinnaker contributors.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.ecs

import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.ecs.EcsNativeContinueServiceDeploymentTask
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.ecs.EcsNativeRollbackServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.ecs.EcsNativeStopServiceDeploymentTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.ecs.WaitForEcsNativeServiceDeploymentTask
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification

/**
 * Pins the task graphs of the explicit ecs-native Continue and Stop service-deployment stages, and
 * that both resolve the exact deployment ARN published by the deploy stage they reference.
 */
class EcsNativeServiceDeploymentStagesSpec extends Specification {

  private static List<TaskNode.TaskDefinition> tasksOf(TaskNode.TaskGraph graph) {
    graph.collect { it as TaskNode.TaskDefinition }
  }

  private static StageExecutionImpl deployStage(PipelineExecutionImpl pipeline, String arn) {
    def deploy = new StageExecutionImpl(pipeline, 'createServerGroup', 'deploy', [cloudProvider: 'ecs-native'])
    deploy.refId = 'deploy-ref'
    deploy.outputs.ecsNativeExpectedServiceDeploymentArn = arn
    pipeline.stages << deploy
    deploy
  }

  private static StageExecutionImpl lifecycleStage(PipelineExecutionImpl pipeline, String type, String refId) {
    def stage = new StageExecutionImpl(pipeline, type, type, [
      cloudProvider       : 'ecs-native',
      account             : 'test',
      region              : 'us-west-2',
      serverGroupName     : 'myapp-main',
      deploymentStageRefId: 'deploy-ref',
    ])
    stage.refId = refId
    stage.requisiteStageRefIds = ['deploy-ref']
    pipeline.stages << stage
    stage
  }

  def 'continue stage runs the operation, monitors it, then waits for the exact deployment to succeed'() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline('orca')
    def stage = lifecycleStage(pipeline, 'ecsNativeContinueServiceDeployment', 'continue-ref')

    when:
    def tasks = tasksOf(new EcsNativeContinueServiceDeploymentStage().buildTaskGraph(stage))

    then:
    tasks*.name == ['continueServiceDeployment', 'monitorServiceDeployment', WaitForEcsNativeServiceDeploymentTask.TASK_NAME]
    tasks*.implementingClass == [
      EcsNativeContinueServiceDeploymentTask,
      MonitorKatoTask,
      WaitForEcsNativeServiceDeploymentTask,
    ]
  }

  def 'stop stage runs the operation, monitors it, then waits for the exact deployment to stop'() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline('orca')
    def stage = lifecycleStage(pipeline, 'ecsNativeStopServiceDeployment', 'stop-ref')

    when:
    def tasks = tasksOf(new EcsNativeStopServiceDeploymentStage().buildTaskGraph(stage))

    then:
    tasks*.name == ['stopServiceDeployment', 'monitorServiceDeployment', WaitForEcsNativeServiceDeploymentTask.TASK_NAME]
    tasks*.implementingClass == [
      EcsNativeStopServiceDeploymentTask,
      MonitorKatoTask,
      WaitForEcsNativeServiceDeploymentTask,
    ]
  }

  def 'stage types match the names Deck registers and pipelines use'() {
    expect:
    StageDefinitionBuilder.getType(EcsNativeContinueServiceDeploymentStage) == 'ecsNativeContinueServiceDeployment'
    StageDefinitionBuilder.getType(EcsNativeStopServiceDeploymentStage) == 'ecsNativeStopServiceDeployment'
    StageDefinitionBuilder.getType(EcsNativeRollbackServerGroupStage) == 'ecsNativeRollbackServerGroup'
    EcsNativeRollbackServerGroupStage.PIPELINE_CONFIG_TYPE == 'ecsNativeRollbackServerGroup'
  }

  def 'rollback stage updates in place, monitors, refreshes the cache, then waits for the deployment'() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline('orca')
    def stage = lifecycleStage(pipeline, 'ecsNativeRollbackServerGroup', 'rollback-ref')

    when:
    def tasks = tasksOf(new EcsNativeRollbackServerGroupStage().buildTaskGraph(stage))

    then:
    tasks*.name == ['rollbackServerGroup', 'monitorServerGroup', 'forceCacheRefresh', WaitForEcsNativeServiceDeploymentTask.TASK_NAME]
    tasks*.implementingClass == [
      EcsNativeRollbackServerGroupTask,
      MonitorKatoTask,
      ServerGroupCacheForceRefreshTask,
      WaitForEcsNativeServiceDeploymentTask,
    ]
  }

  def 'stop stage asks the wait task for STOPPED, and continue stage does not'() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline('orca')
    def stopStage = lifecycleStage(pipeline, 'ecsNativeStopServiceDeployment', 'stop-ref')
    def continueStage = lifecycleStage(pipeline, 'ecsNativeContinueServiceDeployment', 'continue-ref')

    expect:
    // Without this flag the wait would treat STOPPED as a terminal failure of the deployment.
    new EcsNativeStopServiceDeploymentTask().getAdditionalContext(stopStage, [:]) ==
      [ecsNativeWaitForStopped: true, ecsNativeAcceptRollback: true]
    new EcsNativeContinueServiceDeploymentTask().getAdditionalContext(continueStage, [:]) == [:]
  }

  def 'a requested Continue rollback is an accepted outcome'() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline('orca')
    def rejectContinue = lifecycleStage(pipeline, 'ecsNativeContinueServiceDeployment', 'continue-ref')
    rejectContinue.context.lifecycleAction = 'rollback'

    expect:
    new EcsNativeContinueServiceDeploymentTask().getAdditionalContext(rejectContinue, [:]) == [ecsNativeAcceptRollback: true]
  }

  def 'continue and stop both resolve the same ARN from the referenced deploy stage'() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline('orca')
    deployStage(pipeline, 'arn:aws:ecs:us-west-2:123:service-deployment/myapp-main/abc')
    def continueStage = lifecycleStage(pipeline, 'ecsNativeContinueServiceDeployment', 'continue-ref')
    def stopStage = lifecycleStage(pipeline, 'ecsNativeStopServiceDeployment', 'stop-ref')

    expect:
    new EcsNativeContinueServiceDeploymentTask().convert(continueStage).ecsNativeExpectedServiceDeploymentArn ==
      'arn:aws:ecs:us-west-2:123:service-deployment/myapp-main/abc'
    new EcsNativeStopServiceDeploymentTask().convert(stopStage).ecsNativeExpectedServiceDeploymentArn ==
      'arn:aws:ecs:us-west-2:123:service-deployment/myapp-main/abc'
    // The wait task resolves the identical value, so Continue's final wait polls the same deployment.
    WaitForEcsNativeServiceDeploymentTask.resolveExpectedServiceDeploymentArn(continueStage) ==
      WaitForEcsNativeServiceDeploymentTask.resolveExpectedServiceDeploymentArn(stopStage)
  }

  def 'the referenced deploy stage wins over a stale direct ARN, so a re-run cannot target an old deployment'() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline('orca')
    deployStage(pipeline, 'arn:current')
    def stage = lifecycleStage(pipeline, 'ecsNativeContinueServiceDeployment', 'continue-ref')
    stage.context.ecsNativeExpectedServiceDeploymentArn = 'arn:stale-from-an-earlier-run'

    expect:
    new EcsNativeContinueServiceDeploymentTask().convert(stage).ecsNativeExpectedServiceDeploymentArn == 'arn:current'
  }

  def 'a Continue after a Manual Judgment resolves the deploy without an explicit reference'() {
    given: 'deploy -> manualJudgment -> Continue, wired only through requisiteStageRefIds'
    def pipeline = PipelineExecutionImpl.newPipeline('orca')
    deployStage(pipeline, 'arn:aws:ecs:us-west-2:123:service-deployment/myapp-main/abc')
    def judgment = new StageExecutionImpl(pipeline, 'manualJudgment', 'Approve', [:])
    judgment.refId = 'judge-ref'
    judgment.requisiteStageRefIds = ['deploy-ref']
    pipeline.stages << judgment
    def stage = new StageExecutionImpl(pipeline, 'ecsNativeContinueServiceDeployment', 'Continue', [
      cloudProvider: 'ecs-native', account: 'test', region: 'us-west-2', serverGroupName: 'myapp-main',
    ])
    stage.refId = 'continue-ref'
    stage.requisiteStageRefIds = ['judge-ref']
    pipeline.stages << stage

    expect:
    new EcsNativeContinueServiceDeploymentTask().convert(stage).ecsNativeExpectedServiceDeploymentArn ==
      'arn:aws:ecs:us-west-2:123:service-deployment/myapp-main/abc'
  }

  def 'a lifecycle stage with neither a deploy-stage reference nor a direct ARN fails closed'() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline('orca')
    def stage = new StageExecutionImpl(pipeline, 'ecsNativeStopServiceDeployment', 'stop', [
      cloudProvider: 'ecs-native', account: 'test', region: 'us-west-2', serverGroupName: 'myapp-main',
    ])
    pipeline.stages << stage

    when:
    new EcsNativeStopServiceDeploymentTask().convert(stage)

    then:
    thrown(IllegalArgumentException)
  }
}
