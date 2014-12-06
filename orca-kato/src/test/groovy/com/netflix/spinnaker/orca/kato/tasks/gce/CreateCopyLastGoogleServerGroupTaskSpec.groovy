/*
 * Copyright 2014 Google, Inc.
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

import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import com.netflix.spinnaker.orca.kato.api.ops.gce.DeployGoogleServerGroupOperation
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

class CreateCopyLastGoogleServerGroupTaskSpec extends Specification {
  @Subject task = new CreateCopyLastGoogleServerGroupTask()
  def stage = new PipelineStage(new Pipeline(), "copyLastAsg_gce")
  def mapper = new OrcaObjectMapper()
  def taskId = new TaskId(UUID.randomUUID().toString())

  def copyLastAsgConfig = [
    application        : "myapp",
    stack              : "test",
    capacity           : [
            min    : 6,
            max    : 6,
            desired: 6
    ],
    image              : "some-base-image",
    instanceType       : "f1-micro",
    zone               : "us-central1-b",
    loadBalancers      : ["testlb"],
    source             : [
            zone            : "us-central1-a",
            serverGroupName : "myapp-test-v000"
    ],
    credentials        : "fzlem"
  ]

  def setup() {
    mapper.registerModule(new GuavaModule())

    task.mapper = mapper

    stage.pipeline.stages.add(stage)
    stage.context = copyLastAsgConfig
  }

  def "creates a create copy google server group task based on job parameters"() {
    given:
    def operations
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations = it[0]
        rx.Observable.from(taskId)
      }
    }

    when:
    task.execute(stage)

    then:
    operations.size() == 1

    with (operations[0].copyLastGoogleServerGroupDescription) {
      it instanceof DeployGoogleServerGroupOperation
      application == copyLastAsgConfig.application
      stack == copyLastAsgConfig.stack
      initialNumReplicas == copyLastAsgConfig.capacity.desired
      image == copyLastAsgConfig.image
      instanceType == copyLastAsgConfig.instanceType
      networkLoadBalancers == copyLastAsgConfig.loadBalancers
      zone == copyLastAsgConfig.zone
      source.zone == copyLastAsgConfig.source.zone
      source.serverGroupName == copyLastAsgConfig.source.serverGroupName
      credentials == copyLastAsgConfig.credentials
    }
  }

  def "returns a success status with the kato task id"() {
    given:
    task.kato = Stub(KatoService) {
      requestOperations(*_) >> rx.Observable.from(taskId)
    }

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.outputs."kato.task.id" == taskId
    result.outputs."deploy.account.name" == copyLastAsgConfig.credentials
  }
}
