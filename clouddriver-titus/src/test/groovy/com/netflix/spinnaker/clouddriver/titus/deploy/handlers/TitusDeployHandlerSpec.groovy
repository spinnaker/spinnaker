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

package com.netflix.spinnaker.clouddriver.titus.deploy.handlers

import com.netflix.spinnaker.config.AwsConfiguration
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion
import com.netflix.spinnaker.clouddriver.titus.client.model.SubmitJobRequest
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription
import spock.lang.Specification
import spock.lang.Subject

class TitusDeployHandlerSpec extends Specification {
  NetflixTitusCredentials netflixTitusCredentials = Mock(NetflixTitusCredentials)
  def accountCredentialsProvider = Mock(AccountCredentialsProvider) {
    getCredentials("test") >> {
      return netflixTitusCredentials
    }
  }

  def accountCredentialsRepository = Mock(AccountCredentialsRepository) {
    getOne("test") >> {
      return netflixTitusCredentials
    }
  }

  TitusClient titusClient = Mock(TitusClient)

  TitusClientProvider titusClientProvider = Stub(TitusClientProvider) {
    getTitusClient(_, _) >> titusClient
  }

  NetflixTitusCredentials testCredentials = new NetflixTitusCredentials(
    'test', 'test', 'test', [new TitusRegion('us-east-1', 'test', 'http://foo', false, false, "blah", "blah", 7104, [])], 'test', 'test', 'test', 'test', false, '', 'mainvpc', [], "", false, false, false
  )

  @Subject
  TitusDeployHandler titusDeployHandler = new TitusDeployHandler(titusClientProvider, accountCredentialsRepository)

  def setup() {
    Task task = Mock(Task)
    TaskRepository.threadLocalTask.set(task)
  }

  void 'TitusDeployHandler should submit a Titus job successfully'() {
    given:
    TitusDeployDescription titusDeployDescription = new TitusDeployDescription(
      application: 'api',
      stack: 'test',
      freeFormDetails: '',
      region: 'us-east-1',
      subnet: 'vpc0',
      imageId: 'api.server:master-201506020033-trusty-7366606',
      capacity: [desired: 1, min: 1, max: 2],
      resources: [cpu: 2, memory: 4, disk: 4000, ports: [7001], allocateIpAddress: true],
      env: ['netflix.environment': 'test'],
      credentials: testCredentials,
      interestingHealthProviderNames: [
        "Titus",
        "Discovery"
      ],
      containerAttributes: [
        'k1': 'value1',
        'k2': 123
      ]
    )
    titusClient.findJobsByApplication(_) >> []

    titusDeployHandler.deployDefaults = [
      addAppGroupToServerGroup: false
    ] as AwsConfiguration.DeployDefaults

    titusDeployHandler.accountCredentialsProvider = accountCredentialsProvider

    when:
    DeploymentResult deploymentResult = titusDeployHandler.handle(titusDeployDescription, [])

    then:
    noExceptionThrown()
    deploymentResult != null
    deploymentResult.serverGroupNames && deploymentResult.serverGroupNames.contains('us-east-1:api-test-v000')
    deploymentResult.serverGroupNameByRegion && deploymentResult.serverGroupNameByRegion['us-east-1'] == 'api-test-v000'
    accountCredentialsProvider.getCredentials(_) >> netflixTitusCredentials
    1 * titusClient.submitJob({
      it.jobName == 'api-test-v000' &&
        it.dockerImageName == 'api.server' &&
        it.dockerImageVersion == 'master-201506020033-trusty-7366606' &&
        it.instancesMin == titusDeployDescription.capacity.min &&
        it.instancesMax == titusDeployDescription.capacity.max &&
        it.instancesDesired == titusDeployDescription.capacity.desired &&
        it.cpu == titusDeployDescription.resources.cpu &&
        it.memory == titusDeployDescription.resources.memory &&
        it.disk == titusDeployDescription.resources.disk &&
        it.ports == titusDeployDescription.resources.ports &&
        it.env == titusDeployDescription.env &&
        it.application == titusDeployDescription.application &&
        it.allocateIpAddress == titusDeployDescription.resources.allocateIpAddress &&
        it.labels.get("interestingHealthProviderNames") == "Titus,Discovery"
    } as SubmitJobRequest) >> "123456"
  }

}
