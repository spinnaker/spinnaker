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
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage

class DeleteSecurityGroupTaskSpec extends Specification {
  @Subject task = new DeleteSecurityGroupTask()
  def stage = new PipelineStage(type: "whatever")
  def taskId = new TaskId(UUID.randomUUID().toString())

  def config = [
    securityGroupName : "foo",
    vpcId             : 'vpc1',
    regions           : ["us-west-1"],
    credentials       : "fzlem"
  ]

  def "creates a delete security group task based on job parameters"() {
    stage.context.putAll(config)
    def operations
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations = it[0]
        rx.Observable.from(taskId)
      }
    }

    when:
    task.execute(stage.asImmutable())

    then:
    operations.size() == 1
    with(operations[0].deleteSecurityGroupDescription) {
      it instanceof Map
      securityGroupName == this.config.securityGroupName
      vpcId == this.config.vpcId
      regions == this.config.regions
      credentials == this.config.credentials
    }
  }

  def "creates a delete security group task without optional parameters"() {
    config.remove("vpcId")
    stage.context.putAll(config)
    def operations
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations = it[0]
        rx.Observable.from(taskId)
      }
    }

    when:
    task.execute(stage.asImmutable())

    then:
    operations.size() == 1
    with(operations[0].deleteSecurityGroupDescription) {
      it instanceof Map
      securityGroupName == this.config.securityGroupName
      vpcId == this.config.vpcId
      regions == this.config.regions
      credentials == this.config.credentials
    }
  }

  def "returns a success status with the kato task id"() {
    stage.context.putAll(config)
    task.kato = Stub(KatoService) {
      requestOperations(*_) >> rx.Observable.from(taskId)
    }

    when:
    def result = task.execute(stage.asImmutable())

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.outputs."kato.task.id" == taskId
    result.outputs."delete.account.name" == config.credentials
  }
}
