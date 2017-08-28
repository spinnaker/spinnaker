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
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class DeleteApplicationTaskSpec extends Specification {
  @Subject
  def task = new DeleteApplicationTask(mapper: new ObjectMapper())

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
      1 * get(config.application.name) >> new Application()
      1 * delete(config.application.name)
      1 * deletePermission(config.application.name)
      0 * _._
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
      1 * get(config.application.name) >> application
      1 * delete(config.application.name)
      1 * deletePermission(config.application.name)
      0 * _._
    }

    when:
    def taskResult = task.execute(pipeline.stages.first())

    then:
    taskResult.context.previousState == application
  }
}
