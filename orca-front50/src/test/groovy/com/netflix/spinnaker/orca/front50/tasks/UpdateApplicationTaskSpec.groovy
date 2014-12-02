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


package com.netflix.spinnaker.orca.front50.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.front50.model.Front50Credential
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

class UpdateApplicationTaskSpec extends Specification {
  @Subject
  def task = new UpdateApplicationTask(mapper: new ObjectMapper())

  def config = [
    account    : "test",
    application: [
      "name" : "application",
      "owner" : "owner"
    ]
  ]

  void "should update global application if it is associated with account"() {
    given:
    task.front50Service = Mock(Front50Service) {
      1 * update(config.account, {
        new Application(config.application).properties.entrySet() == it.properties.entrySet()
      })
      1 * getCredentials() >> [new Front50Credential(name: "global", global: true)]
      1 * get("global", config.application.name) >> new Application(accounts: "prod,test")
      1 * update("global", {
        new Application(config.application + [accounts: "prod,test"]).properties.entrySet() == it.properties.entrySet()
      })
      0 * _._
    }

    when:
    def taskResult = task.execute(new PipelineStage(new Pipeline(), "UpdateApplication", config))

    then:
    taskResult.status == ExecutionStatus.SUCCEEDED
  }

  void "should NOT update global application if it was not previously associated with account"() {
    given:
    task.front50Service = Mock(Front50Service) {
      1 * update(config.account, {
        new Application(config.application).properties.entrySet() == it.properties.entrySet()
      })
      1 * getCredentials() >> [new Front50Credential(name: "global", global: true)]
      1 * get("global", config.application.name) >> new Application(accounts: "prod")
      0 * update("global", _)
      0 * _._
    }

    when:
    def taskResult = task.execute(new PipelineStage(new Pipeline(), "UpdateApplication", config))

    then:
    taskResult.status == ExecutionStatus.SUCCEEDED
  }
}
