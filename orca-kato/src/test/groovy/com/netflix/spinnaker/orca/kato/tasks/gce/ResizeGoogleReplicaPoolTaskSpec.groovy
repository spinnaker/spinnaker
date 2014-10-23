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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.netflix.spinnaker.orca.PipelineStatus
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import com.netflix.spinnaker.orca.kato.api.ops.gce.ResizeGoogleReplicaPoolOperation
import com.netflix.spinnaker.orca.pipeline.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

class ResizeGoogleReplicaPoolTaskSpec extends Specification {
  @Subject task = new ResizeGoogleReplicaPoolTask()
  def stage = new PipelineStage("whatever")
  def mapper = new ObjectMapper()
  def taskId = new TaskId(UUID.randomUUID().toString())

  def resizeASGConfig = [
    asgName    : "test-replica-pool",
    zones      : "us-central1-b",
    credentials: "fzlem",
    capacity   : [
      min    : 1,
      max    : 10,
      desired: 6
    ]
  ]

  def setup() {
    mapper.registerModule(new GuavaModule())

    task.mapper = mapper

    stage.updateContext(resizeASGConfig)
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
    task.execute(stage)

    then:
    operations.size() == 1
    with(operations[0].resizeGoogleReplicaPoolDescription) {
      it instanceof ResizeGoogleReplicaPoolOperation
      replicaPoolName == resizeASGConfig.asgName
      zone == resizeASGConfig.zones[0]
      credentials == resizeASGConfig.credentials
      numReplicas == resizeASGConfig.capacity.desired
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
    result.status == PipelineStatus.SUCCEEDED
    result.outputs."kato.task.id" == taskId
    result.outputs."deploy.account.name" == resizeASGConfig.credentials
  }
}
