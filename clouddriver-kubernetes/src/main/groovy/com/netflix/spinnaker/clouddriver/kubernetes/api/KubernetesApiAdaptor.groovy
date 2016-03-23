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
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.*
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.api.model.extensions.Ingress
import io.fabric8.kubernetes.client.KubernetesClient

import java.awt.image.ReplicateScaleFilter
import java.util.concurrent.TimeUnit

class KubernetesApiAdaptor {
  KubernetesClient client

  static final int RETRY_COUNT = 20
  static final long RETRY_MAX_WAIT_MILLIS = TimeUnit.SECONDS.toMillis(10)
  static final long RETRY_INITIAL_WAIT_MILLIS = 100

  KubernetesApiAdaptor(KubernetesClient client) {
    if (!client) {
      throw new IllegalArgumentException("Client may not be null.")
    }
    this.client = client
  }

  /*
   * Exponential backoff strategy for waiting on changes to replication controllers
   */
  Boolean blockUntilReplicationControllerConsistent(ReplicationController desired) {
    def current = getReplicationController(desired.metadata.namespace, desired.metadata.name)

    def wait = RETRY_INITIAL_WAIT_MILLIS
    def attempts = 0
    while (current.status.observedGeneration < desired.status.observedGeneration) {
      attempts += 1
      if (attempts > RETRY_COUNT) {
        return false
      }

      sleep(wait)
      wait = [wait * 2, RETRY_MAX_WAIT_MILLIS].min()

      current = getReplicationController(desired.metadata.namespace, desired.metadata.name)
    }

    return true
  }

  Ingress createIngress(String namespace, Ingress ingress) {
    client.extensions().ingress().inNamespace(namespace).create(ingress)
  }

  Ingress replaceIngress(String namespace, String name, Ingress ingress) {
    client.extensions().ingress().inNamespace(namespace).withName(name).replace(ingress)
  }

  Ingress getIngress(String namespace, String name) {
    client.extensions().ingress().inNamespace(namespace).withName(name).get()
  }

  boolean deleteIngress(String namespace, String name) {
    client.extensions().ingress().inNamespace(namespace).withName(name).delete()
  }

  List<Ingress> getIngresses(String namespace) {
    client.extensions().ingress().inNamespace(namespace).list().items
  }

  List<ReplicationController> getReplicationControllers(String namespace) {
    client.replicationControllers().inNamespace(namespace).list().items
  }

  List<Pod> getPods(String namespace, String replicationControllerName) {
    client.pods().inNamespace(namespace).withLabel(KubernetesUtil.REPLICATION_CONTROLLER_LABEL, replicationControllerName).list().items
  }

  Pod getPod(String namespace, String name) {
    client.pods().inNamespace(namespace).withName(name).get()
  }

  boolean deletePod(String namespace, String name) {
    client.pods().inNamespace(namespace).withName(name).delete()
  }

  List<Pod> getPods(String namespace) {
    client.pods().inNamespace(namespace).list().items
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

  boolean hardDestroyReplicationController(String namespace, String name) {
    client.replicationControllers().inNamespace(namespace).withName(name).delete()
  }

  void togglePodLabels(String namespace, String name, List<String> keys, String value) {
    def edit = client.pods().inNamespace(namespace).withName(name).edit().editMetadata()

    keys.each {
      edit.removeFromLabels(it)
      edit.addToLabels(it, value)
    }

    edit.endMetadata().done()
  }

  ReplicationController toggleReplicationControllerSpecLabels(String namespace, String name, List<String> keys, String value) {
    def edit = client.replicationControllers().inNamespace(namespace).withName(name).cascading(false).edit().editSpec().editTemplate().editMetadata()

    keys.each {
      edit.removeFromLabels(it)
      edit.addToLabels(it, value)
    }

    edit.endMetadata().endTemplate().endSpec().done()
  }

  Service getService(String namespace, String service) {
    client.services().inNamespace(namespace).withName(service).get()
  }

  Service createService(String namespace, Service service) {
    client.services().inNamespace(namespace).create(service)
  }

  boolean deleteService(String namespace, String name) {
    client.services().inNamespace(namespace).withName(name).delete()
  }

  List<Service> getServices(String namespace) {
    client.services().inNamespace(namespace).list().items
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
    if (!container) {
      return null
    }

    def containerDescription = new KubernetesContainerDescription()
    containerDescription.name = container.name
    containerDescription.imageDescription = KubernetesUtil.buildImageDescription(container.image)

    container.resources?.with {
      containerDescription.limits = limits?.cpu?.amount || limits?.memory?.amount ?
        new KubernetesResourceDescription(
          cpu: limits?.cpu?.amount,
          memory: limits?.memory?.amount
        ) : null

      containerDescription.requests = requests?.cpu?.amount || requests?.memory?.amount ?
        new KubernetesResourceDescription(
           cpu: requests?.cpu?.amount,
           memory: requests?.memory?.amount
        ) : null
    }

    containerDescription.ports = container.ports?.collect {
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

    containerDescription.livenessProbe = fromProbe(container?.livenessProbe)
    containerDescription.readinessProbe = fromProbe(container?.readinessProbe)

    containerDescription.envVars = container?.env?.collect { envVar ->
      new KubernetesEnvVar(name: envVar.name, value: envVar.value)
    }

    containerDescription.volumeMounts = container?.volumeMounts?.collect { volumeMount ->
      new KubernetesVolumeMount(name: volumeMount.name, readOnly: volumeMount.readOnly, mountPath: volumeMount.mountPath)
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

    deployDescription.volumeSources = replicationController?.spec?.template?.spec?.volumes?.collect { volume ->
      def res = new KubernetesVolumeSource(name: volume.name)

      if (volume.emptyDir) {
        res.type = KubernetesVolumeSourceType.EMPTYDIR
        def medium = volume.emptyDir.medium
        def mediumType

        if (medium == "Memory") {
          mediumType = KubernetesStorageMediumType.MEMORY
        } else {
          mediumType = KubernetesStorageMediumType.DEFAULT
        }

        res.emptyDir = new KubernetesEmptyDir(medium: mediumType)
      } else if (volume.hostPath) {
        res.type = KubernetesVolumeSourceType.HOSTPATH
        res.hostPath = new KubernetesHostPath(path: volume.hostPath.path)
      } else if (volume.persistentVolumeClaim) {
        res.type = KubernetesVolumeSourceType.PERSISTENTVOLUMECLAIM
        res.persistentVolumeClaim = new KubernetesPersistentVolumeClaim(claimName: volume.persistentVolumeClaim.claimName,
                                                                        readOnly: volume.persistentVolumeClaim.readOnly)
      } else {
        res.type = KubernetesVolumeSourceType.UNSUPPORTED
      }

      return res
    } ?: []

    deployDescription.containers = replicationController?.spec?.template?.spec?.containers?.collect {
      fromContainer(it)
    }

    return deployDescription
  }

  static KubernetesProbe fromProbe(Probe probe) {
    if (!probe) {
      return null
    }

    def kubernetesProbe = new KubernetesProbe()
    kubernetesProbe.failureThreshold = probe.failureThreshold ?: 0
    kubernetesProbe.successThreshold = probe.successThreshold ?: 0
    kubernetesProbe.timeoutSeconds = probe.timeoutSeconds ?: 0
    kubernetesProbe.periodSeconds = probe.periodSeconds ?: 0
    kubernetesProbe.initialDelaySeconds = probe.initialDelaySeconds ?: 0
    kubernetesProbe.handler = new KubernetesHandler()

    if (probe.exec) {
      kubernetesProbe.handler.execAction = fromExecAction(probe.exec)
      kubernetesProbe.handler.type = KubernetesHandlerType.EXEC
    }

    if (probe.tcpSocket) {
      kubernetesProbe.handler.tcpSocketAction = fromTcpSocketAction(probe.tcpSocket)
      kubernetesProbe.handler.type = KubernetesHandlerType.TCP
    }

    if (probe.httpGet) {
      kubernetesProbe.handler.httpGetAction = fromHttpGetAction(probe.httpGet)
      kubernetesProbe.handler.type = KubernetesHandlerType.HTTP
    }

    return kubernetesProbe
  }

  static KubernetesExecAction fromExecAction(ExecAction exec) {
    if (!exec) {
      return null
    }

    def kubernetesExecAction = new KubernetesExecAction()
    kubernetesExecAction.commands = exec.command
    return kubernetesExecAction
  }

  static KubernetesTcpSocketAction fromTcpSocketAction(TCPSocketAction tcpSocket) {
    if (!tcpSocket) {
      return null
    }

    def kubernetesTcpSocketAction = new KubernetesTcpSocketAction()
    kubernetesTcpSocketAction.port = tcpSocket.port?.intVal
    return kubernetesTcpSocketAction
  }

  static KubernetesHttpGetAction fromHttpGetAction(HTTPGetAction httpGet) {
    if (!httpGet) {
      return null
    }

    def kubernetesHttpGetAction = new KubernetesHttpGetAction()
    kubernetesHttpGetAction.host = httpGet.host
    kubernetesHttpGetAction.path = httpGet.path
    kubernetesHttpGetAction.port = httpGet.port?.intVal
    kubernetesHttpGetAction.uriScheme = httpGet.scheme
    return kubernetesHttpGetAction
  }
}
