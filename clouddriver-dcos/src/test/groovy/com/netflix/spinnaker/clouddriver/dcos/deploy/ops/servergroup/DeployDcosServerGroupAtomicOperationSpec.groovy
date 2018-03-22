/*
 * Copyright 2018 Cerner Corporation
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

package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DeployDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.mapper.DeployDcosServerGroupDescriptionToAppMapper
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.*
import spock.lang.Subject

class DeployDcosServerGroupAtomicOperationSpec extends BaseSpecification {
  private static final APPLICATION_NAME = DcosSpinnakerAppId.parseVerbose("${DEFAULT_ACCOUNT}/${DEFAULT_REGION}/api-test-detail-v000".toString()).get()

  DCOS mockDcosClient = Mock(DCOS)
  DeployDcosServerGroupDescriptionToAppMapper mockDcosDescriptionToAppMapper = Mock(DeployDcosServerGroupDescriptionToAppMapper)

  DcosAccountCredentials testCredentials = defaultCredentialsBuilder().build()

  DcosClientProvider mockDcosClientProvider = Stub(DcosClientProvider) {
    getDcosClient(testCredentials, DEFAULT_REGION) >> mockDcosClient
  }

  DeployDcosServerGroupDescription description = new DeployDcosServerGroupDescription(
    application: APPLICATION_NAME.serverGroupName.app, region: APPLICATION_NAME.safeGroup,
    credentials: testCredentials, dcosCluster: DEFAULT_REGION, stack: APPLICATION_NAME.serverGroupName.stack,
    freeFormDetails: APPLICATION_NAME.serverGroupName.detail, desiredCapacity: 1, cpus: 0.25, mem: 128, disk: 0, gpus: 0,
          docker: new DeployDcosServerGroupDescription.Docker(forcePullImage: false, privileged: false,
                  network: "BRIDGE",
                  image: new DeployDcosServerGroupDescription.Image(imageId: "test")))

  App application = new App(id: APPLICATION_NAME.toString(), instances: 1, cpus: 0.25, mem: 128, disk: 0, gpus: 0,
    container: new Container(docker: new Docker(image: "test", forcePullImage: false, privileged: false, portMappings: [], network: "BRIDGE")),
    versionInfo: new AppVersionInfo(lastConfigChangeAt: null)
  )

  @Subject
  AtomicOperation atomicOperation = new DeployDcosServerGroupAtomicOperation(mockDcosClientProvider, mockDcosDescriptionToAppMapper, description)

  def setup() {
    Task task = Mock(Task)
    TaskRepository.threadLocalTask.set(task)
  }

  void 'DeployDcosServerGroupAtomicOperation should deploy the DCOS service successfully'() {
    given:
    mockDcosClient.maybeApps(_) >> Optional.of(new GetAppNamespaceResponse(apps: []))
    mockDcosDescriptionToAppMapper.map(_, _) >> application

    when:
    DeploymentResult deploymentResult = atomicOperation.operate([])

    then:
    noExceptionThrown()
    deploymentResult != null
    deploymentResult.serverGroupNames && deploymentResult.serverGroupNames.contains(String.format("%s:%s", "${DEFAULT_REGION}", APPLICATION_NAME.serverGroupName.group))
    deploymentResult.serverGroupNameByRegion && deploymentResult.serverGroupNameByRegion.get("${DEFAULT_REGION}".toString()) == APPLICATION_NAME.serverGroupName.group
  }
}
