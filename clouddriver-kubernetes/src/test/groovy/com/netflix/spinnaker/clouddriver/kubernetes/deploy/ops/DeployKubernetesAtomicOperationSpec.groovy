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

package com.netflix.spinnaker.clouddriver.kubernetes.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.DeployKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.KubernetesContainerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.IntOrString
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.ReplicationControllerList
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.ServiceList
import io.fabric8.kubernetes.api.model.ServicePort
import io.fabric8.kubernetes.api.model.ServiceSpec
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.internal.ReplicationControllerOperationsImpl
import io.fabric8.kubernetes.client.dsl.internal.ServiceOperationsImpl
import spock.lang.Subject
import spock.lang.Specification

class DeployKubernetesAtomicOperationSpec extends Specification {
  private static final NAMESPACE = "default"
  private static final APPLICATION = "app"
  private static final STACK = "stack"
  private static final DETAILS = "details"
  private static final SEQUENCE = "v000"
  private static final TARGET_SIZE = 3
  private static final LOAD_BALANCER_NAMES = ["lb1", "lb2"]
  private static final SECURITY_GROUP_NAMES = ["sg1", "sg2", "sg3"]
  private static final CONTAINER_NAMES = ["c1", "c2"]
  private static final TARGET_PORT = 80

  def kubernetesClientMock
  def kubernetesUtilMock
  def credentials
  def containers
  def description
  def replicationControllerOperationsMock
  def replicationControllerListMock
  def replicationControllerMock

  def serviceOperationsMock
  def serviceListMock
  def serviceMock
  def serviceSpecMock
  def servicePortMock
  def metadataMock

  def intOrStringMock

  def clusterName
  def replicationControllerName

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    kubernetesClientMock = Mock(KubernetesClient)
    kubernetesUtilMock = Mock(KubernetesUtil)
    replicationControllerOperationsMock = Mock(ReplicationControllerOperationsImpl)
    replicationControllerListMock = Mock(ReplicationControllerList)
    replicationControllerMock = Mock(ReplicationController)
    serviceOperationsMock = Mock(ServiceOperationsImpl)
    serviceListMock = Mock(ServiceList)
    serviceMock = Mock(Service)
    serviceSpecMock = Mock(ServiceSpec)
    servicePortMock = Mock(ServicePort)
    metadataMock = Mock(ObjectMeta)
    intOrStringMock = Mock(IntOrString)

    credentials = new KubernetesCredentials(NAMESPACE, kubernetesClientMock)
    clusterName = KubernetesUtil.combineAppStackDetail(APPLICATION, STACK, DETAILS)
    replicationControllerName = String.format("%s-v%s", clusterName, SEQUENCE)

    containers = []
    CONTAINER_NAMES.each { name ->
      containers = containers << new KubernetesContainerDescription(name: name, image: name)
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
        securityGroups: SECURITY_GROUP_NAMES,
        containers: containers,
        kubernetesCredentials: credentials
      )

      @Subject def operation = new DeployKubernetesAtomicOperation(description)
      operation.kubernetesUtil = kubernetesUtilMock

    when:
      operation.operate([])

    then:
      1 * kubernetesUtilMock.getNextSequence(_, _) >> SEQUENCE
      SECURITY_GROUP_NAMES.each { name ->
        1 * kubernetesUtilMock.getSecurityGroup(credentials, name) >> serviceMock
        1 * serviceMock.getSpec() >> serviceSpecMock
        1 * serviceSpecMock.getPorts() >> [servicePortMock]
        1 * servicePortMock.getTargetPort() >> intOrStringMock
        1 * intOrStringMock.getIntVal() >> TARGET_PORT
      }

    then:
      1 * kubernetesClientMock.replicationControllers() >> replicationControllerOperationsMock
      1 * replicationControllerOperationsMock.inNamespace(NAMESPACE) >> replicationControllerOperationsMock
      1 * replicationControllerOperationsMock.create({ rc ->
        LOAD_BALANCER_NAMES.each { name ->
          assert(rc.metadata.labels[KubernetesUtil.loadBalancerKey(name)])
        }

        SECURITY_GROUP_NAMES.each { name ->
          assert(rc.metadata.labels[KubernetesUtil.securityGroupKey(name)])
        }

        LOAD_BALANCER_NAMES.each { name ->
          assert(rc.spec.template.metadata.labels[KubernetesUtil.loadBalancerKey(name)])
        }

        SECURITY_GROUP_NAMES.each { name ->
          assert(rc.spec.template.metadata.labels[KubernetesUtil.securityGroupKey(name)])
        }

        assert(rc.spec[0].replicas == TARGET_SIZE)

        CONTAINER_NAMES.eachWithIndex { name, idx ->
          assert(rc.spec.template.spec.containers[0][idx].name == name)
          assert(rc.spec.template.spec.containers[0][idx].image == name)
          assert(rc.spec.template.spec.containers[0][idx].ports[0].containerPort == TARGET_PORT)
        }
      }) >> replicationControllerMock
      2 * replicationControllerMock.getMetadata() >> metadataMock
      2 * metadataMock.getName() >> replicationControllerName
  }

}
