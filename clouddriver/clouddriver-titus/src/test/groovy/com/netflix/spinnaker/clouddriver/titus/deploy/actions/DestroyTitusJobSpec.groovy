/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.deploy.actions

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.client.model.TerminateJobRequest
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.description.DestroyTitusJobDescription
import spock.lang.Specification
import spock.lang.Subject
import com.netflix.spinnaker.clouddriver.saga.models.Saga

class DestroyTitusJobSpec extends Specification {
  def titusClient = Mock(TitusClient)
  def titusClientProvider = Stub(TitusClientProvider) {
    getTitusClient(_, _) >> titusClient
  }

  def accountCredentialsProvider = Mock(AccountCredentialsProvider)
  def testCredentials = Mock(NetflixTitusCredentials)

  @Subject
  DestroyTitusJob operation = new DestroyTitusJob(accountCredentialsProvider, titusClientProvider)

  void 'should terminate the titus job successfully'() {
    given:
    def saga = new Saga("test-saga", "1")
    def command = DestroyTitusJob.DestroyTitusJobCommand.builder().description(
      new DestroyTitusJobDescription(
        jobId: '1234', region: 'us-east-1', account: 'test', user: 'testUser'
      )
    ).build()

    when:
    operation.apply(command, saga)

    then:
    1 * accountCredentialsProvider.getCredentials('test') >> testCredentials
    1 * titusClient.terminateJob({ TerminateJobRequest terminateJobRequest ->
      assert terminateJobRequest.jobId == '1234'
      assert terminateJobRequest.user == 'testUser'
    })
    0 * _
  }
}
