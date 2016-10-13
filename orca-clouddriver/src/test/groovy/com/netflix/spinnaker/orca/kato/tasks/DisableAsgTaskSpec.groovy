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

import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceConfiguration
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceSupport
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

class DisableAsgTaskSpec extends Specification {
  @Subject task = new DisableAsgTask()
  def stage = new PipelineStage(type: "whatever")
  def mapper = new OrcaObjectMapper()
  def taskId = new TaskId(UUID.randomUUID().toString())

  def disableASGConfig = [
    asgName    : "test-asg",
    regions    : ["us-west-1", "us-east-1"],
    credentials: "fzlem"
  ]

  def setup() {
    mapper.registerModule(new GuavaModule())

    task.mapper = mapper
    task.targetReferenceSupport = Mock(TargetReferenceSupport)

    stage.context.putAll(disableASGConfig)
  }

  def "creates a disable ASG task based on job parameters"() {
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
    with(operations[0].disableAsgDescription) {
      it instanceof Map
      asgName == this.disableASGConfig.asgName
      regions == this.disableASGConfig.regions
      credentials == this.disableASGConfig.credentials
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
    result.stageOutputs."kato.last.task.id" == taskId
    result.stageOutputs."deploy.account.name" == disableASGConfig.credentials
  }

  void "should get target dynamically when configured"() {
    setup:
    stage.context.target = TargetReferenceConfiguration.Target.ancestor_asg_dynamic
    task.kato = Stub(KatoService) {
      requestOperations(*_) >> rx.Observable.from(taskId)
    }

    when:
    def result = task.execute(stage)

    then:
    1 * task.targetReferenceSupport.isDynamicallyBound(stage) >> true
    1 * task.targetReferenceSupport.getDynamicallyBoundTargetAsgReference(stage) >> [
      asg: [name: "foo-v001"],
      region: "us-east-1"
    ]
    result.stageOutputs.asgName == "foo-v001"
    result.stageOutputs."deploy.server.groups" == ["us-west-1": ["foo-v001"], "us-east-1": ["foo-v001"]]
  }
}
