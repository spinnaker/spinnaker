/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.ops.loadbalancer

import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.kubernetes.v1.api.KubernetesApiAdaptor
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.loadbalancer.KubernetesLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.loadbalancer.KubernetesNamedServicePort
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.ServicePort
import io.fabric8.kubernetes.api.model.ServiceSpec
import spock.lang.Specification
import spock.lang.Subject

class UpsertKubernetesLoadBalancerAtomicOperationSpec extends Specification {
  final static List<String> NAMESPACES = ['default', 'prod']
  final static String NAMESPACE = 'prod'
  final static int VALID_PORT1 = 80
  final static int VALID_PORT2 = 7002
  final static int INVALID_PORT = 0
  final static String VALID_PROTOCOL1 = "TCP"
  final static String VALID_PROTOCOL2 = "UDP"
  final static String INVALID_PROTOCOL = "PCT"
  final static String VALID_NAME1 = "name"
  final static String VALID_NAME2 = "eman"
  final static String INVALID_NAME = "bad name ?"
  final static String VALID_IP1 = "127.0.0.1"
  final static Map VALID_LABELS = ["foo": "bar", "bar": "baz"]

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def apiMock
  def accountCredentialsRepositoryMock
  def credentials
  def namedAccountCredentials
  def dockerRegistry
  def dockerRegistries
  def spectatorRegistry
  KubernetesNamedServicePort namedPort1

  def setup() {
    apiMock = Mock(KubernetesApiAdaptor)

    spectatorRegistry = new DefaultRegistry()
    dockerRegistry = Mock(LinkedDockerRegistryConfiguration)
    dockerRegistries = [dockerRegistry]
    accountCredentialsRepositoryMock = Mock(AccountCredentialsRepository)
    credentials = new KubernetesV1Credentials(apiMock, NAMESPACES, [], [], accountCredentialsRepositoryMock)
    namedAccountCredentials = Mock(KubernetesNamedAccountCredentials) {
      getCredentials() >> credentials
    }

    namedPort1 = new KubernetesNamedServicePort(name: VALID_NAME1, port: VALID_PORT1, targetPort: VALID_PORT1, nodePort: VALID_PORT1, protocol: VALID_PROTOCOL1)
  }

  void "should upsert a new loadbalancer"() {
    setup:
      def description = new KubernetesLoadBalancerDescription(
          name: VALID_NAME1,
          externalIps: [VALID_IP1],
          ports: [namedPort1],
          credentials: namedAccountCredentials,
          namespace: NAMESPACE
      )
      def resultServiceMock = Mock(Service)

      @Subject def operation = new UpsertKubernetesLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      1 * apiMock.getService(NAMESPACE, VALID_NAME1) >> null
      1 * apiMock.createService(NAMESPACE, { service ->
        service.metadata.name == description.name
        service.spec.externalIPs.eachWithIndex { ip, idx ->
          ip == description.externalIps[idx]
        }
        def port = service.spec.ports[0]
        port.port == namedPort1.port
        port.name == namedPort1.name
        port.targetPort.intVal == namedPort1.targetPort
        port.nodePort == namedPort1.nodePort
        port.protocol == namedPort1.protocol
      }) >> resultServiceMock
      resultServiceMock.getMetadata() >> [name: '', namespace: '']
  }


  void "should upsert a new loadbalancer, and overwrite port data"() {
    setup:
    def description = new KubernetesLoadBalancerDescription(
        name: VALID_NAME1,
        externalIps: [VALID_IP1],
        ports: [namedPort1],
        credentials: namedAccountCredentials,
        namespace: NAMESPACE
    )
      def resultServiceMock = Mock(Service)
      def existingServiceMock = Mock(Service)
      def servicePortMock = Mock(ServicePort)
      def serviceSpecMock = Mock(ServiceSpec)

      existingServiceMock.getSpec() >> serviceSpecMock
      serviceSpecMock.getPorts() >> [servicePortMock]
      servicePortMock.getPort() >> VALID_PORT2
      servicePortMock.getName() >> VALID_NAME2
      servicePortMock.getNodePort() >> VALID_PORT2
      servicePortMock.getProtocol() >> VALID_PROTOCOL2

      @Subject def operation = new UpsertKubernetesLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      1 * apiMock.getService(NAMESPACE, VALID_NAME1) >> existingServiceMock
      1 * apiMock.replaceService(NAMESPACE, VALID_NAME1, { service ->
        service.metadata.name == description.name
        service.spec.externalIPs.eachWithIndex { ip, idx ->
          ip == description.externalIps[idx]
        }
        def port = service.spec.ports[0]
        port.port == namedPort1.port
        port.name == namedPort1.name
        port.targetPort.intVal == namedPort1.targetPort
        port.nodePort == namedPort1.nodePort
        port.protocol == namedPort1.protocol
      }) >> resultServiceMock
      resultServiceMock.getMetadata() >> [name: '', namespace: '']
  }

  void "should upsert a new loadbalancer, and insert port data"() {
    setup:
      def description = new KubernetesLoadBalancerDescription(
          name: VALID_NAME1,
          externalIps: [VALID_IP1],
          credentials: namedAccountCredentials,
          namespace: NAMESPACE
      )
      def resultServiceMock = Mock(Service)
      def existingServiceMock = Mock(Service)
      def servicePortMock = Mock(ServicePort)
      def serviceSpecMock = Mock(ServiceSpec)

      existingServiceMock.getSpec() >> serviceSpecMock
      serviceSpecMock.getPorts() >> [servicePortMock]
      servicePortMock.getPort() >> VALID_PORT2
      servicePortMock.getName() >> VALID_NAME2
      servicePortMock.getNodePort() >> VALID_PORT2
      servicePortMock.getProtocol() >> VALID_PROTOCOL2

      @Subject def operation = new UpsertKubernetesLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      1 * apiMock.getService(NAMESPACE, VALID_NAME1) >> existingServiceMock
      1 * apiMock.replaceService(NAMESPACE, VALID_NAME1, { service ->
        service.metadata.name == description.name
        service.spec.externalIPs.eachWithIndex { ip, idx ->
          ip == description.externalIps[idx]
        }
        def port = service.spec.ports[0]
        port.port == VALID_PORT2
        port.name == VALID_NAME2
        port.nodePort == VALID_PORT2
        port.protocol == VALID_PROTOCOL2
      }) >> resultServiceMock
      resultServiceMock.getMetadata() >> [name: '', namespace: '']
  }

  void "should upsert a new loadbalancer, and insert ip data"() {
    setup:
      def description = new KubernetesLoadBalancerDescription(
          name: VALID_NAME1,
          credentials: namedAccountCredentials,
          namespace: NAMESPACE
      )
      def resultServiceMock = Mock(Service)
      def existingServiceMock = Mock(Service)
      def serviceSpecMock = Mock(ServiceSpec)

      existingServiceMock.getSpec() >> serviceSpecMock
      serviceSpecMock.getExternalIPs() >> [VALID_IP1]

      @Subject def operation = new UpsertKubernetesLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      1 * apiMock.getService(NAMESPACE, VALID_NAME1) >> existingServiceMock
      1 * apiMock.replaceService(NAMESPACE, VALID_NAME1, { service ->
        service.metadata.name == description.name
        service.spec.externalIPs[0] = VALID_IP1
      }) >> resultServiceMock
      resultServiceMock.getMetadata() >> [name: '', namespace: '']
  }

  void "should upsert a new loadbalancer, and set labels"() {
    setup:
    def description = new KubernetesLoadBalancerDescription(
      name: VALID_NAME1,
      externalIps: [VALID_IP1],
      credentials: namedAccountCredentials,
      namespace: NAMESPACE,
      serviceLabels: VALID_LABELS
    )
    def resultServiceMock = Mock(Service)
    def mockMetaData = Mock(ObjectMeta)
    resultServiceMock.metadata >> mockMetaData

    @Subject def operation = new UpsertKubernetesLoadBalancerAtomicOperation(description)

    when:
    operation.operate([])

    then:
    1 * apiMock.getService(NAMESPACE, VALID_NAME1) >> null
    1 * apiMock.createService(NAMESPACE, { service ->
      service.metadata.name == description.name
      service.metadata.labels == VALID_LABELS
    }) >> resultServiceMock
  }

  void "should upsert a new loadbalancer, and copy labels over"() {
    setup:
    def description = new KubernetesLoadBalancerDescription(
        name: VALID_NAME1,
        externalIps: [VALID_IP1],
        credentials: namedAccountCredentials,
        namespace: NAMESPACE
    )
    def resultServiceMock = Mock(Service)
    def existingServiceMock = Mock(Service)
    def metadataMock = Mock(ObjectMeta)

    existingServiceMock.getMetadata() >> metadataMock
    metadataMock.getLabels() >> VALID_LABELS

    @Subject def operation = new UpsertKubernetesLoadBalancerAtomicOperation(description)

    when:
    operation.operate([])

    then:
    1 * apiMock.getService(NAMESPACE, VALID_NAME1) >> existingServiceMock
    1 * apiMock.replaceService(NAMESPACE, VALID_NAME1, { service ->
      service.metadata.name == description.name
      service.metadata.labels == VALID_LABELS
    }) >> resultServiceMock
    resultServiceMock.getMetadata() >> [name: '', namespace: '']
  }
}
