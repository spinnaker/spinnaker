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



package com.netflix.spinnaker.orca.kato.tasks

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import com.netflix.spinnaker.orca.SimpleTaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.Task
import com.netflix.spinnaker.orca.kato.api.TaskId
import rx.Observable

class MonitorKatoTaskSpec extends Specification {

  @Subject task = new MonitorKatoTask()

  @Unroll("result is #expectedResult if kato task is #katoStatus")
  def "result depends on Kato task status"() {
    given:
    task.kato = Stub(KatoService) {
      lookupTask(taskId) >> Observable.from(new Task(taskId, new Task.Status(completed: completed, failed: failed), [], []))
    }

    and:
    def context = new SimpleTaskContext()
    context."kato.last.task.id" = new TaskId(taskId)

    expect:
    task.execute(context).status == expectedResult

    where:
    completed | failed | expectedResult
    true      | false  | TaskResult.Status.SUCCEEDED
    false     | false  | TaskResult.Status.RUNNING
    true      | true   | TaskResult.Status.FAILED

    taskId = "kato-task-id"
    katoStatus = completed ? "completed" : "incomplete"
  }

}
