/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.orca.kato.tasks.gce

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

class TerminateGoogleInstancesTaskSpec extends Specification {

  @Subject task = new TerminateGoogleInstancesTask()
  def stage = new PipelineStage(type: "whatever")
  def taskId = new TaskId(UUID.randomUUID().toString())

  def terminateInstancesConfig = [
    cloudProvider           : "gce",
    zone                    : "us-central1-b",
    credentials             : "fzlem",
    managedInstanceGroupName: "myapp-test-v001",
    instanceIds             : ['myapp-test-v001-abcd', 'myapp-test-v001-efgh'],
    launchTimes             : [1419273631008, 1419273085007]
  ]
  def serverGroup = "some-server-group"

  def setup() {
    stage.context.putAll(terminateInstancesConfig)
  }

  def "creates a terminate google instances task based on job parameters"() {
    given:
    def operations
    task.kato = Mock(KatoService) {
      1 * requestOperations(stage.context.cloudProvider, _) >> {
        operations = it[1]
        rx.Observable.from(taskId)
      }
    }

    when:
    task.execute(stage.asImmutable())

    then:
    operations.size() == 1
    def katoRequest = operations[0].terminateInstances
    katoRequest instanceof Map
    validTerminateInstancesCall(katoRequest)
  }

  void validTerminateInstancesCall(Map katoRequest) {
    with (katoRequest) {
      assert zone == this.terminateInstancesConfig.zone
      assert credentials == this.terminateInstancesConfig.credentials
      assert managedInstanceGroupName == this.terminateInstancesConfig.managedInstanceGroupName
      assert instanceIds == this.terminateInstancesConfig.instanceIds
      assert launchTimes == this.terminateInstancesConfig.launchTimes
    }
  }

  def "returns a success status with the kato task id"() {
    given:
    task.kato = Stub(KatoService) {
      requestOperations(*_) >> rx.Observable.from(taskId)
    }

    when:
    def result = task.execute(stage.asImmutable())

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.outputs."kato.last.task.id" == taskId
    result.outputs."terminate.account.name" == terminateInstancesConfig.credentials
  }
}
