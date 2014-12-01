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
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Subject

class CreateApplicationTaskSpec extends Specification {
  @Subject
  def task = new CreateApplicationTask(mapper: new ObjectMapper())

  def config = [
    account    : "test",
    application: [
      "name" : "new application",
      "owner": "appowner"
    ]
  ]


  void "should add full exception details to TaskResult when Front50 returns a status 400"() {
    given:
    def retrofitError = Mock(RetrofitError) {
      1 * getResponse() >> new Response("", 400, "", [], null)
      1 * getBodyAs(Map) >> [errors: ["#1", "#2"]]
      0 * _._
    }

    and:
    task.front50Service = Mock(Front50Service) {
      1 * create(_, _, _) >> { throw retrofitError }
      0 * _._
    }

    when:
    def taskResult = task.execute(new PipelineStage(new Pipeline(), "CreateApplication", config))

    then:
    taskResult.status == ExecutionStatus.TERMINAL
    taskResult.outputs.exception == [
      statusCode: 400, operation: "CreateApplication", details: [errors: ["#1", "#2"]]
    ]
  }

  void "should associate global application with new accounts if it previously existed"() {
    given:
    task.front50Service = Mock(Front50Service) {
      1 * create(config.account as String, config.application.name as String, _)
      1 * getCredentials() >> [new Front50Credential(name: "global", global: true)]
      1 * get("global", config.application.name) >> new Application(accounts: "prod")
      1 * update("global", {
        // assert that only accounts were changed, no other modifications should be applied.
        it.accounts == "prod,test" && it.owner != config.application.owner
      })
      0 * _._
    }

    when:
    def taskResult = task.execute(new PipelineStage(new Pipeline(), "CreateApplication", config))

    then:
    taskResult.status == ExecutionStatus.SUCCEEDED
  }

  void "should create global application if it did not previously exist"() {
    given:
    def retrofitError = Mock(RetrofitError) {
      1 * getResponse() >> new Response("", 404, "", [], null)
      0 * _._
    }

    and:
    task.front50Service = Mock(Front50Service) {
      1 * create(config.account as String, config.application.name as String, _ as Application)
      1 * getCredentials() >> [new Front50Credential(name: "global", global: true)]
      1 * get("global", config.application.name) >> { throw retrofitError }
      1 * create("global", config.application.name, { it.accounts == "test" && it.owner == config.application.owner })
      0 * _._
    }

    when:
    def taskResult = task.execute(new PipelineStage(new Pipeline(), "CreateApplication", config))

    then:
    taskResult.status == ExecutionStatus.SUCCEEDED
  }
}
