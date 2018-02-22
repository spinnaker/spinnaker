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

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.Task
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class MonitorKatoTaskSpec extends Specification {

  def now = Instant.now()
  @Subject task = new MonitorKatoTask(new NoopRegistry(), Clock.fixed(now, ZoneId.of("UTC")))

  @Unroll("result is #expectedResult if kato task is #katoStatus")
  def "result depends on Kato task status"() {
    given:
    task.kato = Stub(KatoService) {
      lookupTask(taskId, false) >> Observable.from(new Task(taskId, new Task.Status(completed: completed, failed: failed), [], []))
    }

    and:
    def stage = new Stage(Execution.newPipeline("orca"), "whatever", [
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
    task.kato = Stub(KatoService) {
      lookupTask(taskId, false) >> Observable.from(new Task(taskId, new Task.Status(completed: true), resultObjects, []))
    }

    and:
    def stage = new Stage(Execution.newPipeline("orca"), "whatever", [
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
    def stage = new Stage(Execution.newPipeline("orca"), "whatever", context)

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

  @Unroll
  def "should retry if the task is not found in clouddriver until timeout #desc"() {
    given:
    def ctx = [
      "kato.last.task.id": new TaskId(taskId)
    ]
    if (previousRetry) {
      ctx.put('kato.task.firstNotFoundRetry', now.minusMillis(elapsed).toEpochMilli())
    } else {
      if (previousRetryFlag != null) {
        ctx.put('kato.task.firstNotFoundRetry', previousRetryFlag)
      }
    }
    def stage = new Stage(Execution.newPipeline("orca"), "whatever", ctx)
    task.kato = Stub(KatoService) {
      lookupTask(taskId, false) >> { retrofit404() }
    }

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING
    result.context['kato.task.notFoundRetryCount'] == 1

    where:
    taskId = "katoTaskId"
    desc                 | elapsed                                    | previousRetry | previousRetryFlag
    "first failure"      | 0                                          |  false        | null
    "first failure w -1" | 0                                          |  false        | -1
    "first retry"        | 1                                          |  true         | 'N/A'
    "about to fail"      | MonitorKatoTask.TASK_NOT_FOUND_TIMEOUT     |  true         | 'N/A'
  }

  def "should set kato.task.skipReplica=true when getting a task from clouddriver times out"() {
    given:
    def taskId = "katoTaskId"
    def elapsed = MonitorKatoTask.TASK_NOT_FOUND_TIMEOUT + 1
    task.kato = Mock(KatoService)

    def stage = stage {
      type = "type"
      context = [
        "kato.last.task.id": new TaskId(taskId),
        "kato.task.firstNotFoundRetry": now.minusMillis(elapsed).toEpochMilli()
      ]
    }

    when:
    def result = task.execute(stage)

    then:
    result.context['kato.task.skipReplica'] == true
    notThrown(RetrofitError)
    with(task.kato) {
      1 * lookupTask(taskId, false) >> { retrofit404() }
      0 * lookupTask(taskId, true)
    }
  }

  def "should get task from master when kato.task.skipReplica=true"() {
    given:
    def taskId = "katoTaskId"
    def elapsed = MonitorKatoTask.TASK_NOT_FOUND_TIMEOUT + 1
    task.kato = Mock(KatoService)
    def stage = stage {
      context = [
        "kato.last.task.id": new TaskId(taskId),
        "kato.task.firstNotFoundRetry": now.minusMillis(elapsed).toEpochMilli(),
        "kato.task.skipReplica": true
      ]
    }

    when:
    task.execute(stage)

    then:
    notThrown(RetrofitError)
    with(task.kato) {
      1 * lookupTask(taskId, true) >> Observable.from(new Task(taskId, new Task.Status(completed: true, failed: false), [], []))
      0 * lookupTask(taskId, false)
    }
  }

  def "should timeout if task not not found after timeout period"() {
    given:
    def taskId = "katoTaskId"
    def stage = stage {
      context = [
        "kato.last.task.id": new TaskId(taskId),
        "kato.task.firstNotFoundRetry": now.minusMillis(elapsed).toEpochMilli(),
        "kato.task.skipReplica": true
      ]
    }

    task.kato = Stub(KatoService) {
      lookupTask(taskId, true) >> { retrofit404() }
    }

    when:
    task.execute(stage)

    then:
    thrown(RetrofitError)

    where:
    elapsed = MonitorKatoTask.TASK_NOT_FOUND_TIMEOUT + 1
  }

  def retrofit404() {
    throw RetrofitError.httpError("http://localhost", new Response("http://localhost", 404, "Not Found", [], new TypedByteArray("application/json", new byte[0])), null, Task)
  }
}
