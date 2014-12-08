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
import com.netflix.spinnaker.orca.kato.api.ops.gce.DisableGoogleServerGroupOperation
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

class DisableGoogleServerGroupTaskSpec extends Specification {
  @Subject task = new DisableGoogleServerGroupTask()
  def stage = new PipelineStage(new Pipeline(), "whatever")
  def mapper = new OrcaObjectMapper()
  def taskId = new TaskId(UUID.randomUUID().toString())

  def disableASGConfig = [
    asgName    : "test-asg",
    zones      : ["us-central1-b"],
    credentials: "fzlem"
  ]

  def setup() {
    mapper.registerModule(new GuavaModule())

    task.mapper = mapper

    stage.context = disableASGConfig
  }

  def "creates a disable google server group task based on job parameters"() {
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
    with(operations[0].disableGoogleReplicaPoolDescription) {
      it instanceof DisableGoogleServerGroupOperation
      replicaPoolName == disableASGConfig.asgName
      zone == disableASGConfig.zones[0]
      credentials == disableASGConfig.credentials
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
    result.outputs."deploy.account.name" == disableASGConfig.credentials
  }
}
