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

import com.netflix.spinnaker.orca.SimpleTaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.mort.MortService
import retrofit.client.Response
import retrofit.mime.TypedInput
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class WaitForUpsertedSecurityGroupTaskSpec extends Specification {

  @Subject task = new WaitForUpsertedSecurityGroupTask()

  @Unroll
  void "should return #taskStatus status when group was #old and is now '#current' per Mort"() {
    given:
    def groupName = 'group'
    def account = 'account'
    def region = 'region'
    def response = GroovyMock(Response)
    response.getStatus() >> 200
    response.getBody() >> {
      def input = Mock(TypedInput)
      input.in() >> new ByteArrayInputStream(current.bytes)
      input
    }
    task.mortService = Stub(MortService) {
      getSecurityGroup(account, 'aws', groupName, region) >> response
    }

    and:
    def context = new SimpleTaskContext()
    context."upsert.account" = account
    context."upsert.region" = region
    context."upsert.name" = groupName
    if (old) {
      context."upsert.pre.response" = old
    }

    expect:
    task.execute(context).status == taskStatus

    where:
    old         | current     || taskStatus
    null        | ''          || TaskResult.Status.RUNNING
    null        | 'changed'   || TaskResult.Status.SUCCEEDED
    'original'  | 'original'  || TaskResult.Status.RUNNING
    'original'  | 'changed'   || TaskResult.Status.SUCCEEDED
  }

}
