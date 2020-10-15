/*
 * Copyright 2019 Netflix, Inc.
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

import com.netflix.spinnaker.clouddriver.saga.models.Saga
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.description.ServiceJobProcessesRequest
import spock.lang.Specification
import spock.lang.Subject

class UpsertTitusJobProcessesSpec extends Specification {
  def testCredentials = Mock(NetflixTitusCredentials)

  def accountCredentialsProvider = Mock(AccountCredentialsProvider)
  def titusClientProvider = Mock(TitusClientProvider)
  def titusClient = Mock(TitusClient)

  @Subject
  UpsertTitusJobProcesses upsertTitusJobProcesses = new UpsertTitusJobProcesses(
    accountCredentialsProvider, titusClientProvider
  )

  void 'should update scaling processes'() {
    given:
    def saga = new Saga("my-saga", "my-id")
    def request = new ServiceJobProcessesRequest(credentials: testCredentials, region: "us-east-1")
    def command = UpsertTitusJobProcesses.UpsertTitusJobProcessesCommand.builder().description(
      request
    ).build()

    when:
    upsertTitusJobProcesses.apply(command, saga)

    then:
    1 * accountCredentialsProvider.getCredentials(_) >> { return testCredentials }
    1 * titusClientProvider.getTitusClient(testCredentials, "us-east-1") >> { return titusClient }

    1 * titusClient.updateScalingProcesses(request)
    0 * _
  }
}
