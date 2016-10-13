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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.Task
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class MonitorKatoTaskSpec extends Specification {

  @Subject task = new MonitorKatoTask()

  @Unroll("result is #expectedResult if kato task is #katoStatus")
  def "result depends on Kato task status"() {
    given:
    task.kato = Stub(KatoService) {
      lookupTask(taskId) >> Observable.from(new Task(taskId, new Task.Status(completed: completed, failed: failed), [], []))
    }

    and:
    def stage = new PipelineStage(new Pipeline(), "whatever", [
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
      lookupTask(taskId) >> Observable.from(new Task(taskId, new Task.Status(completed: true), resultObjects, []))
    }

    and:
    def stage = new PipelineStage(new Pipeline(), "whatever", [
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
    def stage = new PipelineStage(new Pipeline(), "whatever", context)

    when:
    def result = task.execute(stage)

    then:
    result.status
    result.stageOutputs.isEmpty()
    result.globalOutputs.isEmpty()

    where:
    context                     | _
    ["kato.last.task.id": null] | _
    [:]                         | _
  }

}
