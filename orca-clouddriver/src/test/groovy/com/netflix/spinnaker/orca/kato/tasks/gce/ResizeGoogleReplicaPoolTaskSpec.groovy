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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeSupport
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceSupport
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

class ResizeGoogleReplicaPoolTaskSpec extends Specification {
  def targetReferenceSupport = Mock(TargetReferenceSupport)
  def resizeSupport = Mock(ResizeSupport)
  @Subject
    task = new ResizeGoogleReplicaPoolTask(targetReferenceSupport: targetReferenceSupport, resizeSupport: resizeSupport)
  def stage = new PipelineStage(type: "whatever")
  def taskId = new TaskId(UUID.randomUUID().toString())

  def resizeASGConfig = [
    asgName    : "test-replica-pool",
    zones      : ["us-central1-b"],
    credentials: "fzlem",
    capacity   : [
      min    : 1,
      max    : 10,
      desired: 6
    ]
  ]

  def setup() {
    stage.context.putAll(resizeASGConfig)
  }

  def "creates a resize google replica pool task based on job parameters"() {
    given:
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
      with(operations[0].resizeGoogleReplicaPoolDescription) {
        it instanceof Map
        replicaPoolName == this.resizeASGConfig.asgName
        zone == this.resizeASGConfig.zones[0]
        credentials == this.resizeASGConfig.credentials
        numReplicas == this.resizeASGConfig.capacity.desired
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
      result.outputs."kato.task.id" == taskId
      result.outputs."deploy.account.name" == resizeASGConfig.credentials
  }

  def "creates a resize google replica pool task from a dynamic pipeline stage"() {
    given:
      def operations
      task.kato = Mock(KatoService) {
        1 * requestOperations(*_) >> {
          operations = it[0]
          rx.Observable.from(taskId)
        }
      }
      targetReferenceSupport.isDynamicallyBound(_) >> true
      1 * resizeSupport.createResizeStageDescriptors(_, _) >> [
        [
          action         : "scale_up",
          cluster        : "testapp-asg",
          credentials    : "fzlem",
          numReplicas    : 6,
          provider       : "gce",
          regions        : ["us-central1"],
          replicaPoolName: "test-replica-pool",
          scaleNum       : 2,
          target         : "current_asg_dynamic",
          zones          : ["us-central1-b"],
        ]
      ]


    when:
      task.execute(stage.asImmutable())

    then:
      operations.size() == 1
      with(operations[0].resizeGoogleReplicaPoolDescription) {
        it instanceof Map
        replicaPoolName == this.resizeASGConfig.asgName
        zone == this.resizeASGConfig.zones[0]
        credentials == this.resizeASGConfig.credentials
        numReplicas == this.resizeASGConfig.capacity.desired
      }
  }
}
