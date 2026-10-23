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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.ecs

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.EcsNativeService
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.EcsServiceDeploymentStatus
import com.netflix.spinnaker.orca.clouddriver.model.Task
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTaskSpec
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.time.Clock
import java.util.concurrent.TimeUnit

class WaitForEcsNativeServiceDeploymentTaskSpec extends Specification {

  def ecsNativeService = Mock(EcsNativeService)

  @Subject
  def task = new WaitForEcsNativeServiceDeploymentTask(ecsNativeService: ecsNativeService)

  private StageExecutionImpl stageWithContext(Map context, String deploymentArn = null, Map deployContext = [:]) {
    def pipeline = PipelineExecutionImpl.newPipeline('orca')
    def stage = new StageExecutionImpl(pipeline, 'test', 'test', context)
    if (deploymentArn) {
      def deploy = new StageExecutionImpl(pipeline, 'createServerGroup', 'deploy', deployContext)
      deploy.refId = 'deploy-ref'
      deploy.outputs.ecsNativeExpectedServiceDeploymentArn = deploymentArn
      pipeline.stages << deploy
      stage.requisiteStageRefIds = ['deploy-ref']
    }
    pipeline.stages << stage
    return stage
  }

  private static EcsServiceDeploymentStatus inProgress() {
    new EcsServiceDeploymentStatus(serviceDeploymentArn: 'service-deployment-1', status: 'IN_PROGRESS')
  }

  private Map directContext() {
    [account: 'test', region: 'us-west-2', serverGroupName: 'myapp', ecsNativeExpectedServiceDeploymentArn: 'service-deployment-1']
  }

  def 'a 404 below the retry cap keeps running and increments the not-found counter'() {
    given:
    def stage = stageWithContext(directContext() + [(WaitForEcsNativeServiceDeploymentTask.NOT_FOUND_RETRY_COUNT): previous])

    when:
    def result = task.execute(stage)

    then:
    1 * ecsNativeService.getServiceDeploymentStatus('test', 'us-west-2', 'myapp', 'service-deployment-1') >> {
      throw MonitorKatoTaskSpec.makeSpinnakerHttpException(404)
    }
    result.status == ExecutionStatus.RUNNING
    result.context[WaitForEcsNativeServiceDeploymentTask.NOT_FOUND_RETRY_COUNT] == previous + 1

    where:
    previous << [0, 1, WaitForEcsNativeServiceDeploymentTask.MAX_NOT_FOUND_RETRIES - 2]
  }

  def 'the first 404 starts the not-found counter at 1'() {
    given:
    def stage = stageWithContext(directContext())

    when:
    def result = task.execute(stage)

    then:
    1 * ecsNativeService.getServiceDeploymentStatus(*_) >> { throw MonitorKatoTaskSpec.makeSpinnakerHttpException(404) }
    result.status == ExecutionStatus.RUNNING
    result.context[WaitForEcsNativeServiceDeploymentTask.NOT_FOUND_RETRY_COUNT] == 1
  }

  def 'a 404 that reaches the retry cap fails the task'() {
    given:
    def stage = stageWithContext(directContext() + [
      (WaitForEcsNativeServiceDeploymentTask.NOT_FOUND_RETRY_COUNT): WaitForEcsNativeServiceDeploymentTask.MAX_NOT_FOUND_RETRIES - 1])

    when:
    task.execute(stage)

    then:
    1 * ecsNativeService.getServiceDeploymentStatus(*_) >> { throw MonitorKatoTaskSpec.makeSpinnakerHttpException(404) }
    def e = thrown(SpinnakerHttpException)
    e.responseCode == 404
  }

  def 'a successful response resets the not-found counter'() {
    given:
    def stage = stageWithContext(directContext() + [(WaitForEcsNativeServiceDeploymentTask.NOT_FOUND_RETRY_COUNT): 12])

    when:
    def result = task.execute(stage)

    then:
    1 * ecsNativeService.getServiceDeploymentStatus(*_) >> Calls.response(inProgress())
    result.status == ExecutionStatus.RUNNING
    result.context[WaitForEcsNativeServiceDeploymentTask.NOT_FOUND_RETRY_COUNT] == 0
  }

  def 'non-404 errors are rethrown without retry accounting'() {
    given:
    def stage = stageWithContext(directContext())

    when:
    task.execute(stage)

    then:
    1 * ecsNativeService.getServiceDeploymentStatus(*_) >> { throw MonitorKatoTaskSpec.makeSpinnakerHttpException(409) }
    def e = thrown(SpinnakerHttpException)
    e.responseCode == 409
  }

  private static final long HOUR = TimeUnit.HOURS.toMillis(1)
  private static final long MINUTE = TimeUnit.MINUTES.toMillis(1)

  @Unroll
  def 'dynamic timeout for a stage carrying its own settings #settings is #expectedMinutes minutes over the base hour'() {
    given:
    def stage = stageWithContext(settings)

    expect:
    task.getDynamicTimeout(stage) == HOUR + expectedMinutes * MINUTE

    where:
    settings                                                                                               || expectedMinutes
    [:]                                                                                                    || 0
    [bakeTimeInMinutes: 30]                                                                                || 30
    [bakeTimeInMinutes: '15']                                                                              || 15
    [bakeTimeInMinutes: -5]                                                                                || 0
    [bakeTimeInMinutes: 'not-a-number']                                                                    || 0
    [bakeTimeInMinutes: 1440, lifecyclePauseStage: 'POST_TEST_TRAFFIC_SHIFT', lifecyclePauseTimeoutMinutes: 120] || 1560
    // A deploy that stops at the lifecycle gate never waits through the pause.
    [bakeTimeInMinutes: 10, lifecyclePauseStage: 'POST_TEST_TRAFFIC_SHIFT', lifecyclePauseTimeoutMinutes: 120,
     ecsNativeWaitForLifecycleGate: true]                                                                  || 10
    // Without a pause stage the pause timeout is ignored.
    [lifecyclePauseStage: '', lifecyclePauseTimeoutMinutes: 120]                                           || 0
  }

  def 'Continue/Stop derive the dynamic timeout from the referenced deploy stage settings'() {
    given:
    def stage = stageWithContext(
      [account: 'test', region: 'us-west-2', serverGroupName: 'myapp'],
      'deploy-deployment',
      [bakeTimeInMinutes: 20, lifecyclePauseStage: 'POST_TEST_TRAFFIC_SHIFT', lifecyclePauseTimeoutMinutes: 60,
       ecsNativeWaitForLifecycleGate: true])

    expect: "the deploy stage's own gate flag does not apply to the Continue stage's wait"
    task.getDynamicTimeout(stage) == HOUR + 80 * MINUTE
  }

  def 'a stage whose own write started a deployment uses its own settings, not the upstream deploy'() {
    given:
    def stage = stageWithContext([
      'kato.tasks': [katoTask('1', [ecsNativeExpectedServiceDeploymentArn: 'own-deployment'])]
    ], 'predecessor-deployment', [bakeTimeInMinutes: 300])

    expect:
    task.getDynamicTimeout(stage) == HOUR
  }

  def 'monitorDeploy output merged into the createServerGroup context hands off its own ARN over an upstream deploy'() {
    given: 'a succeeded clouddriver task for this stage whose result carries the new deployment ARN'
    def kato = Mock(KatoService)
    def monitor = new MonitorKatoTask(kato, new NoopRegistry(), Clock.systemUTC(), Mock(DynamicConfigService), new RetrySupport())
    def katoResult = new Task('kato-1', new Task.Status(completed: true, failed: false), [[
      serverGroupNames: ['us-west-2:myapp'],
      deployments     : [[cloudProvider: 'ecs-native', serverGroupName: 'myapp',
                          metadata: [ecsNativeExpectedServiceDeploymentArn: 'own-deployment']]]
    ]], [], [])
    kato.lookupTask('kato-1', false) >> katoResult

    and: 'an upstream ecs-native deploy stage that published a different ARN'
    def stage = stageWithContext([
      cloudProvider: 'ecs-native', account: 'test', 'kato.last.task.id': new TaskId('kato-1')
    ], 'predecessor-deployment')
    stage.type = 'createServerGroup'

    when: 'the monitor task output is merged into the stage context, as RunTaskHandler does'
    def monitorResult = monitor.execute(stage)
    stage.context.putAll(monitorResult.context)

    then:
    monitorResult.status == ExecutionStatus.SUCCEEDED
    monitorResult.context.ecsNativeExpectedServiceDeploymentArn == 'own-deployment'
    WaitForEcsNativeServiceDeploymentTask.resolveExpectedServiceDeploymentArn(stage) == 'own-deployment'

    when: 'the wait task polls'
    def waitResult = task.execute(stage)

    then: 'it pins the deployment this stage started'
    1 * ecsNativeService.getServiceDeploymentStatus('test', 'us-west-2', 'myapp', 'own-deployment') >>
      Calls.response(new EcsServiceDeploymentStatus(serviceDeploymentArn: 'own-deployment', status: 'SUCCESSFUL'))
    0 * ecsNativeService.getServiceDeploymentStatus(_, _, _, 'predecessor-deployment')
    waitResult.status == ExecutionStatus.SUCCEEDED
  }

  @Unroll
  def "maps ECS service-deployment status '#deploymentStatus' to execution status '#expectedStatus'"() {
    given:
    def stage = stageWithContext([
      account: 'test',
      region: 'us-west-2',
      serverGroupName: 'myapp',
      ecsNativeExpectedServiceDeploymentArn: 'service-deployment-1'
    ])
    def status = new EcsServiceDeploymentStatus(
      serviceDeploymentArn: 'service-deployment-1',
      status: deploymentStatus,
      targetTaskDefinition: 'task-def-arn',
      statusReason: 'reason'
    )

    when:
    def result = task.execute(stage)

    then:
    1 * ecsNativeService.getServiceDeploymentStatus(
      'test', 'us-west-2', 'myapp', 'service-deployment-1') >> Calls.response(status)
    result.status == expectedStatus
    result.context.ecsNativeDeploymentStatus == status

    where:
    deploymentStatus       | expectedStatus
    'PENDING'              | ExecutionStatus.RUNNING
    'IN_PROGRESS'          | ExecutionStatus.RUNNING
    'ROLLBACK_IN_PROGRESS' | ExecutionStatus.RUNNING
    'SUCCESSFUL'           | ExecutionStatus.SUCCEEDED
    'ROLLBACK_SUCCESSFUL'  | ExecutionStatus.TERMINAL
    'ROLLBACK_FAILED'      | ExecutionStatus.TERMINAL
    'STOPPED'              | ExecutionStatus.TERMINAL
  }

  def 'resolves the ARN from the synthetic child of a Deck-authored deploy stage'() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline('orca')
    def wrapper = new StageExecutionImpl(pipeline, 'deploy', 'Deploy', [:])
    wrapper.refId = 'deploy-ref'
    def child = new StageExecutionImpl(pipeline, 'createServerGroup', 'Deploy in eu-central-1', [:])
    child.parentStageId = wrapper.id
    child.outputs.ecsNativeExpectedServiceDeploymentArn = 'service-deployment-from-child'
    pipeline.stages << wrapper << child
    def stage = new StageExecutionImpl(pipeline, 'test', 'test', [:])
    stage.requisiteStageRefIds = ['deploy-ref']

    expect:
    WaitForEcsNativeServiceDeploymentTask.resolveExpectedServiceDeploymentArn(stage) == 'service-deployment-from-child'
  }

  /**
   * A Deck-authored deploy as Orca builds it at runtime: a "deploy" wrapper with one synthetic
   * createServerGroup child per cluster, each carrying the ARN MonitorKatoTask wrote into its
   * CONTEXT (not its outputs), optionally followed by a Manual Judgment and then the stage under
   * test, wired through requisiteStageRefIds only.
   */
  private static StageExecutionImpl deployGraph(PipelineExecutionImpl pipeline, Map<String, Map> childContextsByName, boolean viaManualJudgment) {
    def wrapper = new StageExecutionImpl(pipeline, 'deploy', 'Deploy', [cloudProvider: 'ecs-native'])
    wrapper.refId = 'deploy-ref'
    pipeline.stages << wrapper
    childContextsByName.each { String name, Map context ->
      def child = new StageExecutionImpl(pipeline, 'createServerGroup', name, context)
      child.parentStageId = wrapper.id
      child.syntheticStageOwner = SyntheticStageOwner.STAGE_AFTER
      pipeline.stages << child
    }
    String upstreamRef = 'deploy-ref'
    if (viaManualJudgment) {
      def judgment = new StageExecutionImpl(pipeline, 'manualJudgment', 'Approve rollout', [:])
      judgment.refId = 'judge-ref'
      judgment.requisiteStageRefIds = ['deploy-ref']
      pipeline.stages << judgment
      upstreamRef = 'judge-ref'
    }
    def stage = new StageExecutionImpl(pipeline, 'ecsNativeContinueServiceDeployment', 'Continue', [
      account: 'test', region: 'us-west-2', serverGroupName: 'myapp'])
    stage.refId = 'continue-ref'
    stage.requisiteStageRefIds = [upstreamRef]
    pipeline.stages << stage
    return stage
  }

  def 'resolves the ARN through a Manual Judgment between the deploy and the Continue stage'() {
    given:
    def stage = deployGraph(PipelineExecutionImpl.newPipeline('orca'),
      ['Deploy in us-west-2': [ecsNativeExpectedServiceDeploymentArn: 'arn-from-child', bakeTimeInMinutes: 7]], true)
    def status = new EcsServiceDeploymentStatus(serviceDeploymentArn: 'arn-from-child', status: 'IN_PROGRESS')

    when:
    def result = task.execute(stage)

    then: 'the deploy child two hops upstream is found and polled'
    1 * ecsNativeService.getServiceDeploymentStatus('test', 'us-west-2', 'myapp', 'arn-from-child') >> Calls.response(status)
    result.status == ExecutionStatus.RUNNING

    and: 'the dynamic timeout is derived from that same child'
    task.getDynamicTimeout(stage) == TimeUnit.HOURS.toMillis(1) + TimeUnit.MINUTES.toMillis(7)
  }

  def 'a deploy that started several ecs-native deployments is ambiguous without an explicit ARN'() {
    given:
    def stage = deployGraph(PipelineExecutionImpl.newPipeline('orca'), [
      'Deploy in us-west-2'   : [ecsNativeExpectedServiceDeploymentArn: 'arn-west'],
      'Deploy in eu-central-1': [ecsNativeExpectedServiceDeploymentArn: 'arn-eu'],
    ], viaManualJudgment)

    when:
    WaitForEcsNativeServiceDeploymentTask.resolveExpectedServiceDeploymentArn(stage)

    then: 'neither child is picked silently; the message names both'
    def e = thrown(IllegalStateException)
    e.message.contains('2 ecs-native service deployments')
    e.message.contains("'Deploy in us-west-2' -> arn-west")
    e.message.contains("'Deploy in eu-central-1' -> arn-eu")
    e.message.contains('ecsNativeExpectedServiceDeploymentArn')

    and: 'the timeout calculation stays lenient instead of failing the stage from inside the handler'
    task.getDynamicTimeout(stage) == TimeUnit.HOURS.toMillis(1)

    where:
    viaManualJudgment << [false, true]
  }

  def 'a multi-cluster deploy whose children publish the ARN in their outputs is still ambiguous'() {
    given: 'the ARN only in the children outputs, where the ancestor-output fallback used to find it'
    def pipeline = PipelineExecutionImpl.newPipeline('orca')
    def stage = deployGraph(pipeline, ['Deploy in us-west-2': [:], 'Deploy in eu-central-1': [:]], false)
    pipeline.stages.findAll { it.type == 'createServerGroup' }.eachWithIndex { child, i ->
      child.outputs.ecsNativeExpectedServiceDeploymentArn = "arn-${i}".toString()
    }

    when:
    WaitForEcsNativeServiceDeploymentTask.resolveExpectedServiceDeploymentArn(stage)

    then:
    thrown(IllegalStateException)
  }

  def 'an explicit ARN on the stage selects one of several deployments'() {
    given:
    def stage = deployGraph(PipelineExecutionImpl.newPipeline('orca'), [
      'Deploy in us-west-2'   : [ecsNativeExpectedServiceDeploymentArn: 'arn-west', bakeTimeInMinutes: 3],
      'Deploy in eu-central-1': [ecsNativeExpectedServiceDeploymentArn: 'arn-eu', bakeTimeInMinutes: 11],
    ], true)
    stage.context.ecsNativeExpectedServiceDeploymentArn = 'arn-eu'

    expect:
    WaitForEcsNativeServiceDeploymentTask.resolveExpectedServiceDeploymentArn(stage) == 'arn-eu'
    task.getDynamicTimeout(stage) == TimeUnit.HOURS.toMillis(1) + TimeUnit.MINUTES.toMillis(11)
  }

  def 'an explicit ARN that matches none of a multi-cluster deploy fails rather than picking one'() {
    given:
    def stage = deployGraph(PipelineExecutionImpl.newPipeline('orca'), [
      'Deploy in us-west-2'   : [ecsNativeExpectedServiceDeploymentArn: 'arn-west'],
      'Deploy in eu-central-1': [ecsNativeExpectedServiceDeploymentArn: 'arn-eu'],
    ], false)
    stage.context.ecsNativeExpectedServiceDeploymentArn = 'arn-from-an-earlier-run'

    when:
    WaitForEcsNativeServiceDeploymentTask.resolveExpectedServiceDeploymentArn(stage)

    then:
    def e = thrown(IllegalStateException)
    e.message.contains('arn-from-an-earlier-run')
    e.message.contains('matches none')
  }

  def 'an explicit deploymentStageRefId to a multi-cluster deploy is resolved with the same rules'() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline('orca')
    def stage = deployGraph(pipeline, [
      'Deploy in us-west-2'   : [ecsNativeExpectedServiceDeploymentArn: 'arn-west'],
      'Deploy in eu-central-1': [ecsNativeExpectedServiceDeploymentArn: 'arn-eu'],
    ], true)
    // Not connected through requisites at all; only the explicit reference points at the deploy.
    stage.requisiteStageRefIds = []
    stage.context.deploymentStageRefId = 'deploy-ref'

    when:
    WaitForEcsNativeServiceDeploymentTask.resolveExpectedServiceDeploymentArn(stage)

    then:
    thrown(IllegalStateException)

    when:
    stage.context.ecsNativeExpectedServiceDeploymentArn = 'arn-west'

    then:
    WaitForEcsNativeServiceDeploymentTask.resolveExpectedServiceDeploymentArn(stage) == 'arn-west'
  }

  def 'a single-cluster deploy still wins over a stale ARN on the stage, through a Manual Judgment too'() {
    given:
    def stage = deployGraph(PipelineExecutionImpl.newPipeline('orca'),
      ['Deploy in us-west-2': [ecsNativeExpectedServiceDeploymentArn: 'arn-current']], true)
    stage.context.ecsNativeExpectedServiceDeploymentArn = 'arn-stale'

    expect:
    WaitForEcsNativeServiceDeploymentTask.resolveExpectedServiceDeploymentArn(stage) == 'arn-current'
  }

  private static Map katoTask(String id, Map metadata) {
    [id: id, status: [completed: true], resultObjects: [[
      serverGroupNames: ['us-west-2:myapp'],
      deployments: [[cloudProvider: 'ecs-native', serverGroupName: 'myapp', metadata: metadata]]
    ]]]
  }

  def 'a deploy that follows another ecs-native deploy waits for its own deployment, not the predecessor'() {
    given: 'a preceding ecs-native deploy that already finished with its own ARN'
    def stage = stageWithContext([
      account: 'test', region: 'us-west-2', serverGroupName: 'myapp',
      'kato.tasks': [katoTask('1', [ecsNativeExpectedServiceDeploymentArn: 'own-deployment'])]
    ], 'predecessor-deployment')
    def status = new EcsServiceDeploymentStatus(serviceDeploymentArn: 'own-deployment', status: 'IN_PROGRESS')

    when:
    def result = task.execute(stage)

    then:
    1 * ecsNativeService.getServiceDeploymentStatus('test', 'us-west-2', 'myapp', 'own-deployment') >> Calls.response(status)
    0 * ecsNativeService.getServiceDeploymentStatus(_, _, _, 'predecessor-deployment')
    result.status == ExecutionStatus.RUNNING
  }

  def 'uses the most recent own kato task ARN when the stage retried its write'() {
    given:
    def stage = stageWithContext([
      'kato.tasks': [
        katoTask('1', [ecsNativeExpectedServiceDeploymentArn: 'first-attempt']),
        katoTask('2', [ecsNativeExpectedServiceDeploymentArn: 'second-attempt'])]
    ], 'predecessor-deployment')

    expect:
    WaitForEcsNativeServiceDeploymentTask.resolveExpectedServiceDeploymentArn(stage) == 'second-attempt'
  }

  def 'a stage whose own write returned no ARN never falls back to an upstream deployment'() {
    given:
    def stage = stageWithContext([
      account: 'test', region: 'us-west-2', serverGroupName: 'myapp',
      'kato.tasks': [katoTask('1', [ecsNativeExpectedTaskDefinition: 'task-def:2'])]
    ], 'predecessor-deployment')

    when:
    task.execute(stage)

    then:
    thrown(IllegalArgumentException)
    0 * ecsNativeService.getServiceDeploymentStatus(*_)
  }

  def 'Continue/Stop kato results carry no deployments, so the referenced deploy stage still wins'() {
    given:
    def stage = stageWithContext([
      'kato.tasks': [[id: '1', status: [completed: true], resultObjects: []]]
    ], 'deploy-deployment')

    expect:
    WaitForEcsNativeServiceDeploymentTask.resolveExpectedServiceDeploymentArn(stage) == 'deploy-deployment'
  }

  def 'resolves ARN from referenced deploy stage output before direct fallback'() {
    given:
    def stage = stageWithContext([
      account: 'test', region: 'us-west-2', serverGroupName: 'myapp',
      ecsNativeExpectedServiceDeploymentArn: 'legacy-arn'
    ], 'service-deployment-1')
    def status = new EcsServiceDeploymentStatus(
      serviceDeploymentArn: 'service-deployment-1', status: 'IN_PROGRESS', lifecycleStage: 'SCALE_UP')

    when:
    def result = task.execute(stage)

    then:
    1 * ecsNativeService.getServiceDeploymentStatus(
      'test', 'us-west-2', 'myapp', 'service-deployment-1') >> Calls.response(status)
    result.status == ExecutionStatus.RUNNING
  }

  private EcsServiceDeploymentStatus statusWithHook(String deploymentStatus, String hookStatus) {
    new EcsServiceDeploymentStatus(
      serviceDeploymentArn: 'service-deployment-1',
      status: deploymentStatus,
      lifecycleStage: 'POST_TEST_TRAFFIC_SHIFT',
      lifecycleHookDetails: [new EcsServiceDeploymentStatus.LifecycleHook(hookId: 'hook-1', status: hookStatus)])
  }

  @Unroll
  def 'gate wait with a #hookStatus PAUSE hook while #deploymentStatus maps to #expected'() {
    given:
    def stage = stageWithContext([
      account: 'test', region: 'us-west-2', serverGroupName: 'myapp',
      ecsNativeExpectedServiceDeploymentArn: 'service-deployment-1',
      ecsNativeWaitForLifecycleGate: true
    ])

    when:
    def result = task.execute(stage)

    then:
    1 * ecsNativeService.getServiceDeploymentStatus(
      'test', 'us-west-2', 'myapp', 'service-deployment-1') >> Calls.response(statusWithHook(deploymentStatus, hookStatus))
    result.status == expected

    where:
    deploymentStatus | hookStatus        || expected
    'IN_PROGRESS'    | 'AWAITING_ACTION' || ExecutionStatus.SUCCEEDED
    'IN_PROGRESS'    | 'IN_PROGRESS'     || ExecutionStatus.RUNNING
    'IN_PROGRESS'    | 'SUCCEEDED'       || ExecutionStatus.RUNNING
  }

  def 'a hook awaiting action is ignored when the stage is not waiting for the gate'() {
    given:
    def stage = stageWithContext([
      account: 'test', region: 'us-west-2', serverGroupName: 'myapp',
      ecsNativeExpectedServiceDeploymentArn: 'service-deployment-1'
    ])

    when:
    def result = task.execute(stage)

    then:
    1 * ecsNativeService.getServiceDeploymentStatus(
      'test', 'us-west-2', 'myapp', 'service-deployment-1') >> Calls.response(statusWithHook('IN_PROGRESS', 'AWAITING_ACTION'))
    result.status == ExecutionStatus.RUNNING
  }

  @Unroll
  def 'rollback outcome with ecsNativeAcceptRollback=#accept is #expected'() {
    given:
    def stage = stageWithContext([
      account: 'test', region: 'us-west-2', serverGroupName: 'myapp',
      ecsNativeExpectedServiceDeploymentArn: 'service-deployment-1',
      ecsNativeAcceptRollback: accept
    ])

    when:
    def result = task.execute(stage)

    then:
    1 * ecsNativeService.getServiceDeploymentStatus(
      'test', 'us-west-2', 'myapp', 'service-deployment-1') >> Calls.response(
        new EcsServiceDeploymentStatus(serviceDeploymentArn: 'service-deployment-1', status: 'ROLLBACK_SUCCESSFUL'))
    result.status == expected

    where:
    accept || expected
    true   || ExecutionStatus.SUCCEEDED
    false  || ExecutionStatus.TERMINAL
  }

  def 'ROLLBACK_FAILED stays terminal even when a rollback was requested'() {
    given:
    def stage = stageWithContext([
      account: 'test', region: 'us-west-2', serverGroupName: 'myapp',
      ecsNativeExpectedServiceDeploymentArn: 'service-deployment-1',
      ecsNativeAcceptRollback: true
    ])

    when:
    def result = task.execute(stage)

    then:
    1 * ecsNativeService.getServiceDeploymentStatus(
      'test', 'us-west-2', 'myapp', 'service-deployment-1') >> Calls.response(
        new EcsServiceDeploymentStatus(serviceDeploymentArn: 'service-deployment-1', status: 'ROLLBACK_FAILED'))
    result.status == ExecutionStatus.TERMINAL
  }

  def 'stop wait succeeds when the exact deployment reaches STOPPED'() {
    given:
    def stage = stageWithContext([
      account: 'test', region: 'us-west-2', serverGroupName: 'myapp',
      ecsNativeExpectedServiceDeploymentArn: 'service-deployment-1',
      ecsNativeWaitForStopped: true
    ])
    def status = new EcsServiceDeploymentStatus(
      serviceDeploymentArn: 'service-deployment-1', status: 'STOPPED')

    when:
    def result = task.execute(stage)

    then:
    1 * ecsNativeService.getServiceDeploymentStatus(
      'test', 'us-west-2', 'myapp', 'service-deployment-1') >> Calls.response(status)
    result.status == ExecutionStatus.SUCCEEDED
  }

  def 'completed different deployment identity is terminal, never success'() {
    given:
    def stage = stageWithContext([
      account: 'test',
      region: 'us-west-2',
      serverGroupName: 'myapp',
      ecsNativeExpectedServiceDeploymentArn: 'deployment-1'
    ])
    def inProgress = new EcsServiceDeploymentStatus(
      serviceDeploymentArn: 'deployment-1',
      status: 'IN_PROGRESS',
      targetTaskDefinition: 'task-def-1'
    )
    def rollbackDeployment = new EcsServiceDeploymentStatus(
      serviceDeploymentArn: 'deployment-2',
      status: 'SUCCESSFUL',
      targetTaskDefinition: 'task-def-previous'
    )

    when:
    def first = task.execute(stage)
    def second = task.execute(stage)

    then:
    2 * ecsNativeService.getServiceDeploymentStatus(
      'test', 'us-west-2', 'myapp', 'deployment-1') >>> [Calls.response(inProgress), Calls.response(rollbackDeployment)]
    first.status == ExecutionStatus.RUNNING
    second.status == ExecutionStatus.TERMINAL
  }

  def "resolves region and serverGroupName from a preceding deploy stage's output when not set directly"() {
    given:
    def stage = stageWithContext([
      account: 'test',
      ecsNativeExpectedServiceDeploymentArn: 'service-deployment-1',
      'deploy.server.groups': ['us-west-2': ['myapp']]
    ])
    def status = new EcsServiceDeploymentStatus(
      serviceDeploymentArn: 'service-deployment-1',
      status: 'IN_PROGRESS')

    when:
    def result = task.execute(stage)

    then:
    1 * ecsNativeService.getServiceDeploymentStatus(
      'test', 'us-west-2', 'myapp', 'service-deployment-1') >> Calls.response(status)
    result.status == ExecutionStatus.RUNNING
  }

  def "falls back to 'credentials' for the account"() {
    given:
    def stage = stageWithContext([
      credentials: 'test',
      region: 'eu-central-1',
      serverGroupName: 'myapp',
      ecsNativeExpectedServiceDeploymentArn: 'service-deployment-1'
    ])
    def status = new EcsServiceDeploymentStatus(
      serviceDeploymentArn: 'service-deployment-1',
      status: 'SUCCESSFUL')

    when:
    def result = task.execute(stage)

    then:
    1 * ecsNativeService.getServiceDeploymentStatus(
      'test', 'eu-central-1', 'myapp', 'service-deployment-1') >> Calls.response(status)
    result.status == ExecutionStatus.SUCCEEDED
  }

  def 'throws when deployment identity cannot be resolved'() {
    given:
    def stage = stageWithContext([account: 'test', region: 'us-west-2', serverGroupName: 'myapp'])

    when:
    task.execute(stage)

    then:
    thrown(IllegalArgumentException)
  }
}
