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
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion
import com.netflix.spinnaker.clouddriver.titus.client.model.SubmitJobRequest
import spock.lang.Specification
import spock.lang.Subject

class TitusDeployHandlerSpec extends Specification {

  TitusClient titusClient = Mock(TitusClient)

  TitusClientProvider titusClientProvider = Stub(TitusClientProvider) {
    getTitusClient(_, _) >> titusClient
  }

  NetflixTitusCredentials testCredentials = new NetflixTitusCredentials(
    'test', 'test', 'test', [new TitusRegion('us-east-1', 'test', 'http://foo')]
  )

  @Subject
  TitusDeployHandler titusDeployHandler = new TitusDeployHandler(titusClientProvider)

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
      account: 'test',
      region: 'us-east-1',
      subnet: 'vpc0',
      imageId: 'api.server:master-201506020033-trusty-7366606',
      capacity: [desired: 1],
      resources: [cpu: 2, memory: 4, disk: 4000, ports: [7001], allocateIpAddress: true],
      env: ['netflix.environment': 'test'],
      credentials: testCredentials
    )
    titusClient.findJobsByApplication(_) >> []

    when:
    DeploymentResult deploymentResult = titusDeployHandler.handle(titusDeployDescription, [])

    then:
    noExceptionThrown()
    deploymentResult != null
    deploymentResult.serverGroupNames && deploymentResult.serverGroupNames.contains('us-east-1:api-test-v000')
    deploymentResult.serverGroupNameByRegion && deploymentResult.serverGroupNameByRegion['us-east-1'] == 'api-test-v000'
    1 * titusClient.submitJob({
      it.jobName == 'api-test-v000' &&
        it.dockerImageName == 'api.server' &&
        it.dockerImageVersion == 'master-201506020033-trusty-7366606' &&
        it.instances == titusDeployDescription.capacity.desired &&
        it.cpu == titusDeployDescription.resources.cpu &&
        it.memory == titusDeployDescription.resources.memory &&
        it.disk == titusDeployDescription.resources.disk &&
        it.ports == titusDeployDescription.resources.ports &&
        it.env == titusDeployDescription.env &&
        it.application == titusDeployDescription.application &&
        it.allocateIpAddress ==  titusDeployDescription.resources.allocateIpAddress
    } as SubmitJobRequest)
  }

}
