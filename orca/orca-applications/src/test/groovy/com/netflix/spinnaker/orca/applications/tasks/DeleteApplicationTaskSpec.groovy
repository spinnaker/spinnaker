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

package com.netflix.spinnaker.orca.applications.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.KeelService
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class DeleteApplicationTaskSpec extends Specification {
  @Subject
  def task = new DeleteApplicationTask(
      Mock(Front50Service),
      Mock(KeelService),
      new ObjectMapper(),
      Mock(DynamicConfigService))

  def config = [
    account    : "test",
    application: [
      "name": "application"
    ]
  ]
  def pipeline = pipeline {
    stage {
      type = "DeleteApplication"
      context = config
    }
  }

  void "should delete global application if it was only associated with a single account"() {
    given:
    task.front50Service = Mock(Front50Service) {
      1 * get(config.application.name) >> Calls.response(new Application())
      1 * delete(config.application.name) >> Calls.response(null)
      1 * deletePermission(config.application.name) >> Calls.response(null)
      0 * _._
    }
    task.keelService = Mock(KeelService) {
      1 * deleteDeliveryConfig(config.application.name) >> Calls.response(ResponseBody.create(MediaType.parse("application/json"), "[]"))
    }

    when:
    def taskResult = task.execute(pipeline.stages.first())

    then:
    taskResult.status == ExecutionStatus.SUCCEEDED
  }

  void "should keep track of previous state"() {
    given:
    Application application = new Application()
    task.front50Service = Mock(Front50Service) {
      1 * get(config.application.name) >> Calls.response(application)
      1 * delete(config.application.name) >> Calls.response(null)
      1 * deletePermission(config.application.name) >> Calls.response(null)
      0 * _._
    }
    task.keelService = Mock(KeelService) {
      1 * deleteDeliveryConfig(config.application.name) >> Calls.response(ResponseBody.create(MediaType.parse("application/json"), "[]"))
    }

    when:
    def taskResult = task.execute(pipeline.stages.first())

    then:
    taskResult.context.previousState == application
  }

  void "should ignore not found errors when deleting managed delivery data"() {
    given:
    task.front50Service = Mock(Front50Service) {
      1 * get(config.application.name) >> Calls.response(new Application())
      1 * delete(config.application.name) >> Calls.response(null)
      1 * deletePermission(config.application.name) >> Calls.response(null)
      0 * _._
    }
    task.keelService = Mock(KeelService) {
      1 * deleteDeliveryConfig(config.application.name) >> Calls.response(Response.error(404, ResponseBody.create(MediaType.parse("application/json"), "not found")))
    }

    when:
    def taskResult = task.execute(pipeline.stages.first())

    then:
    taskResult.status == ExecutionStatus.SUCCEEDED
  }
}
