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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.netflix.spinnaker.orca.SimpleTaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import com.netflix.spinnaker.orca.kato.api.ops.DeleteAsgOperation
import spock.lang.Specification
import spock.lang.Subject

class DeleteAsgTaskSpec extends Specification {
  @Subject task = new DeleteAsgTask()
  def context = new SimpleTaskContext()
  def mapper = new ObjectMapper()
  def taskId = new TaskId(UUID.randomUUID().toString())

  def deleteASGConfig = [
    asgName    : "test-asg",
    regions    : ["us-west-1"],
    credentials: "fzlem",
    forceDelete: true
  ]

  def setup() {
    mapper.registerModule(new GuavaModule())

    task.mapper = mapper

    deleteASGConfig.each {
      context."deleteAsg.$it.key" = it.value
    }
  }

  def "creates a delete ASG task based on job parameters"() {
    given:
    def operations
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations = it[0]
        rx.Observable.from(taskId)
      }
    }

    when:
    task.execute(context)

    then:
    operations.size() == 1
    with(operations[0].deleteAsgDescription) {
      it instanceof DeleteAsgOperation
      asgName == deleteASGConfig.asgName
      regions == deleteASGConfig.regions
      credentials == deleteASGConfig.credentials
      forceDelete == deleteASGConfig.forceDelete
    }
  }

  def "returns a success status with the kato task id"() {
    given:
    task.kato = Stub(KatoService) {
      requestOperations(*_) >> rx.Observable.from(taskId)
    }

    when:
    def result = task.execute(context)

    then:
    result.status == TaskResult.Status.SUCCEEDED
    result.outputs."kato.task.id" == taskId
    result.outputs."deploy.account.name" == deleteASGConfig.credentials
  }
}
