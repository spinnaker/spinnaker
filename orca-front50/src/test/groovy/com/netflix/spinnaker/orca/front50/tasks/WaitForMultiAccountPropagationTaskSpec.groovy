package com.netflix.spinnaker.orca.front50.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.front50.model.Front50Credential
import com.netflix.spinnaker.orca.front50.pipeline.CreateApplicationStage
import com.netflix.spinnaker.orca.front50.pipeline.DeleteApplicationStage
import com.netflix.spinnaker.orca.front50.pipeline.UpdateApplicationStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll


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


class WaitForMultiAccountPropagationTaskSpec extends Specification {
  @Subject
  def task = new WaitForMultiAccountPropagationTask(mapper: new ObjectMapper())

  def config = [
    account    : "test",
    application: [
      "name" : "application",
      "owner": "owner"
    ]
  ]

  @Unroll
  void "should be #expectedStatus if application #removed from all accounts"() {
    given:
    task.front50Service = Mock(Front50Service) {
      1 * getCredentials() >> [new Front50Credential(name: "global", global: true), new Front50Credential(name: "test")]
      1 * get("test", config.application.name) >> testApplication
      1 * get("global", config.application.name) >> globalApplication
      0 * _._
    }

    and:
    def pipeline = new Pipeline()
    pipeline.stages = [new PipelineStage(pipeline, DeleteApplicationStage.MAYO_CONFIG_TYPE)]

    when:
    def taskResult = task.execute(new PipelineStage(pipeline, "WaitForMultiAccountPropagation", config))

    then:
    taskResult.status == expectedStatus

    where:
    testApplication                   | globalApplication                      | expectedStatus
    null                              | null                                   | ExecutionStatus.SUCCEEDED
    null                              | new Application(accounts: "prod")      | ExecutionStatus.SUCCEEDED
    null                              | new Application(accounts: "prod,test") | ExecutionStatus.RUNNING
    new Application(accounts: "test") | null                                   | ExecutionStatus.RUNNING

    removed = (expectedStatus == ExecutionStatus.RUNNING) ? "not removed" : "removed"
  }

  @Unroll
  void "should be #expectedStatus if application #exists in all accounts"() {
    given:
    task.front50Service = Mock(Front50Service) {
      1 * getCredentials() >> [new Front50Credential(name: "global", global: true), new Front50Credential(name: "test")]
      1 * get("test", config.application.name) >> testApplication
      1 * get("global", config.application.name) >> globalApplication
      0 * _._
    }

    and:
    def pipeline = new Pipeline()
    pipeline.stages = [new PipelineStage(pipeline, stageType)]

    when:
    def taskResult = task.execute(new PipelineStage(pipeline, "WaitForMultiAccountPropagation", config))

    then:
    taskResult.status == expectedStatus

    where:
    stageType                               | testApplication   | globalApplication                 | expectedStatus
    CreateApplicationStage.MAYO_CONFIG_TYPE | null              | null                              | ExecutionStatus.RUNNING
    CreateApplicationStage.MAYO_CONFIG_TYPE | new Application() | new Application(accounts: "prod") | ExecutionStatus.RUNNING
    CreateApplicationStage.MAYO_CONFIG_TYPE | new Application() | new Application(accounts: "test") | ExecutionStatus.SUCCEEDED
    UpdateApplicationStage.MAYO_CONFIG_TYPE | null              | null                              | ExecutionStatus.RUNNING
    UpdateApplicationStage.MAYO_CONFIG_TYPE | new Application() | new Application(accounts: "prod") | ExecutionStatus.RUNNING
    UpdateApplicationStage.MAYO_CONFIG_TYPE | new Application() | new Application(accounts: "test") | ExecutionStatus.SUCCEEDED

    exists = (expectedStatus == ExecutionStatus.RUNNING) ? "does not exist" : "exists"
  }
}
