/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.Task
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class MonitorKatoTaskSpec extends Specification {
  def now = Instant.now()
  def retrySupport = Spy(RetrySupport) {
    _ * sleep(_) >> { /* do nothing */ }
  }
  KatoService kato = Mock(KatoService)
  DynamicConfigService dynamicConfigService = Mock()

  @Subject task = new MonitorKatoTask(kato, new NoopRegistry(), Clock.fixed(now, ZoneId.of("UTC")), dynamicConfigService, retrySupport)

  @Unroll("result is #expectedResult if kato task is #katoStatus")
  def "result depends on Kato task status"() {
    given:
    kato.lookupTask(taskId, false) >> new Task(taskId, new Task.Status(completed: completed, failed: failed), [], [], [])

    and:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "whatever", [
      "kato.last.task.id": new TaskId(taskId)
    ])

    expect:
    task.execute(stage).status == expectedResult

    where:
    completed | failed | expectedResult
    true      | false  | ExecutionStatus.SUCCEEDED
    false     | false  | ExecutionStatus.RUNNING
    true      | true   | ExecutionStatus.TERMINAL

    taskId = "kato-task-id"
    katoStatus = completed ? "completed" : "incomplete"
  }

  @Unroll("result is #expectedResult if katoResultExpected is #katoResultExpected and resultObject is #resultObjects")
  def "result depends on Kato task status and result object size for create/upsert operations"() {
    given:
    kato.lookupTask(taskId, false) >> new Task(taskId, new Task.Status(completed: true), resultObjects, [], [])

    and:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "whatever", [
        "kato.last.task.id": new TaskId(taskId),
        "kato.result.expected": katoResultExpected,
        "deploy.server.groups": [:]
    ])

    expect:
    task.execute(stage).status == expectedResult

    where:
    katoResultExpected | resultObjects || expectedResult
    true               | null          || ExecutionStatus.RUNNING
    true               | []            || ExecutionStatus.RUNNING
    false              | []            || ExecutionStatus.SUCCEEDED
    true               | [[a: 1]]      || ExecutionStatus.SUCCEEDED
    false              | [[a: 1]]      || ExecutionStatus.SUCCEEDED
    null               | []            || ExecutionStatus.SUCCEEDED

    taskId = "kato-task-id"
  }

  @Unroll
  def "should automatically succeed if task id does not exist"() {
    given:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "whatever", context)

    when:
    def result = task.execute(stage)

    then:
    result.status
    result.context.isEmpty()
    result.outputs.isEmpty()

    where:
    context                     | _
    ["kato.last.task.id": null] | _
    [:]                         | _
  }

  def "should increment retry count when can't find the task"() {
    given:
    def taskId = "katoTaskId"

    def stage = stage {
      type = "type"
      context = [
        "kato.last.task.id": new TaskId(taskId),
      ]
    }

    when: 'task is not found'
    def result = task.execute(stage)

    then: 'should set the retry count'
    MonitorKatoTask.MAX_HTTP_INTERNAL_RETRIES * kato.lookupTask(taskId, false) >> { retrofit404() }
    result.context['kato.task.notFoundRetryCount'] == 1
    result.status == ExecutionStatus.RUNNING
    notThrown(RetrofitError)

    when: 'task is not found'
    stage.context.put('kato.task.notFoundRetryCount', 1)
    result = task.execute(stage)

    then: 'should increment task count'
    MonitorKatoTask.MAX_HTTP_INTERNAL_RETRIES * kato.lookupTask(taskId, false) >> { retrofit404() }
    result.context['kato.task.notFoundRetryCount'] == 2
    result.status == ExecutionStatus.RUNNING
    notThrown(RetrofitError)

    when: 'task is found, but not completed'
    1 * kato.lookupTask(taskId, false) >> new Task(taskId, new Task.Status(completed: false, failed: false), [], [], [])
    result = task.execute(stage)

    then: 'should reset the retry count'
    result.context['kato.task.notFoundRetryCount'] == 0
    result.status == ExecutionStatus.RUNNING
  }

  def "should retry clouddriver task if classified as retryable"() {
    given:
    def katoTask = new Task("katoTaskId", new Task.Status(true, true, true), [], [], [])

    def stage = stage {
      type = "type"
      context = [
        "kato.last.task.id": new TaskId(katoTask.id),
        "kato.task.terminalRetryCount": 8
      ]
    }

    when:
    def result = task.execute(stage)

    then:
    notThrown(RetrofitError)
    dynamicConfigService.isEnabled("tasks.monitor-kato-task.terminal-retries", _) >> true
    with(kato) {
      1 * lookupTask(katoTask.id, false) >> { katoTask }
      1 * resumeTask(katoTask.id) >> { new TaskId(katoTask.id) }
    }

    and:
    result.status == ExecutionStatus.RUNNING
    result.context["kato.task.lastStatus"] == ExecutionStatus.TERMINAL
    result.context['kato.task.terminalRetryCount'] == 9
  }

  def "should timeout if task not not found after too many retries"() {
    given:
    def taskId = "katoTaskId"
    def stage = stage {
      context = [
        "kato.last.task.id": new TaskId(taskId),
        "kato.task.notFoundRetryCount": 1000,
      ]
    }

    kato.lookupTask(taskId, false) >> { retrofit404() }

    when:
    task.execute(stage)

    then:
    thrown(RetrofitError)
  }

  def retrofit404() {
    throw RetrofitError.httpError("http://localhost", new Response("http://localhost", 404, "Not Found", [], new TypedByteArray("application/json", new byte[0])), null, Task)
  }

  @Unroll
  def "verify task outputs"() {
    given:
    kato.lookupTask(taskId, false) >> new Task(
        taskId,
        new Task.Status(completed: true, failed: false),
        [],
        [],
        [new Task.Output(manifest: manifest, phase: phase, stdOut: stdOut, stdError: stdError)]
    )

    and:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "whatever", [
        "kato.last.task.id": new TaskId(taskId)
    ])

    when:
    def result = task.execute(stage)

    then:
    !result.context.isEmpty()
    result.context.containsKey("kato.tasks")
    result.context.get("kato.tasks").collect().size() == 1
    result.context["kato.tasks"].collect().get(0)['outputs'] == [new Task.Output(manifest: manifest, phase: phase, stdOut: stdOut, stdError: stdError)]

    where:
    manifest        | phase                 | stdOut        | stdError
    "some-manifest" | "Deploy K8s Manifest" | "some output" | ""
    "some-manifest" | "Deploy K8s Manifest" | ""            | "error logs"
    ""              | ""                    | ""            | ""

    taskId = "kato-task-id"
  }
}
