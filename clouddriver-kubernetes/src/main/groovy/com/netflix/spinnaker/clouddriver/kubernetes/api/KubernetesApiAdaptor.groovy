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

package com.netflix.spinnaker.clouddriver.kubernetes.api

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.DeployKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesContainerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesContainerPort
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesResourceDescription
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.client.KubernetesClient

class KubernetesApiAdaptor {
  KubernetesClient client

  KubernetesApiAdaptor(KubernetesClient client) {
    if (!client) {
      throw new IllegalArgumentException("Client may not be null.")
    }
    this.client = client
  }

  List<ReplicationController> getReplicationControllers(String namespace) {
    client.replicationControllers().inNamespace(namespace).list().items
  }

  List<Pod> getPods(String namespace, String replicationControllerName) {
    client.pods().inNamespace(namespace).withLabel(KubernetesUtil.REPLICATION_CONTROLLER_LABEL, replicationControllerName).list().items
  }

  ReplicationController getReplicationController(String namespace, String serverGroupName) {
    client.replicationControllers().inNamespace(namespace).withName(serverGroupName).get()
  }

  ReplicationController createReplicationController(String namespace, ReplicationController replicationController) {
    client.replicationControllers().inNamespace(namespace).create(replicationController)
  }

  ReplicationController resizeReplicationController(String namespace, String name, int size) {
    client.replicationControllers().inNamespace(namespace).withName(name).scale(size)
  }

  void removePodLabels(String namespace, String name, List<String> keys) {
    def edit = client.pods().inNamespace(namespace).withName(name).edit().editMetadata()

    keys.each {
      edit.removeFromLabels(it)
    }

    edit.endMetadata().done()
  }

  void addPodLabels(String namespace, String name, List<String> keys, String value) {
    def edit = client.pods().inNamespace(namespace).withName(name).edit().editMetadata()

    keys.each {
      edit.addToLabels(it, value)
    }

    edit.endMetadata().done()
  }

  void removeReplicationControllerSpecLabels(String namespace, String name, List<String> keys) {
    def edit = client.replicationControllers().inNamespace(namespace).withName(name).edit().editSpec().editTemplate().editMetadata()

    keys.each {
      edit.removeFromLabels(it)
    }

    edit = edit.endMetadata().endTemplate()

    keys.each {
      edit.removeFromSelector(it)
    }

    edit.endSpec().done()
  }

  void addReplicationControllerSpecLabels(String namespace, String name, List<String> keys, String value) {
    def edit = client.replicationControllers().inNamespace(namespace).withName(name).edit().editSpec().editTemplate().editMetadata()

    keys.each {
      edit.addToLabels(it, value)
    }

    edit = edit.endMetadata().endTemplate()

    keys.each {
      edit.addToSelector(it, value)
    }

    edit.endSpec().done()
  }

  Service getService(String namespace, String service) {
    client.services().inNamespace(namespace).withName(service).get()
  }

  Service createService(String namespace, Service service) {
    client.services().inNamespace(namespace).create(service)
  }

  List<Service> getServices(String namespace) {
    client.services().inNamespace(namespace).list().items
  }

  Service getSecurityGroup(String namespace, String securityGroup) {
    getService(namespace, securityGroup)
  }

  Service replaceService(String namespace, String name, Service service) {
    client.services().inNamespace(namespace).withName(name).replace(service)
  }

  Secret getSecret(String namespace, String secret) {
    client.secrets().inNamespace(namespace).withName(secret).get()
  }

  Boolean deleteSecret(String namespace, String secret) {
    client.secrets().inNamespace(namespace).withName(secret).delete()
  }

  Secret createSecret(String namespace, Secret secret) {
    client.secrets().inNamespace(namespace).create(secret)
  }

  Namespace getNamespace(String namespace) {
    client.namespaces().withName(namespace).get()
  }

  Namespace createNamespace(Namespace namespace) {
    client.namespaces().create(namespace)
  }

  static KubernetesContainerDescription fromContainer(Container container) {
    def containerDescription = new KubernetesContainerDescription()
    containerDescription.name = container?.name
    containerDescription.imageDescription = KubernetesUtil.buildImageDescription(container?.image)

    container?.resources?.with {
      containerDescription.limits = new  KubernetesResourceDescription(
        cpu: limits?.cpu?.amount,
        memory: limits?.memory?.amount
      )

      containerDescription.requests = new  KubernetesResourceDescription(
        cpu: requests?.cpu?.amount,
        memory: requests?.memory?.amount
      )
    }

    containerDescription.ports = container?.ports?.collect {
      def port = new KubernetesContainerPort()
      port.hostIp = it?.hostIP
      if (it?.hostPort) {
        port.hostPort = it?.hostPort?.intValue()
      }
      if (it?.containerPort) {
        port.containerPort = it?.containerPort?.intValue()
      }
      port.name = it?.name
      port.protocol = it?.protocol

      return port
    }

    return containerDescription
  }

  static DeployKubernetesAtomicOperationDescription fromReplicationController(ReplicationController replicationController) {
    def deployDescription = new DeployKubernetesAtomicOperationDescription()
    def parsedName = Names.parseName(replicationController?.metadata?.name)

    deployDescription.application = parsedName?.app
    deployDescription.stack = parsedName?.stack
    deployDescription.freeFormDetails = parsedName?.detail
    deployDescription.loadBalancers = KubernetesUtil?.getDescriptionLoadBalancers(replicationController)
    deployDescription.namespace = replicationController?.metadata?.namespace
    deployDescription.targetSize = replicationController?.spec?.replicas
    deployDescription.securityGroups = []

    deployDescription.containers = replicationController?.spec?.template?.spec?.containers?.collect {
      fromContainer(it)
    }

    return deployDescription
  }
}
