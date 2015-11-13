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

package com.netflix.spinnaker.kato.titan.deploy.handlers
import com.netflix.spinnaker.clouddriver.titan.TitanClientProvider
import com.netflix.spinnaker.clouddriver.titan.credentials.NetflixTitanCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.DeploymentResult
import com.netflix.spinnaker.kato.titan.deploy.description.TitanDeployDescription
import com.netflix.titanclient.TitanClient
import com.netflix.titanclient.TitanRegion
import com.netflix.titanclient.model.SubmitJobRequest
import spock.lang.Specification
import spock.lang.Subject

class TitanDeployHandlerSpec extends Specification {

  TitanClient titanClient = Mock(TitanClient)

  TitanClientProvider titanClientProvider = Stub(TitanClientProvider) {
    getTitanClient(_, _) >> titanClient
  }

  NetflixTitanCredentials testCredentials = new NetflixTitanCredentials(
    'test', 'test', 'test', [new TitanRegion('us-east-1', 'test', 'http://foo', 'http://bar')]
  )

  @Subject
  TitanDeployHandler titanDeployHandler = new TitanDeployHandler(titanClientProvider)

  def setup() {
    Task task = Mock(Task)
    TaskRepository.threadLocalTask.set(task)
  }

  void 'TitanDeployHandler should submit a Titan job successfully'() {
    given:
    TitanDeployDescription titanDeployDescription = new TitanDeployDescription(
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
    titanClient.findJobsByApplication(_) >> []

    when:
    DeploymentResult deploymentResult = titanDeployHandler.handle(titanDeployDescription, [])

    then:
    noExceptionThrown()
    deploymentResult != null
    deploymentResult.serverGroupNames && deploymentResult.serverGroupNames.contains('us-east-1:api-test-v000')
    deploymentResult.serverGroupNameByRegion && deploymentResult.serverGroupNameByRegion['us-east-1'] == 'api-test-v000'
    1 * titanClient.submitJob({
      it.jobName == 'api-test-v000' &&
        it.dockerImageName == 'api.server' &&
        it.dockerImageVersion == 'master-201506020033-trusty-7366606' &&
        it.instances == titanDeployDescription.capacity.desired &&
        it.cpu == titanDeployDescription.resources.cpu &&
        it.memory == titanDeployDescription.resources.memory &&
        it.disk == titanDeployDescription.resources.disk &&
        it.ports == titanDeployDescription.resources.ports &&
        it.env == titanDeployDescription.env &&
        it.application == titanDeployDescription.application &&
        it.allocateIpAddress ==  titanDeployDescription.resources.allocateIpAddress
    } as SubmitJobRequest)
  }

}
