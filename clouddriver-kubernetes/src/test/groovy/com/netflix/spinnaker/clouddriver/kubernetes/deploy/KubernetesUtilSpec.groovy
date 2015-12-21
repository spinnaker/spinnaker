/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.deploy

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.DeployKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.ReplicationControllerList
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.ServiceList
import io.fabric8.kubernetes.api.model.ServiceSpec
import io.fabric8.kubernetes.client.dsl.internal.ServiceOperationsImpl
import io.fabric8.kubernetes.client.dsl.internal.ReplicationControllerOperationsImpl
import io.fabric8.kubernetes.client.KubernetesClient;
import spock.lang.Subject
import spock.lang.Specification

class KubernetesUtilSpec extends Specification {
  private static final NAMESPACE = "default"
  private static final SERVICE = "service"

  def kubernetesClientMock
  def credentials
  def replicationControllerOperationsMock
  def replicationControllerListMock
  def replicationControllerMock

  def serviceOperationsMock
  def serviceListMock
  def serviceMock
  def serviceSpecMock

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    kubernetesClientMock = Mock(KubernetesClient)
    credentials = new KubernetesCredentials(NAMESPACE, kubernetesClientMock)
    replicationControllerOperationsMock = Mock(ReplicationControllerOperationsImpl)
    replicationControllerListMock = Mock(ReplicationControllerList)
    replicationControllerMock = Mock(ReplicationController)

    serviceOperationsMock = Mock(ServiceOperationsImpl)
    serviceListMock = Mock(ServiceList)
    serviceMock = Mock(Service)
    serviceSpecMock = Mock(ServiceSpec)
  }

  void "list replication controllers"() {
    when:
      KubernetesUtil.getReplicationControllers(credentials)

    then:
      1 * kubernetesClientMock.replicationControllers() >> replicationControllerOperationsMock
      1 * replicationControllerOperationsMock.inNamespace(NAMESPACE) >> replicationControllerOperationsMock
      1 * replicationControllerOperationsMock.list() >> replicationControllerListMock
  }

  void "get service"() {
    when:
      KubernetesUtil.getService(credentials, SERVICE)

    then:
      1 * kubernetesClientMock.services() >> serviceOperationsMock
      1 * serviceOperationsMock.inNamespace(NAMESPACE) >> serviceOperationsMock
      1 * serviceOperationsMock.withName(SERVICE) >> serviceOperationsMock
      1 * serviceOperationsMock.get() >> serviceMock
  }

  void "get security group"() {
    when:
      KubernetesUtil.getSecurityGroup(credentials, SERVICE)

    then:
      1 * kubernetesClientMock.services() >> serviceOperationsMock
      1 * serviceOperationsMock.inNamespace(NAMESPACE) >> serviceOperationsMock
      1 * serviceOperationsMock.withName(SERVICE) >> serviceOperationsMock
      1 * serviceOperationsMock.get() >> serviceMock
  }

  void "get load balancer"() {
    when:
      KubernetesUtil.getLoadBalancer(credentials, SERVICE)

    then:
      1 * kubernetesClientMock.services() >> serviceOperationsMock
      1 * serviceOperationsMock.inNamespace(NAMESPACE) >> serviceOperationsMock
      1 * serviceOperationsMock.withName(SERVICE) >> serviceOperationsMock
      1 * serviceOperationsMock.get() >> serviceMock
  }
}
