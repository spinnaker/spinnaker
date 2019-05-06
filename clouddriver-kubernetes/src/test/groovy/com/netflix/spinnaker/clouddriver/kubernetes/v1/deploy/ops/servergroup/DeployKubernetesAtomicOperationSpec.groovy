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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.ops.servergroup

import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v1.api.KubernetesApiAdaptor
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.*
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.exception.KubernetesResourceNotFoundException
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.kubernetes.client.dsl.internal.ReplicationControllerOperationsImpl
import io.fabric8.kubernetes.client.dsl.internal.ServiceOperationsImpl
import spock.lang.Specification
import spock.lang.Subject

class DeployKubernetesAtomicOperationSpec extends Specification {
  private static final NAMESPACE = "default"
  private static final APPLICATION = "app"
  private static final SERVER_GROUP_NAME = "serverGroup"
  private static final STACK = "stack"
  private static final DETAILS = "details"
  private static final SEQUENCE = "v000"
  private static final TARGET_SIZE = 3
  private static final REGISTRY = 'index.docker.io'
  private static final TAG = 'latest'
  private static final REPOSITORY = 'library/nginx'
  private static final LOAD_BALANCER_NAMES = ["lb1", "lb2"]
  private static final CONTAINER_NAMES = ["c1", "c2"]
  private static final REQUEST_CPU = ["100m", null]
  private static final REQUEST_MEMORY = ["100Mi", "200Mi"]
  private static final LIMIT_CPU = ["120m", "200m"]
  private static final LIMIT_MEMORY = ["200Mi", "300Mi"]
  private static final DOCKER_REGISTRY_ACCOUNTS = [new LinkedDockerRegistryConfiguration(accountName: "my-docker-account")]
  private static final PORT = 80
  private static final PERIOD_SECONDS = 20
  private static final SOURCE_CAPACITY = 10
  private static final SOURCE = new Source(account: "account", region: "region", namespace: NAMESPACE, serverGroupName: SERVER_GROUP_NAME, useSourceCapacity: true)

  def spectatorRegistry
  def apiMock
  def credentials
  def namedAccountCredentials
  def dockerRegistry
  def dockerRegistries
  def containers
  def description
  def replicationControllerOperationsMock
  def replicationControllerListMock
  def replicationControllerMock
  def replicationControllerSpecMock
  def replicaSetMock

  def serviceOperationsMock
  def serviceListMock
  def serviceMock
  def serviceSpecMock
  def servicePortMock
  def metadataMock

  def intOrStringMock

  def clusterName
  def replicationControllerName
  def imageId

  def accountCredentialsRepositoryMock

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    spectatorRegistry = new DefaultRegistry()
    apiMock = Mock(KubernetesApiAdaptor)
    replicationControllerOperationsMock = Mock(ReplicationControllerOperationsImpl)
    replicationControllerListMock = Mock(ReplicationControllerList)
    replicaSetMock = Mock(ReplicaSet)
    serviceOperationsMock = Mock(ServiceOperationsImpl)
    serviceListMock = Mock(ServiceList)
    serviceMock = Mock(Service)
    serviceSpecMock = Mock(ServiceSpec)
    servicePortMock = Mock(ServicePort)
    metadataMock = Mock(ObjectMeta)
    intOrStringMock = Mock(IntOrString)
    accountCredentialsRepositoryMock = Mock(AccountCredentialsRepository)
    replicationControllerMock = Mock(ReplicationController)
    replicationControllerSpecMock = Mock(ReplicationControllerSpec)

    def livenessProbe = new KubernetesProbe([
      periodSeconds: PERIOD_SECONDS,
      handler: new KubernetesHandler([
        type: KubernetesHandlerType.TCP,
        tcpSocketAction: new KubernetesTcpSocketAction([
          port: PORT
        ])
      ])
    ])

    imageId = KubernetesUtil.getImageId(REGISTRY, REPOSITORY, TAG, null)
    def imageDescription = KubernetesUtil.buildImageDescription(imageId)

    DOCKER_REGISTRY_ACCOUNTS.forEach({ account ->
      def dockerRegistryAccountMock = Mock(DockerRegistryNamedAccountCredentials)
      accountCredentialsRepositoryMock.getOne(account.accountName) >> dockerRegistryAccountMock
      dockerRegistryAccountMock.getAccountName() >> account
      apiMock.getSecret(NAMESPACE, account.accountName) >> null
      apiMock.createSecret(NAMESPACE, _) >> null
    })

    dockerRegistry = Mock(LinkedDockerRegistryConfiguration)
    dockerRegistries = [dockerRegistry]
    credentials = new KubernetesV1Credentials(apiMock, [NAMESPACE], [], DOCKER_REGISTRY_ACCOUNTS, accountCredentialsRepositoryMock,)
    namedAccountCredentials = Mock(KubernetesNamedAccountCredentials) {
      getCredentials() >> credentials
    }
    clusterName = KubernetesUtil.combineAppStackDetail(APPLICATION, STACK, DETAILS)
    replicationControllerName = String.format("%s-v%s", clusterName, SEQUENCE)

    replicationControllerSpecMock.replicas >> SOURCE_CAPACITY
    replicationControllerMock.spec >> replicationControllerSpecMock
    apiMock.getReplicationController(NAMESPACE, SERVER_GROUP_NAME) >> replicationControllerMock

    containers = []
    CONTAINER_NAMES.eachWithIndex { name, idx ->
      def requests = new KubernetesResourceDescription(cpu: REQUEST_CPU[idx], memory: REQUEST_MEMORY[idx])
      def limits = new KubernetesResourceDescription(cpu: LIMIT_CPU[idx], memory: LIMIT_MEMORY[idx])
      containers = containers << new KubernetesContainerDescription(name: name,
        imageDescription: imageDescription,
        requests: requests,
        limits: limits,
        livenessProbe: livenessProbe
      )
    }
  }

  void "should deploy a replication controller"() {
    setup:
      description = new DeployKubernetesAtomicOperationDescription(
        application: APPLICATION,
        stack: STACK,
        freeFormDetails: DETAILS,
        targetSize: TARGET_SIZE,
        loadBalancers: LOAD_BALANCER_NAMES,
        containers: containers,
        credentials: namedAccountCredentials
      )

      @Subject def operation = new DeployKubernetesAtomicOperation(description)

    when:
      operation.operate([])

    then:

      1 * apiMock.getReplicationControllers(NAMESPACE) >> []
      1 * apiMock.getReplicaSets(NAMESPACE) >> []
      5 * replicaSetMock.getMetadata() >> metadataMock
      3 * metadataMock.getName() >> replicationControllerName
      1 * apiMock.createReplicaSet(NAMESPACE, { ReplicaSet rs ->
        LOAD_BALANCER_NAMES.each { name ->
          assert(rs.spec.template.metadata.labels[KubernetesUtil.loadBalancerKey(name)])
        }

        assert(rs.spec.replicas == TARGET_SIZE)

        CONTAINER_NAMES.eachWithIndex { name, idx ->
          assert(rs.spec.template.spec.containers[idx].name == name)
          assert(rs.spec.template.spec.containers[idx].image == imageId)
          assert(rs.spec.template.spec.containers[idx].resources.requests.cpu == REQUEST_CPU[idx])
          assert(rs.spec.template.spec.containers[idx].resources.requests.memory == REQUEST_MEMORY[idx])
          assert(rs.spec.template.spec.containers[idx].resources.limits.cpu == LIMIT_CPU[idx])
          assert(rs.spec.template.spec.containers[idx].resources.limits.memory == LIMIT_MEMORY[idx])
          assert(rs.spec.template.spec.containers[idx].livenessProbe.periodSeconds == PERIOD_SECONDS)
          assert(rs.spec.template.spec.containers[idx].livenessProbe.tcpSocket.port.intVal == PORT)
        }
      }) >> replicaSetMock
  }

  void "should error when source capacity specified but no source exists"() {
    setup:
    description = new DeployKubernetesAtomicOperationDescription(
      application: APPLICATION,
      stack: STACK,
      freeFormDetails: DETAILS,
      targetSize: TARGET_SIZE,
      loadBalancers: LOAD_BALANCER_NAMES,
      containers: containers,
      credentials: namedAccountCredentials,
      source: SOURCE
    )

    @Subject def operation = new DeployKubernetesAtomicOperation(description)

    when:
    operation.operate([])

    then:
    1 * apiMock.getReplicationController(NAMESPACE, SERVER_GROUP_NAME) >> null
    1 * apiMock.getReplicaSet(NAMESPACE, SERVER_GROUP_NAME) >> null
    thrown(KubernetesResourceNotFoundException)
  }

  void "should copy source capacity when specified"() {
    setup:
    description = new DeployKubernetesAtomicOperationDescription(
      application: APPLICATION,
      stack: STACK,
      freeFormDetails: DETAILS,
      targetSize: TARGET_SIZE,
      loadBalancers: LOAD_BALANCER_NAMES,
      containers: containers,
      credentials: namedAccountCredentials,
      source: SOURCE
    )

    @Subject def operation = new DeployKubernetesAtomicOperation(description)

    when:
    operation.operate([])

    then:

    1 * apiMock.getReplicationControllers(NAMESPACE) >> []
    1 * apiMock.getReplicaSets(NAMESPACE) >> []
    5 * replicaSetMock.getMetadata() >> metadataMock
    3 * metadataMock.getName() >> replicationControllerName
    1 * apiMock.createReplicaSet(NAMESPACE, { ReplicaSet rs ->
      assert(rs.spec.replicas == SOURCE_CAPACITY)
      true
    }) >> replicaSetMock
  }

  void "should favor sequence when specified"() {
    setup:
    description = new DeployKubernetesAtomicOperationDescription(
      application: APPLICATION,
      sequence: 10,
      targetSize: TARGET_SIZE,
      containers: containers,
      credentials: namedAccountCredentials
    )
    def replicaSetName = new KubernetesServerGroupNameResolver(NAMESPACE, null)
      .generateServerGroupName(APPLICATION, null, null, description.sequence, false)
    def operation = new DeployKubernetesAtomicOperation(description)

    when:
    operation.operate([])

    then:
    0 * apiMock.getReplicationControllers(NAMESPACE) >> []
    0 * apiMock.getReplicaSets(NAMESPACE) >> []
    5 * replicaSetMock.getMetadata() >> metadataMock
    3 * metadataMock.getName() >> replicationControllerName
    1 * apiMock.createReplicaSet(NAMESPACE, { ReplicaSet rs ->
      assert rs.metadata.name == replicaSetName
      true
    }) >> replicaSetMock
  }
}
