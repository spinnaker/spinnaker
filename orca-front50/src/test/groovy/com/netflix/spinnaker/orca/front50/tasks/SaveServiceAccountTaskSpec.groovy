/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.front50.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class SaveServiceAccountTaskSpec extends Specification {
  Front50Service front50Service = Mock(Front50Service)
  FiatPermissionEvaluator fiatPermissionEvaluator = Mock(FiatPermissionEvaluator)
  FiatStatus fiatStatus = Mock() {
    _ * isEnabled() >> true
  }
  ObjectMapper objectMapper = new ObjectMapper()

  @Subject
  SaveServiceAccountTask task = new SaveServiceAccountTask(fiatStatus: fiatStatus,
    front50Service: front50Service, fiatPermissionEvaluator: fiatPermissionEvaluator)

  def "should do nothing if no pipeline roles present"() {
    given:
    def pipeline = [
      application: 'orca',
      name: 'my pipeline',
      stages: []
    ]
    def stage = stage {
      context = [
        pipeline: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes)
      ]
    }

    when:
    def result = task.execute(stage)

    then:
    0 * front50Service.saveServiceAccount(_)
    result.status == ExecutionStatus.SUCCEEDED
  }

  def "should create a serviceAccount with correct roles"() {
    given:
    def pipeline = [
      application: 'orca',
      id: 'pipeline-id',
      name: 'My pipeline',
      stages: [],
      roles: ['foo']
    ]
    def stage = stage {
      context = [
        pipeline: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes)
      ]
    }

    def expectedServiceAccount = new ServiceAccount(name: 'pipeline-id@managed-service-account', memberOf: ['foo'])

    when:
    stage.getExecution().setTrigger(new DefaultTrigger('manual', null, 'abc@somedomain.io'))
    def result = task.execute(stage)

    then:
    1 * fiatPermissionEvaluator.getPermission('abc@somedomain.io') >> {
      new UserPermission().addResources([new Role('foo')]).view
    }

    1 * front50Service.saveServiceAccount(expectedServiceAccount) >> {
      new Response('http://front50', 200, 'OK', [], null)
    }

    result.status == ExecutionStatus.SUCCEEDED
    result.context == ImmutableMap.of('pipeline.serviceAccount', expectedServiceAccount.name)
  }

  def "should not allow adding roles that a user does not have"() {
    given:
    def pipeline = [
      application: 'orca',
      id: 'pipeline-id',
      name: 'my pipeline',
      stages: [],
      roles: ['foo', 'bar']
    ]
    def stage = stage {
      context = [
        pipeline: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes)
      ]
    }

    when:
    stage.getExecution().setTrigger(new DefaultTrigger('manual', null, 'abc@somedomain.io'))
    def result = task.execute(stage)

    then:
    1 * fiatPermissionEvaluator.getPermission('abc@somedomain.io') >> {
      new UserPermission().addResources([new Role('foo')]).view
    }

    0 * front50Service.saveServiceAccount(_)

    result.status == ExecutionStatus.TERMINAL
    result.context == ImmutableMap.of()
  }

  def "should allow an admin to save pipelines"() {
    given:
    def pipeline = [
      application: 'orca',
      id: 'pipeline-id',
      name: 'My pipeline',
      stages: [],
      roles: ['foo']
    ]
    def stage = stage {
      context = [
        pipeline: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes)
      ]
    }

    def expectedServiceAccount = new ServiceAccount(name: 'pipeline-id@managed-service-account', memberOf: ['foo'])

    when:
    stage.getExecution().setTrigger(new DefaultTrigger('manual', null, 'abc@somedomain.io'))
    def result = task.execute(stage)

    then:
    1 * fiatPermissionEvaluator.getPermission('abc@somedomain.io') >> {
      new UserPermission().setAdmin(true).view
    }

    1 * front50Service.saveServiceAccount(expectedServiceAccount) >> {
      new Response('http://front50', 200, 'OK', [], null)
    }

    result.status == ExecutionStatus.SUCCEEDED
    result.context == ImmutableMap.of('pipeline.serviceAccount', expectedServiceAccount.name)
  }

}
