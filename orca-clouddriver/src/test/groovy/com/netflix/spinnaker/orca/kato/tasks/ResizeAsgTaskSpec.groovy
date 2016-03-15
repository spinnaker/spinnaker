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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeSupport
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceSupport
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

class ResizeAsgTaskSpec extends Specification {
  @Subject task = new ResizeAsgTask()
  def stage = new PipelineStage(type: "pipeline")
  def taskId = new TaskId(UUID.randomUUID().toString())

  def resizeASGConfig = [
    asgName    : "test-asg",
    regions    : ["us-west-1", "us-east-1"],
    credentials: "fzlem",
    capacity   : [
      min    : 1,
      max    : 10,
      desired: 6
    ]
  ]

  def setup() {
    task.targetReferenceSupport = Stub(TargetReferenceSupport)
    stage.context.putAll(resizeASGConfig)
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
    with(operations[0].resizeAsgDescription) {
      it instanceof Map
      asgName == this.resizeASGConfig.asgName
      regions == this.resizeASGConfig.regions
      credentials == this.resizeASGConfig.credentials
      regions.size() == 2
      capacity.with {
        min = this.resizeASGConfig.capacity.min
        max = this.resizeASGConfig.capacity.max
        desired = this.resizeASGConfig.capacity.desired
      }
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
    result.stageOutputs."deploy.account.name" == resizeASGConfig.credentials
  }

  def "gets target dynamically when configured"() {
    given:
    def targetReferenceSupport = Mock(TargetReferenceSupport)
    def resizeSupport = Mock(ResizeSupport)

    task.kato = Stub(KatoService) {
      requestOperations(*_) >> rx.Observable.from(taskId)
    }
    task.targetReferenceSupport = targetReferenceSupport
    task.resizeSupport = resizeSupport

    def calculatedCapacity = [ min: 5, max: 5, desired: 5]
    def resizeDescriptor = [
        asgName: "asg-v003",
        capacity: calculatedCapacity,
        regions: ["us-east-1"],
        credentials: "prod"
    ]

    when:
    def result = task.execute(stage)

    then:
    1 * targetReferenceSupport.isDynamicallyBound(stage) >> true
    1 * targetReferenceSupport.getDynamicallyBoundTargetAsgReference(stage) >> []
    1 * resizeSupport.createResizeStageDescriptors(stage, _) >> [resizeDescriptor]
    result.status == ExecutionStatus.SUCCEEDED
    result.stageOutputs."kato.last.task.id" == taskId
    result.stageOutputs."deploy.account.name" == "prod"
    result.stageOutputs.capacity == calculatedCapacity
    result.stageOutputs."deploy.server.groups" == [ "us-east-1" : ["asg-v003"]]
  }
}
