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

package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.loadbalancer

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.DeleteDcosLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor.DcosDeploymentMonitor
import com.netflix.spinnaker.clouddriver.dcos.exception.DcosOperationException
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.App
import spock.lang.Subject

class DeleteDcosLoadBalancerAtomicOperationSpec extends BaseSpecification {
  private static final LOAD_BALANCER_NAME = "external"

  DcosAccountCredentials credentials
  def dcosClientProviderMock
  def dcosClientMock
  def dcosDeploymentMonitorMock
  def appMock
  def taskMock

  def setup() {
    taskMock = Mock(Task)
    TaskRepository.threadLocalTask.set(taskMock)

    appMock = Mock(App)
    credentials = defaultCredentialsBuilder().build()
    dcosDeploymentMonitorMock = Mock(DcosDeploymentMonitor)

    dcosClientMock = Mock(DCOS)
    dcosClientProviderMock = Stub(DcosClientProvider) {
      getDcosClient(credentials, DEFAULT_REGION) >> dcosClientMock
    }
  }

  void "DeleteDcosLoadBalancerAtomicOperation should delete the load balancer for the given name if it exists"() {
    setup:
    appMock.id >> "/${DEFAULT_ACCOUNT}/${LOAD_BALANCER_NAME}"

    def description = new DeleteDcosLoadBalancerAtomicOperationDescription(
      credentials: credentials,
      dcosCluster: DEFAULT_REGION,
      loadBalancerName: LOAD_BALANCER_NAME
    )

    @Subject def operation = new DeleteDcosLoadBalancerAtomicOperation(dcosClientProviderMock,
      dcosDeploymentMonitorMock, description)

    when:
    operation.operate([])

    then:
    1 * dcosClientMock.maybeApp("/${DEFAULT_ACCOUNT}/${LOAD_BALANCER_NAME}") >> Optional.of(appMock)
    1 * dcosClientMock.deleteApp(appMock.id)
    1 * dcosDeploymentMonitorMock.waitForAppDestroy(dcosClientMock, appMock.id, null, taskMock, "DESTROY_LOAD_BALANCER")
  }

  void "DeleteDcosLoadBalancerAtomicOperation should throw an exception when the given load balancer does not exist"() {
    setup:
    appMock.id >> "/${DEFAULT_ACCOUNT}/${LOAD_BALANCER_NAME}"

    def description = new DeleteDcosLoadBalancerAtomicOperationDescription(
      credentials: credentials,
      dcosCluster: DEFAULT_REGION,
      loadBalancerName: LOAD_BALANCER_NAME
    )

    @Subject def operation = new DeleteDcosLoadBalancerAtomicOperation(dcosClientProviderMock,
      dcosDeploymentMonitorMock, description)

    when:
    operation.operate([])

    then:
    1 * dcosClientMock.maybeApp("/${DEFAULT_ACCOUNT}/${LOAD_BALANCER_NAME}") >> Optional.empty()
    thrown(DcosOperationException)
  }
}
