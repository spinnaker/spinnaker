/*
 *  Copyright 2019 Pivotal, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.Task
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class CloudFoundryMonitorKatoServicesTaskTest extends Specification {
  @Subject task = new CloudFoundryMonitorKatoServicesTask(null)

  @Unroll("result is #expectedStatus if kato task is #katoStatus and resultObjects is #resultObjects")
  def "result depends on Kato task status and result object content"() {
    given:
    task.kato = Stub(KatoService) {
      lookupTask(taskIdString, true) >>
        Observable.from(new Task(taskIdString, new Task.Status(completed: completed, failed: failed), resultObjects, []))
    }

    and:
    def stage = new Stage(Execution.newPipeline("orca"), "deployService", context)

    when:
    def result = task.execute(stage)

    then:
    result.status == expectedStatus
    result.context == ["kato.last.task.id"           : new TaskId(taskIdString),
                       "kato.task.firstNotFoundRetry": -1,
                       "kato.task.notFoundRetryCount": 0,
                       "kato.tasks"                  : [["id"           : taskIdString,
                                                         "status"       : new Task.Status(completed, failed),
                                                         "history"      : [],
                                                         "resultObjects": resultObjects ?: []]]
                      ]

    where:
    cloudProvider = "cloud"
    taskIdString = "kato-task-id"
    credentials = "my-account"
    region = "org > space"
    taskId = new TaskId(taskIdString)
    context = [
      "cloudProvider"    : cloudProvider,
      "kato.last.task.id": taskId,
      "credentials"      : credentials,
      "region"           : region
    ]

    completed | failed | resultObjects      | expectedStatus
    false     | false  | []                 | ExecutionStatus.RUNNING
    true      | false  | null               | ExecutionStatus.RUNNING
    true      | false  | []                 | ExecutionStatus.RUNNING
    true      | true   | []                 | ExecutionStatus.TERMINAL

    katoStatus = completed ? "completed" : "incomplete"
  }

  def "should return SUCCEEDED when the kato task completes"() {
    given:
    def inProgressResult = ["type"               : "CREATE",
                            "state"              : "IN_PROGRESS",
                            "serviceInstanceName": "service-instance-name"]

    task.kato = Stub(KatoService) {
      lookupTask("kato-task-id", true) >>
        Observable.from(new Task("kato-task-id", new Task.Status(completed: true, failed: false), [inProgressResult], []))
    }

    and:
    def stage = new Stage(Execution.newPipeline("orca"), "deployService", ["cloudProvider"    : "cloud",
                                                                           "kato.last.task.id": new TaskId("kato-task-id"),
                                                                           "credentials"      : "my-account",
                                                                           "region"           : "org > space" ])

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context == ["kato.last.task.id"           : new TaskId("kato-task-id"),
                       "kato.task.firstNotFoundRetry": -1,
                       "kato.task.notFoundRetryCount": 0,
                       "kato.tasks"                  : [["id"           : "kato-task-id",
                                                         "status"       : new Task.Status(true, false),
                                                         "history"      : [],
                                                         "resultObjects": [inProgressResult]]],
                       "service.account": "my-account",
                       "service.instance.name": "service-instance-name",
                       "service.operation.type": "CREATE",
                       "service.region": "org > space"]
  }

  def "should return TERMINAL and an exception should be present when one is received from kato"() {
    given:
    def expectedException = ["type"     : "EXCEPTION",
                             "operation": "my-atomic-operation",
                             "cause"    : "MyException",
                             "message"  : "Epic Failure"]
    task.kato = Stub(KatoService) {
      lookupTask("kato-task-id", true) >>
        Observable.from(new Task("kato-task-id", new Task.Status(completed: true, failed: true), [expectedException], []))
    }

    and:
    def stage = new Stage(Execution.newPipeline("orca"), "deployService", ["cloudProvider"    : "cloud",
                                                                           "kato.last.task.id": new TaskId("kato-task-id"),
                                                                           "credentials"      : "my-account",
                                                                           "region"           : "org > space" ])


    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.TERMINAL
    result.context == ["kato.last.task.id"           : new TaskId("kato-task-id"),
                       "kato.task.firstNotFoundRetry": -1,
                       "kato.task.notFoundRetryCount": 0,
                       "kato.tasks"                  : [["id"           : "kato-task-id",
                                                         "status"       : new Task.Status(true, true),
                                                         "history"      : [],
                                                         "exception"    : expectedException,
                                                         "resultObjects": [expectedException]]],
    ]
  }
}
