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

class DeleteApplicationTaskSpec extends Specification {
  @Subject
  def task = new DeleteApplicationTask(mapper: new ObjectMapper())

  def config = [
    account    : "test",
    application: [
      "name" : "application"
    ]
  ]

  void "should delete global application if it was only associated with a single account"() {
    given:
    task.front50Service = Mock(Front50Service) {
      1 * get("test", config.application.name) >> new Application(accounts: "test")
      1 * delete(config.account, config.application.name)
      1 * getCredentials() >> [new Front50Credential(name: "global", global: true)]
      1 * get("global", config.application.name) >> new Application(accounts: "test")
      1 * delete("global", config.application.name)
      0 * _._
    }

    when:
    def taskResult = task.execute(new PipelineStage(new Pipeline(), "DeleteApplication", config))

    then:
    taskResult.status == ExecutionStatus.SUCCEEDED
  }

  void "should de-associate global application if it was associated with multiple accounts"() {
    given:
    task.front50Service = Mock(Front50Service) {
      1 * get("test", config.application.name) >> new Application(accounts: "test")
      1 * delete(config.account, config.application.name)
      1 * getCredentials() >> [new Front50Credential(name: "global", global: true)]
      1 * get("global", config.application.name) >> new Application(accounts: "prod,test")
      1 * update("global", { it.accounts == "prod" })
      0 * _._
    }

    when:
    def taskResult = task.execute(new PipelineStage(new Pipeline(), "DeleteApplication", config))

    then:
    taskResult.status == ExecutionStatus.SUCCEEDED
  }
}
