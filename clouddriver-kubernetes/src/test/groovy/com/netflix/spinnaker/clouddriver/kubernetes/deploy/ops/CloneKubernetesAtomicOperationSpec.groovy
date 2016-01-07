/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.CloneKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.KubernetesContainerDescription
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.PodSpec
import io.fabric8.kubernetes.api.model.PodTemplateSpec
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec
import spock.lang.Specification
import spock.lang.Subject

class CloneKubernetesAtomicOperationSpec extends Specification {
  private static final APPLICATION = "myapp"
  private static final STACK = "test"
  private static final DETAIL = "mdservice"
  private static final SEQUENCE = "v000"
  private static final TARGET_SIZE = 2
  private static final LOAD_BALANCER_NAMES = ["lb1", "lb2"]
  private static final SECURITY_GROUP_NAMES = ["sg1", "sg2"]
  private static final LABELS = ["load-balancer-lb1": true, "load-balancer-lb2": true, "security-group-sg1": true, "security-group-sg2": true]
  private static final CONTAINER_NAMES = ["c1", "c2"]
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

  def kubernetesUtilMock

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    kubernetesUtilMock = Mock(KubernetesUtil)

    containers = []
    CONTAINER_NAMES.each { name ->
      containers = containers << new KubernetesContainerDescription(name: name, image: name)
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
      securityGroups: SECURITY_GROUP_NAMES,
      containers: containers
    )

    replicationController = new ReplicationController()
    replicationControllerSpec = new ReplicationControllerSpec()
    podTemplateSpec= new PodTemplateSpec()
    objectMetadata = new ObjectMeta()
    podSpec = new PodSpec()

    objectMetadata.setLabels(LABELS)
    podTemplateSpec.setMetadata(objectMetadata)
    replicationControllerSpec.setTemplate(podTemplateSpec)

    replicationControllerContainers = []
    CONTAINER_NAMES.each { name ->
      def newContainer = new Container()
      newContainer.setName(name)
      newContainer.setImage(name)
      replicationControllerContainers.push(newContainer)
    }
    podSpec.setContainers(replicationControllerContainers)
    podTemplateSpec.setSpec(podSpec)

    replicationControllerSpec.setReplicas(TARGET_SIZE)

    replicationController.setSpec(replicationControllerSpec)

  }

  void "builds a description based on ancestor server group, overrides nothing"() {
    setup:
      def inputDescription = new CloneKubernetesAtomicOperationDescription(
        source: [serverGroupName: ANCESTOR_SERVER_GROUP_NAME]
      )

      @Subject def operation = new CloneKubernetesAtomicOperation(inputDescription)

      kubernetesUtilMock.getReplicationController(inputDescription.kubernetesCredentials, inputDescription.source.serverGroupName) >> replicationController
      operation.kubernetesUtil = kubernetesUtilMock

    when:
      def resultDescription = operation.cloneAndOverrideDescription()

    then:
      resultDescription.application == expectedResultDescription.application
      resultDescription.stack == expectedResultDescription.stack
      resultDescription.freeFormDetails == expectedResultDescription.freeFormDetails
      resultDescription.targetSize == expectedResultDescription.targetSize
      resultDescription.loadBalancers == expectedResultDescription.loadBalancers
      resultDescription.securityGroups == expectedResultDescription.securityGroups
      resultDescription.containers == expectedResultDescription.containers
  }

  void "operation builds a description based on ancestor server group, overrides everything"() {
    setup:
      def inputDescription = new CloneKubernetesAtomicOperationDescription(
        application: APPLICATION,
        stack: STACK,
        freeFormDetails: DETAIL,
        targetSize: TARGET_SIZE,
        loadBalancers: LOAD_BALANCER_NAMES,
        securityGroups: SECURITY_GROUP_NAMES,
        containers: containers,
        source: [serverGroupName: ANCESTOR_SERVER_GROUP_NAME]
      )

      @Subject def operation = new CloneKubernetesAtomicOperation(inputDescription)

      kubernetesUtilMock.getReplicationController(inputDescription.kubernetesCredentials, inputDescription.source.serverGroupName) >> replicationController
      operation.kubernetesUtil = kubernetesUtilMock

    when:
      def resultDescription = operation.cloneAndOverrideDescription()

    then:
      resultDescription.application == expectedResultDescription.application
      resultDescription.stack == expectedResultDescription.stack
      resultDescription.freeFormDetails == expectedResultDescription.freeFormDetails
      resultDescription.targetSize == expectedResultDescription.targetSize
      resultDescription.loadBalancers == expectedResultDescription.loadBalancers
      resultDescription.securityGroups == expectedResultDescription.securityGroups
      resultDescription.containers == expectedResultDescription.containers
  }

}
