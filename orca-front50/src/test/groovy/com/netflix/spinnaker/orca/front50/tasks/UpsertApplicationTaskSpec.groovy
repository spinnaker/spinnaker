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
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class UpsertApplicationTaskSpec extends Specification {
  @Subject
  def task = new UpsertApplicationTask(mapper: new ObjectMapper())

  @Shared
  def config = [
    account    : "test",
    application: [
      "name" : "application",
      "owner": "owner"
    ]
  ]

  @Shared
  def globalAccount = "global"

  void "should not create an application in a global registry if no global credentials are available"() {
    given:
    task.front50Service = Mock(Front50Service) {
      3 * get(config.account, config.application.name) >> null
      1 * getCredentials() >> []
      1 * create(config.account, config.application.name, {
        it.properties == new Application(config.application).properties
      })
      0 * _._
    }

    when:
    def result = task.execute(new PipelineStage(new Pipeline(), "UpsertApplication", config))

    then:
    result.status == ExecutionStatus.SUCCEEDED
  }

  void "should create an application in global and non-global registries"() {
    given:
    task.front50Service = Mock(Front50Service) {
      1 * get(globalAccount, config.application.name) >> null
      3 * get(config.account, config.application.name) >> null
      1 * getCredentials() >> [new Front50Credential(name: globalAccount, global: true)]
      1 * create(globalAccount, config.application.name, {
        it.properties == new Application(config.application + [accounts: config.account]).properties
      })
      1 * create(config.account, config.application.name, {
        it.properties == new Application(config.application).properties
      })
      0 * _._
    }

    when:
    def result = task.execute(new PipelineStage(new Pipeline(), "UpsertApplication", config))

    then:
    result.status == ExecutionStatus.SUCCEEDED
  }

  void "should update existing application (global) and create new application (non-global)"() {
    given:
    task.front50Service = Mock(Front50Service) {
      1 * get(globalAccount, config.application.name) >> existingGlobalApplication
      1 * get(existingGlobalApplicationAccount, config.application.name) >> existingGlobalApplication
      3 * get(config.account, config.application.name) >> null
      1 * getCredentials() >> [new Front50Credential(name: globalAccount, global: true)]
      1 * update(globalAccount, {
        // assert that the global application is updated w/ new application attributes and merged accounts
        it.properties == new Application(
          config.application + [accounts: [existingGlobalApplicationAccount, config.account].join(",")]
        ).properties
      })
      1 * update(existingGlobalApplicationAccount, {
        // assert that other per-account registries associated with the global application are also updated
        it.properties == new Application(config.application + [accounts: null]).properties
      })
      1 * create(config.account, config.application.name, {
        // assert that the new application properties override whatever existed in the global registry,
        // 'accounts' should never be propagated to non-global applications
        it.properties == (existingGlobalApplication.properties + config.application + [accounts: null])
      })
      0 * _._
    }

    when:
    def result = task.execute(new PipelineStage(new Pipeline(), "UpsertApplication", config))

    then:
    result.status == ExecutionStatus.SUCCEEDED

    where:
    existingGlobalApplicationAccount = "prod"
    existingGlobalApplication = new Application(
      name: "application", owner: "owner", description: "description", accounts: existingGlobalApplicationAccount
    )
  }

  void "should update existing application in global and non-global registries"() {
    given:
    task.front50Service = Mock(Front50Service) {
      1 * get(globalAccount, config.application.name) >> new Application(accounts: "prod")
      1 * get("prod", config.application.name) >> null
      3 * get(config.account, config.application.name) >> new Application()
      1 * getCredentials() >> [new Front50Credential(name: globalAccount, global: true)]
      1 * update(globalAccount, {
        it.properties == new Application(config.application + [accounts: "prod,test"]).properties
      })
      1 * update(config.account, {
        it.properties == new Application(config.application).properties
      })
      0 * _._
    }

    when:
    def result = task.execute(new PipelineStage(new Pipeline(), "UpsertApplication", config))

    then:
    result.status == ExecutionStatus.SUCCEEDED
  }

  void "should merge properties from source to target application"() {
    given:
    def source = new Application(email: "source@netflix.com", description: "sourceDescription", owner: "sourceOwner")
    def target = new Application(owner: "targetOwner")

    when:
    UpsertApplicationTask.mergeApplicationProperties(source, target)

    then:
    target.email == source.email
    target.description == source.description
    target.owner == "targetOwner"
  }

  @Unroll
  void "should keep track of previous and new state during #operation"() {
    given:
    Application application = new Application(config.application)
    task.front50Service = Mock(Front50Service) {
      3 * get(config.account, config.application.name) >> initialState >> initialState >> application
      1 * getCredentials() >> []
      1 * "${operation}"(*_)
      0 * _._
    }

    when:
    def result = task.execute(new PipelineStage(new Pipeline(), "UpsertApplication", config))

    then:
    result.stageOutputs.previousState == (initialState ?: [:])
    result.stageOutputs.newState == application

    where:
    initialState      | operation
    null              | 'create'
    new Application() | 'update'
  }
}
