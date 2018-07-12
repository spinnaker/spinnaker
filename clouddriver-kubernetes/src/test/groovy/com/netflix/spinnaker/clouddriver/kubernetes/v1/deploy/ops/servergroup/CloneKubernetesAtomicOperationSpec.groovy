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
import com.netflix.spinnaker.clouddriver.kubernetes.v1.api.KubernetesApiAdaptor
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.CloneKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesContainerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesResourceDescription
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import io.fabric8.kubernetes.api.model.*
import spock.lang.Specification
import spock.lang.Subject

class CloneKubernetesAtomicOperationSpec extends Specification {
  private static final APPLICATION = "myapp"
  private static final STACK = "test"
  private static final DETAIL = "mdservice"
  private static final NAMESPACE1 = "default"
  private static final NAMESPACE2 = "nondefault"
  private static final SEQUENCE = "v000"
  private static final TARGET_SIZE = 2
  private static final LOAD_BALANCER_NAMES = ["lb1", "lb2"]
  private static final LABELS = ["load-balancer-lb1": true, "load-balancer-lb2": true]
  private static final CONTAINER_NAMES = ["c1", "c2"]
  private static final REGISTRY = 'index.docker.io'
  private static final TAG = 'latest'
  private static final REPOSITORY = 'library/nginx'
  private static final REQUEST_CPU = ["100m", null]
  private static final REQUEST_MEMORY = ["100Mi", "200Mi"]
  private static final LIMIT_CPU = ["120m", "200m"]
  private static final LIMIT_MEMORY = ["200Mi", "300Mi"]
  private static final ANCESTOR_SERVER_GROUP_NAME = "$APPLICATION-$STACK-$DETAIL-$SEQUENCE"

  def containers
  def ancestorNames
  def expectedResultDescription
  def replicationController
  def replicationControllerSpec
  def podTemplateSpec
  def objectMetadata
  def podSpec
  def replicationControllerContainers
  def apiMock
  def dockerRegistry
  def dockerRegistries
  def credentials
  def namedAccountCredentials
  def sourceNamedAccountCredentials
  def accountCredentialsRepositoryMock
  def spectatorRegistry

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    apiMock = Mock(KubernetesApiAdaptor)

    def imageId = KubernetesUtil.getImageId(REGISTRY, REPOSITORY, TAG, null)
    def imageDescription = KubernetesUtil.buildImageDescription(imageId)

    containers = []
    CONTAINER_NAMES.eachWithIndex { name, idx ->
      def requests = new KubernetesResourceDescription(cpu: REQUEST_CPU[idx], memory: REQUEST_MEMORY[idx])
      def limits = new KubernetesResourceDescription(cpu: LIMIT_CPU[idx], memory: LIMIT_MEMORY[idx])
      containers = containers << new KubernetesContainerDescription(name: name, imageDescription: imageDescription, requests: requests, limits: limits)
    }

    ancestorNames = [
      "app": APPLICATION,
      "stack": STACK,
      "detail": DETAIL
    ]

    expectedResultDescription = new CloneKubernetesAtomicOperationDescription(
      application: APPLICATION,
      stack: STACK,
      freeFormDetails: DETAIL,
      targetSize: TARGET_SIZE,
      loadBalancers: LOAD_BALANCER_NAMES,
      containers: containers,
      namespace: NAMESPACE1
    )

    spectatorRegistry = new DefaultRegistry()
    replicationController = new ReplicationController()
    replicationControllerSpec = new ReplicationControllerSpec()
    podTemplateSpec= new PodTemplateSpec()
    objectMetadata = new ObjectMeta()
    podSpec = new PodSpec()
    accountCredentialsRepositoryMock = Mock(AccountCredentialsRepository)
    dockerRegistry = Mock(LinkedDockerRegistryConfiguration)
    dockerRegistries = [dockerRegistry]
    credentials = new KubernetesV1Credentials(apiMock, [], [], [], accountCredentialsRepositoryMock)
    namedAccountCredentials = new KubernetesNamedAccountCredentials.Builder()
        .name("name")
        .dockerRegistries(dockerRegistries)
        .spectatorRegistry(spectatorRegistry)
        .credentials(credentials)
        .build()

    sourceNamedAccountCredentials = new KubernetesNamedAccountCredentials.Builder()
        .name("name")
        .dockerRegistries(dockerRegistries)
        .spectatorRegistry(spectatorRegistry)
        .credentials(credentials)
        .build()

    objectMetadata.setLabels(LABELS)
    podTemplateSpec.setMetadata(objectMetadata)
    replicationControllerSpec.setTemplate(podTemplateSpec)

    replicationControllerContainers = []
    containers = []
    def l = CONTAINER_NAMES.size()
    CONTAINER_NAMES.eachWithIndex { name, idx ->
      def container = new Container()
      container.setName(name)
      container.setImage(name)

      def requestsBuilder = new ResourceRequirementsBuilder()
      // Rotate indices to ensure they are overwritten by request
      requestsBuilder = requestsBuilder.addToLimits([cpu: new Quantity(LIMIT_CPU[l - idx]), memory: new Quantity(LIMIT_MEMORY[l - idx])])
      requestsBuilder = requestsBuilder.addToRequests([cpu: new Quantity(REQUEST_CPU[l - idx]), memory: new Quantity(REQUEST_MEMORY[l - idx])])
      container.setResources(requestsBuilder.build())
      replicationControllerContainers = replicationControllerContainers << container

      def requests = new KubernetesResourceDescription(cpu: REQUEST_CPU[l - idx], memory: REQUEST_MEMORY[l - idx])
      def limits = new KubernetesResourceDescription(cpu: LIMIT_CPU[l - idx], memory: LIMIT_MEMORY[l - idx])
      containers = containers << new KubernetesContainerDescription(name: name, imageDescription: imageDescription, requests: requests, limits: limits)
    }

    podSpec.setContainers(replicationControllerContainers)
    podTemplateSpec.setSpec(podSpec)
    replicationControllerSpec.setReplicas(TARGET_SIZE)
    replicationController.setSpec(replicationControllerSpec)
  }

  void "builds a description based on ancestor server group, overrides nothing"() {
    setup:
      def inputDescription = new CloneKubernetesAtomicOperationDescription(
        source: [serverGroupName: ANCESTOR_SERVER_GROUP_NAME, namespace: NAMESPACE1],
        credentials: namedAccountCredentials,
        sourceCredentials: sourceNamedAccountCredentials
      )

      @Subject def operation = new CloneKubernetesAtomicOperation(inputDescription)

      apiMock.getReplicationController(NAMESPACE1, inputDescription.source.serverGroupName) >> replicationController

    when:
      def resultDescription = operation.cloneAndOverrideDescription()

    then:
      resultDescription.application == expectedResultDescription.application
      resultDescription.stack == expectedResultDescription.stack
      resultDescription.freeFormDetails == expectedResultDescription.freeFormDetails
      resultDescription.targetSize == expectedResultDescription.targetSize
      resultDescription.loadBalancers == expectedResultDescription.loadBalancers
      resultDescription.namespace == expectedResultDescription.namespace
      resultDescription.containers.eachWithIndex { c, idx ->
        c.imageDescription.registry == expectedResultDescription.containers[idx].imageDescription.registry
        c.imageDescription.tag == expectedResultDescription.containers[idx].imageDescription.tag
        c.imageDescription.repository == expectedResultDescription.containers[idx].imageDescription.repository
        c.name == expectedResultDescription.containers[idx].name
        c.requests?.cpu == expectedResultDescription.containers[idx].requests?.cpu
        c.requests?.memory == expectedResultDescription.containers[idx].requests?.memory
        c.limits?.cpu == expectedResultDescription.containers[idx].limits?.cpu
        c.limits?.memory == expectedResultDescription.containers[idx].limits?.memory
      }
  }

  void "operation builds a description based on ancestor server group, overrides everything"() {
    setup:
      def inputDescription = new CloneKubernetesAtomicOperationDescription(
        application: APPLICATION,
        stack: STACK,
        namespace: NAMESPACE1,
        freeFormDetails: DETAIL,
        targetSize: TARGET_SIZE,
        loadBalancers: LOAD_BALANCER_NAMES,
        containers: containers,
        credentials: namedAccountCredentials,
        sourceCredentials: sourceNamedAccountCredentials,
        source: [serverGroupName: ANCESTOR_SERVER_GROUP_NAME, namespace: NAMESPACE2]
      )

      @Subject def operation = new CloneKubernetesAtomicOperation(inputDescription)

      apiMock.getReplicationController(NAMESPACE2, inputDescription.source.serverGroupName) >> replicationController

    when:
      def resultDescription = operation.cloneAndOverrideDescription()

    then:
      resultDescription.application == expectedResultDescription.application
      resultDescription.stack == expectedResultDescription.stack
      resultDescription.freeFormDetails == expectedResultDescription.freeFormDetails
      resultDescription.targetSize == expectedResultDescription.targetSize
      resultDescription.loadBalancers == expectedResultDescription.loadBalancers
      resultDescription.namespace == expectedResultDescription.namespace
      resultDescription.containers.eachWithIndex { c, idx ->
        c.imageDescription.registry == expectedResultDescription.containers[idx].imageDescription.registry
        c.imageDescription.tag == expectedResultDescription.containers[idx].imageDescription.tag
        c.imageDescription.repository == expectedResultDescription.containers[idx].imageDescription.repository
        c.name == expectedResultDescription.containers[idx].name
        c.requests.cpu == expectedResultDescription.containers[idx].requests.cpu
        c.requests.memory == expectedResultDescription.containers[idx].requests.memory
        c.limits.cpu == expectedResultDescription.containers[idx].limits.cpu
        c.limits.memory == expectedResultDescription.containers[idx].limits.memory
      }
  }
}
