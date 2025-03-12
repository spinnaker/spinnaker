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
import com.google.common.hash.Hashing
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
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
  boolean useSharedManagedServiceAccounts = false

  @Subject
  SaveServiceAccountTask task = new SaveServiceAccountTask(Optional.of(fiatStatus), Optional.of(front50Service),
  Optional.of(fiatPermissionEvaluator), useSharedManagedServiceAccounts)

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

  def "should do nothing if roles are present and didn't change compared to the service user"() {
    given:
    def serviceAccount = 'pipeline-id@managed-service-account'
    def pipeline = [
      application   : 'orca',
      name          : 'my pipeline',
      id            : 'pipeline-id',
      serviceAccount: serviceAccount,
      stages        : [],
      roles         : ['foo', 'bar']
    ]
    def stage = stage {
      context = [
        pipeline: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes)
      ]
    }

    when:
    def result = task.execute(stage)

    then:
    1 * fiatPermissionEvaluator.getPermission(serviceAccount) >> {
      new UserPermission().addResources([new Role('foo'), new Role('bar')]).view
    }
    0 * front50Service.saveServiceAccount(_)
    result.status == ExecutionStatus.SUCCEEDED
    result.context == ImmutableMap.of('pipeline.serviceAccount', serviceAccount)
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

    def user = "abc@somedomain.io"
    def message = ""
    def exceptionMessage = "User '"+ user +"' is not authorized with all roles for pipeline"

    when:
    stage.getExecution().setTrigger(new DefaultTrigger('manual', null, user))

    try {
      task.execute(stage)
    } catch (Exception e) {
      message = e.message
    }

    then:
    1 * fiatPermissionEvaluator.getPermission(user) >> {
      new UserPermission().addResources([new Role('foo')]).view
    }

    0 * front50Service.saveServiceAccount(_)

    message == exceptionMessage
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

  def "should generate a pipeline id if not already present"() {
    given:
    def pipeline = [
      application: 'orca',
      name: 'My pipeline',
      stages: [],
      roles: ['foo']
    ]
    def stage = stage {
      context = [
        pipeline: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes)
      ]
    }
    def uuid = null
    def expectedServiceAccountName = null

    when:
    stage.getExecution().setTrigger(new DefaultTrigger('manual', null, 'abc@somedomain.io'))
    def result = task.execute(stage)

    then:
    1 * fiatPermissionEvaluator.getPermission('abc@somedomain.io') >> {
      new UserPermission().addResources([new Role('foo')]).view
    }

    1 * front50Service.saveServiceAccount({ it.name != null }) >> { ServiceAccount serviceAccount ->
      uuid = serviceAccount.name - "@managed-service-account"
      expectedServiceAccountName = serviceAccount.name
      new Response('http://front50', 200, 'OK', [], null)
    }

    result.status == ExecutionStatus.SUCCEEDED
    result.context == ImmutableMap.of(
      'pipeline.id', uuid,
      'pipeline.serviceAccount', expectedServiceAccountName)
  }

  def "generateSvcAcctName should re-use or generate the appropriate service account name"() {
    given:
    def pipeline = [
        id: 'pipeline-id',
        application: 'orca',
        name: 'My pipeline',
        stages: [],
        roles: ['foo'],
    ]
    if (existingServiceAccountName) {
      pipeline.serviceAccount = existingServiceAccountName
    }
    def stage = stage {
      context = [
          pipeline: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes)
      ]
    }
    def expectedServiceAccount = new ServiceAccount(name: expectedServiceAccountName, memberOf: ['foo'])

    task = new SaveServiceAccountTask(Optional.of(fiatStatus), Optional.of(front50Service),
        Optional.of(fiatPermissionEvaluator), useSharedManagedServiceAccountsEnabled)

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
    result.context == ImmutableMap.of('pipeline.serviceAccount', expectedServiceAccountName)

    where:
    existingServiceAccountName                 | expectedServiceAccountName                 | useSharedManagedServiceAccountsEnabled
    'pipeline-id@managed-service-account'      | 'pipeline-id@managed-service-account'      | false
    'some-acct@shared-managed-service-account' | 'pipeline-id@managed-service-account'      | false
    null                                       | 'pipeline-id@managed-service-account'      | false
    'some-acct@some-org'                       | 'some-acct@some-org'                       | false
    'pipeline-id@managed-service-account'      | 'pipeline-id@managed-service-account'      | true
    'some-acct@shared-managed-service-account' | 'some-acct@shared-managed-service-account' | true
    null                                       | generateSha256StringFromRolesString("foo") | true
    'some-acct@some-org'                       | 'some-acct@some-org'                       | true
  }

  private String generateSha256StringFromRolesString(String rolesString) {
    Hashing.sha256().hashBytes(rolesString.getBytes()).toString() + "@shared-managed-service-account"
  }
}
