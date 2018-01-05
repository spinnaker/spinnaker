/*
 * Copyright 2017 Cisco, Inc.
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
package com.netflix.spinnaker.clouddriver.kubernetes.v1.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.DeployKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesAwsElasticBlockStoreVolumeSource
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesCapabilities
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesConfigMapVolumeSource
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesContainerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesContainerPort
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesEmptyDir
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesHandler
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesHandlerType
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesHostPath
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesKeyToPath
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesLifecycle
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesPersistentVolumeClaim
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesProbe
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesPullPolicy
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesResourceDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesSeLinuxOptions
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesSecretVolumeSource
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesSecurityContext
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesStorageMediumType
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesVolumeMount
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesVolumeSource
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesVolumeSourceType
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesExecAction
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesHttpGetAction
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KeyValuePair
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesTcpSocketAction
import groovy.util.logging.Slf4j
import io.kubernetes.client.models.*
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.autoscaler.KubernetesAutoscalerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.model.KubernetesControllerConverter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Created by spinnaker on 20/8/17.
 */
@Slf4j
class KubernetesClientApiConverter {
  private static final Logger LOG = LoggerFactory.getLogger(KubernetesClientApiConverter)

  static DeployKubernetesAtomicOperationDescription fromStatefulSet(V1beta1StatefulSet statefulSet) {
    def deployDescription = new DeployKubernetesAtomicOperationDescription()
    def parsedName = Names.parseName(statefulSet?.metadata?.name)

    deployDescription.application = parsedName?.app
    deployDescription.stack = parsedName?.stack
    deployDescription.freeFormDetails = parsedName?.detail
    deployDescription.loadBalancers = KubernetesUtil?.getLoadBalancers(statefulSet.spec?.template?.metadata?.labels ?: [:])
    deployDescription.namespace = statefulSet?.metadata?.namespace
    deployDescription.targetSize = statefulSet?.spec?.replicas
    deployDescription.securityGroups = []
    deployDescription.controllerAnnotations = statefulSet?.metadata?.annotations
    deployDescription.podAnnotations = statefulSet?.spec?.template?.metadata?.annotations
    deployDescription.volumeClaims = statefulSet?.spec?.getVolumeClaimTemplates()
    deployDescription.volumeSources = statefulSet?.spec?.template?.spec?.volumes?.collect {
      fromVolume(it)
    } ?: []
    deployDescription.hostNetwork = statefulSet?.spec?.template?.spec?.hostNetwork
    deployDescription.containers = statefulSet?.spec?.template?.spec?.containers?.collect {
      fromContainer(it)
    } ?: []
    deployDescription.terminationGracePeriodSeconds = statefulSet?.spec?.template?.spec?.terminationGracePeriodSeconds
    deployDescription.serviceAccountName = statefulSet?.spec?.template?.spec?.serviceAccountName
    deployDescription.nodeSelector = statefulSet?.spec?.template?.spec?.nodeSelector

    return deployDescription
  }

  static DeployKubernetesAtomicOperationDescription fromDaemonSet(V1beta1DaemonSet daemonSet) {
    def deployDescription = new DeployKubernetesAtomicOperationDescription()
    def parsedName = Names.parseName(daemonSet?.metadata?.name)

    deployDescription.application = parsedName?.app
    deployDescription.stack = parsedName?.stack
    deployDescription.freeFormDetails = parsedName?.detail
    deployDescription.loadBalancers = KubernetesUtil?.getLoadBalancers(daemonSet.spec?.template?.metadata?.labels ?: [:])
    deployDescription.namespace = daemonSet?.metadata?.namespace
    deployDescription.securityGroups = []
    deployDescription.podAnnotations = daemonSet?.spec?.template?.metadata?.annotations
    deployDescription.volumeSources = daemonSet?.spec?.template?.spec?.volumes?.collect {
      fromVolume(it)
    } ?: []

    deployDescription.hostNetwork = daemonSet?.spec?.template?.spec?.hostNetwork

    deployDescription.containers = daemonSet?.spec?.template?.spec?.containers?.collect {
      fromContainer(it)
    } ?: []

    deployDescription.terminationGracePeriodSeconds = daemonSet?.spec?.template?.spec?.terminationGracePeriodSeconds

    deployDescription.nodeSelector = daemonSet?.spec?.template?.spec?.nodeSelector

    return deployDescription
  }

  static KubernetesContainerDescription fromContainer(V1Container container) {
    if (!container) {
      return null
    }

    def containerDescription = new KubernetesContainerDescription()
    containerDescription.name = container.name
    containerDescription.imageDescription = KubernetesUtil.buildImageDescription(container.image)

    if (container.imagePullPolicy) {
      containerDescription.imagePullPolicy = KubernetesPullPolicy.valueOf(container.imagePullPolicy)
    }

    container.resources?.with {
      containerDescription.limits = limits?.cpu || limits?.memory ?
        new KubernetesResourceDescription(
          cpu: limits?.cpu,
          memory: limits?.memory
        ) : null

      containerDescription.requests = requests?.cpu || requests?.memory ?
        new KubernetesResourceDescription(
          cpu: requests?.cpu,
          memory: requests?.memory
        ) : null
    }

    if (container.lifecycle) {
      containerDescription.lifecycle = new KubernetesLifecycle()
      if (container.lifecycle.postStart) {
        containerDescription.lifecycle.postStart = fromHandler(container.lifecycle.postStart)
      }
      if (container.lifecycle.preStop) {
        containerDescription.lifecycle.preStop = fromHandler(container.lifecycle.preStop)
      }
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

    if (container.securityContext) {
      def securityContext = container.securityContext

      containerDescription.securityContext = new KubernetesSecurityContext(privileged: securityContext.privileged,
        runAsNonRoot: securityContext.runAsNonRoot,
        runAsUser: securityContext.runAsUser,
        readOnlyRootFilesystem: securityContext.readOnlyRootFilesystem
      )

      if (securityContext.capabilities) {
        def capabilities = securityContext.capabilities

        containerDescription.securityContext.capabilities = new KubernetesCapabilities(add: capabilities.add, drop: capabilities.drop)
      }

      if (securityContext.seLinuxOptions) {
        def seLinuxOptions = securityContext.seLinuxOptions

        containerDescription.securityContext.seLinuxOptions = new KubernetesSeLinuxOptions(user: seLinuxOptions.user,
          role: seLinuxOptions.role,
          type: seLinuxOptions.type,
          level: seLinuxOptions.level
        )
      }
    }

    containerDescription.livenessProbe = fromV1Probe(container?.livenessProbe)
    containerDescription.readinessProbe = fromV1Probe(container?.readinessProbe)

    containerDescription.volumeMounts = container?.volumeMounts?.collect { volumeMount ->
      new KubernetesVolumeMount(name: volumeMount.name, readOnly: volumeMount.readOnly, mountPath: volumeMount.mountPath)
    }

    containerDescription.args = container?.args ?: []
    containerDescription.command = container?.command ?: []

    return containerDescription
  }

  static KubernetesVolumeSource fromVolume(V1Volume volume) {
    def res = new KubernetesVolumeSource(name: volume.name)

    if (volume.emptyDir) {
      res.type = KubernetesVolumeSourceType.EmptyDir
      def medium = volume.emptyDir.medium
      def mediumType

      if (medium == "Memory") {
        mediumType = KubernetesStorageMediumType.Memory
      } else {
        mediumType = KubernetesStorageMediumType.Default
      }

      res.emptyDir = new KubernetesEmptyDir(medium: mediumType)
    } else if (volume.hostPath) {
      res.type = KubernetesVolumeSourceType.HostPath
      res.hostPath = new KubernetesHostPath(path: volume.hostPath.path)
    } else if (volume.persistentVolumeClaim) {
      res.type = KubernetesVolumeSourceType.PersistentVolumeClaim
      res.persistentVolumeClaim = new KubernetesPersistentVolumeClaim(claimName: volume.persistentVolumeClaim.claimName,
        readOnly: volume.persistentVolumeClaim.readOnly)
    } else if (volume.secret) {
      res.type = KubernetesVolumeSourceType.Secret
      res.secret = new KubernetesSecretVolumeSource(secretName: volume.secret.secretName)
    } else if (volume.configMap) {
      res.type = KubernetesVolumeSourceType.ConfigMap
      def items = volume.configMap.items?.collect { V1KeyToPath item ->
       new KubernetesKeyToPath(key: item.key, path: item.path)
      }
      res.configMap = new KubernetesConfigMapVolumeSource(configMapName: volume.configMap.name, items: items)
    } else if (volume.awsElasticBlockStore) {
      res.type = KubernetesVolumeSourceType.AwsElasticBlockStore
      def ebs = volume.awsElasticBlockStore
      res.awsElasticBlockStore = new KubernetesAwsElasticBlockStoreVolumeSource(volumeId: ebs.volumeID,
        fsType: ebs.fsType,
        partition: ebs.partition)
    } else {
      res.type = KubernetesVolumeSourceType.Unsupported
    }

    return res
  }

  static KubernetesExecAction fromExecAction(V1ExecAction exec) {
    if (!exec) {
      return null
    }

    def kubernetesExecAction = new KubernetesExecAction()
    kubernetesExecAction.commands = exec.command
    return kubernetesExecAction
  }

  static KubernetesHandler fromHandler(V1Handler handler) {
    def kubernetesHandler = new KubernetesHandler()
    if (handler.exec) {
      kubernetesHandler.execAction = fromExecAction(handler.exec)
      kubernetesHandler.type = KubernetesHandlerType.EXEC
    }

    if (handler.tcpSocket) {
      kubernetesHandler.tcpSocketAction = fromTcpSocketAction(handler.tcpSocket)
      kubernetesHandler.type = KubernetesHandlerType.TCP
    }

    if (handler.httpGet) {
      kubernetesHandler.httpGetAction = fromHttpGetAction(handler.httpGet)
      kubernetesHandler.type = KubernetesHandlerType.HTTP
    }

    return kubernetesHandler
  }

  static KubernetesHttpGetAction fromHttpGetAction(V1HTTPGetAction httpGet) {
    if (!httpGet) {
      return null
    }

    def kubernetesHttpGetAction = new KubernetesHttpGetAction()
    kubernetesHttpGetAction.host = httpGet.host
    kubernetesHttpGetAction.path = httpGet.path
    try {
      kubernetesHttpGetAction.port = httpGet.port?.toInteger() ?: 0
    } catch (NumberFormatException ex) {
      log.warn "Port number is not Integer", ex
    }
    kubernetesHttpGetAction.uriScheme = httpGet.scheme
    kubernetesHttpGetAction.httpHeaders = httpGet.httpHeaders?.collect() {
      new KeyValuePair(name: it.name, value: it.value)
    }
    return kubernetesHttpGetAction
  }

  static KubernetesTcpSocketAction fromTcpSocketAction(V1TCPSocketAction tcpSocket) {
    if (!tcpSocket) {
      return null
    }

    def kubernetesTcpSocketAction = new KubernetesTcpSocketAction()
    kubernetesTcpSocketAction.port = tcpSocket.port.toInteger() ?: 0
    return kubernetesTcpSocketAction
  }

  static KubernetesProbe fromV1Probe(V1Probe probe) {
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

  /**
   * This method converts the Object to YAML
   * @param obj
   * @return
   */
  static String getYaml(Object obj) {
    ObjectMapper m = new ObjectMapper(new YAMLFactory());
    return m.writeValueAsString(obj).replaceAll("\\\\", "");
  }

  static V1beta1StatefulSet toStatefulSet(DeployKubernetesAtomicOperationDescription description,
                                          String statefulSetName) {
    def targetSize = description.targetSize ?: description.capacity?.desired
    def stateful = new V1beta1StatefulSet()
    def spec = new V1beta1StatefulSetSpec()

    def templateSpec = toPodTemplateSpec(description, statefulSetName)
    spec.template = templateSpec

    def metadata = new V1ObjectMeta()
    def selector = new V1LabelSelector()
    metadata.labels = genericLabels(description.application, statefulSetName, description.namespace)
    if (description.controllerAnnotations) {
      metadata.annotations = new HashMap<String, String>()
      description.controllerAnnotations.forEach({ k, v ->
        metadata.annotations.put(k, v)
      })
    }
    spec.template.metadata = metadata
    selector.matchLabels = metadata.labels
    spec.selector = selector
    spec.serviceName = statefulSetName
    spec.replicas = description.targetSize

    if (description.podManagementPolicy) {
      spec.podManagementPolicy = description.podManagementPolicy
    }

    def persistentVolumeClaims = toPersistentVolumeClaims(description, statefulSetName)
    persistentVolumeClaims.forEach({ persistentVolumeClaim ->
      spec.addVolumeClaimTemplatesItem(persistentVolumeClaim)
    })

    if (description.updateController) {
      def updateController = description.updateController
      def updateStrategy = new V1beta1StatefulSetUpdateStrategy()
      def rollingUpdate = new V1beta1RollingUpdateStatefulSetStrategy()

      if(updateController) {
        if (updateController.updateStrategy.type.name() != "Recreate") {
          updateStrategy.type = updateController.updateStrategy.type
          if (updateController.updateStrategy.rollingUpdate) {
            if (updateController.updateStrategy.rollingUpdate.partition) {
              rollingUpdate.partition = updateController.updateStrategy.rollingUpdate.partition
            }
            updateStrategy.rollingUpdate = rollingUpdate
          }
          spec.updateStrategy = updateStrategy
        }
      }
    }

    metadata = new V1ObjectMeta()
    metadata.name = statefulSetName
    metadata.namespace = description.namespace
    metadata.labels = genericLabels(description.application, statefulSetName, description.namespace)
    metadata.deletionGracePeriodSeconds = description.terminationGracePeriodSeconds

    stateful.metadata = metadata
    stateful.spec = spec
    stateful.apiVersion = description.apiVersion
    stateful.kind = description.kind

    return stateful
  }

  static List<V1PersistentVolumeClaim> toPersistentVolumeClaims(DeployKubernetesAtomicOperationDescription description, String name) {
    def persistentVolumeClaims = new ArrayList<V1PersistentVolumeClaim>()
    if (description.volumeClaims) {
      description.volumeClaims.forEach({ claim ->
        def spec = new V1PersistentVolumeClaimSpec()
        def metadata = new V1ObjectMeta()

        if (description.volumeAnnotations) {
          metadata.annotations = new HashMap<String, String>()
          description.volumeAnnotations.forEach({ k, v ->
            metadata.annotations.put(k, v)
          })
        }
        metadata.name = claim.claimName

        if (claim.accessModes) {
          spec.accessModes = claim.accessModes
        }

        if (claim.requirements) {
          def resources = new V1ResourceRequirements()
          resources.limits = claim.requirements.limits
          resources.requests = claim.requirements.requests

          spec.resources = resources
        }

        if (claim.storageClassName) {
          spec.storageClassName = claim.storageClassName
        }

        def volumeClaim = new V1PersistentVolumeClaim()
        volumeClaim.spec = spec
        volumeClaim.metadata = metadata

        persistentVolumeClaims.add(volumeClaim)
      })
    }

    return persistentVolumeClaims
  }

  static V1PodTemplateSpec toPodTemplateSpec(DeployKubernetesAtomicOperationDescription description, String name) {
    def podTemplateSpec = new V1PodTemplateSpec()
    def podSpec = new V1PodSpec()
    def metadata = new V1ObjectMeta()

    for (def loadBalancer : description.loadBalancers) {
      metadata.labels.put(KubernetesUtil.loadBalancerKey(loadBalancer), "true")
    }

    if (description.podAnnotations) {
      metadata.annotations = new HashMap<String, String>()
      description.podAnnotations.forEach({ k, v ->
        metadata.annotations.put(k, v)
      })
    }

    podTemplateSpec.metadata = metadata

    if (description.restartPolicy) {
      podSpec.restartPolicy = description.restartPolicy
    } else {
      podSpec.restartPolicy = "Always"
    }

    if (description.terminationGracePeriodSeconds) {
      podSpec.terminationGracePeriodSeconds = description.terminationGracePeriodSeconds
    }

    if (description.imagePullSecrets) {
      podSpec.imagePullSecrets = new ArrayList()
      for (def imagePullSecret : description.imagePullSecrets) {
        def secret = new V1ObjectReference()
        secret.name = imagePullSecret
        secret.namespace = description.namespace
        podSpec.imagePullSecrets.add(secret)
      }
    }

    if (description.serviceAccountName) {
      podSpec.serviceAccountName = description.serviceAccountName
    }

    podSpec.nodeSelector = description.nodeSelector

    if (description.volumeSources) {
      def volumeSources = description.volumeSources.findResults { volumeSource ->
        toVolumeSource(volumeSource)
      }
      podSpec.volumes = volumeSources
    }

    podSpec.hostNetwork = description.hostNetwork
    def containers = description.containers.collect { container ->
      toContainer(container)
    }

    podSpec.dnsPolicy = "ClusterFirst"
    podSpec.containers = containers
    podTemplateSpec.spec = podSpec

    return podTemplateSpec
  }
  static V1Volume toVolumeSource(KubernetesVolumeSource volumeSource) {
    def volume = new V1Volume(name: volumeSource.name)
    switch (volumeSource.type) {
      case KubernetesVolumeSourceType.EmptyDir:
        def res = new V1EmptyDirVolumeSource()
        switch (volumeSource.emptyDir.medium) {
          case KubernetesStorageMediumType.Memory:
            res.medium = "Memory"
            break

          default:
            res = "" // Empty string is default...
        }
        break

      case KubernetesVolumeSourceType.HostPath:
        def res = new V1HostPathVolumeSource()
        res.path = volumeSource.hostPath.path
        volume.hostPath = res
        break

      case KubernetesVolumeSourceType.PersistentVolumeClaim:
        def res = new V1PersistentVolumeClaimVolumeSource()
        res.claimName = volumeSource.persistentVolumeClaim.claimName
        res.readOnly = volumeSource.persistentVolumeClaim.readOnly
        volume.persistentVolumeClaim = res
        break

      case KubernetesVolumeSourceType.Secret:
        def res = new V1SecretVolumeSource()
        res.secretName = volumeSource.secret.secretName
        volume.secret = res
        break

      case KubernetesVolumeSourceType.ConfigMap:
        def res = new V1ConfigMapVolumeSource()
        res.name = volumeSource.configMap.configMapName

        def items = volumeSource.configMap.items?.collect { KubernetesKeyToPath item ->
          new V1KeyToPath(key: item.key, path: item.path)
        }

        res.items = items
        volume.configMap = res
        break

      default:
        LOG.warn "Unable to identify  KubernetesVolumeSourceType $KubernetesVolumeSourceType".toString()
        return null
    }

    return volume
  }

  static V1Container toContainer(KubernetesContainerDescription container) {
    KubernetesUtil.normalizeImageDescription(container.imageDescription)
    def imageId = KubernetesUtil.getImageId(container.imageDescription)
    def v1container = new V1Container()

    v1container.image = imageId

    if (container.imagePullPolicy) {
      v1container.imagePullPolicy = container.imagePullPolicy.toString()
    } else {
      v1container.imagePullPolicy = "ALWAYS"
    }

    v1container.name = container.name

    if (container.ports) {
      container.ports.forEach { it ->
        def ports = new V1ContainerPort()
        if (it.name) {
          ports.name = it.name
        }

        if (it.containerPort) {
          ports.containerPort = it.containerPort
        }

        if (it.hostPort) {
          ports.hostPort = it.hostPort
        }

        if (it.protocol) {
          ports.protocol = it.protocol
        }

        if (it.hostIp) {
          ports.hostIP = it.hostIp
        }

        v1container.addPortsItem(ports)
      }
    }

    if (container.securityContext) {
      def securityContext = new V1SecurityContext()
      securityContext.runAsNonRoot = container.securityContext.runAsNonRoot
      securityContext.runAsUser = container.securityContext.runAsUser
      securityContext.privileged = container.securityContext.privileged
      securityContext.readOnlyRootFilesystem = container.securityContext.readOnlyRootFilesystem
      v1container.securityContext = container.securityContext

      if (container.securityContext.seLinuxOptions) {
        def seLinuxOptions = new V1SELinuxOptions()
        seLinuxOptions.user = container.securityContext.seLinuxOptions.user
        seLinuxOptions.role = container.securityContext.seLinuxOptions.role
        seLinuxOptions.type = container.securityContext.seLinuxOptions.type
        seLinuxOptions.level = container.securityContext.seLinuxOptions.level

        v1container.securityContext.seLinuxOptions = seLinuxOptions
      }

      if (securityContext.capabilities) {
        def capabilities = new V1Capabilities()
        capabilities.add = securityContext.capabilities.add
        capabilities.drop = securityContext.capabilities.drop

        v1container.securityContext.capabilities = capabilities
      }

      v1container.securityContext = securityContext
    }

    [liveness: container.livenessProbe, readiness: container.readinessProbe].each { k, v ->
      def probe = v
      def v1probe = new V1Probe()
      if (probe) {

        v1probe.initialDelaySeconds = probe.initialDelaySeconds

        if (probe.timeoutSeconds) {
          v1probe.timeoutSeconds = probe.timeoutSeconds
        }

        if (probe.failureThreshold) {
          v1probe.failureThreshold = probe.failureThreshold
        }

        if (probe.successThreshold) {
          v1probe.successThreshold = probe.successThreshold
        }

        if(probe.periodSeconds) {
          v1probe.periodSeconds = probe.periodSeconds
        }

        switch (probe.handler.type) {
          case KubernetesHandlerType.EXEC:
            v1probe.exec = toExecAction(probe.handler.execAction)
            break

          case KubernetesHandlerType.TCP:
            v1probe.tcpSocket = toTcpSocketAction(probe.handler.tcpSocketAction)
            break

          case KubernetesHandlerType.HTTP:
            v1probe.httpGet = toHttpGetAction(probe.handler.httpGetAction)
            break
        }

        switch (k) {
          case 'liveness':
            v1container.livenessProbe = v1probe
            break
          case 'readiness':
            v1container.readinessProbe = v1probe
            break
          default:
            throw new IllegalArgumentException("Probe type $k not supported")
        }
      }
    }

    if (container.lifecycle) {
      def lifecycle = new V1Lifecycle()

      if (container.lifecycle.postStart) {
        lifecycle.postStart = toHandler(container.lifecycle.postStart)
      }

      if (container.lifecycle.preStop) {
        lifecycle.preStop = toHandler(container.lifecycle.preStop)
      }
      v1container.lifecycle = lifecycle
    }

    def resources = new V1ResourceRequirements()
    if (container.requests) {
      def requests = [:]

      if (container.requests.memory) {
        requests.memory = container.requests.memory
      }

      if (container.requests.cpu) {
        requests.cpu = container.requests.cpu
      }
      resources.requests = requests
    }

    if (container.limits) {
      def limits = [:]

      if (container.limits.memory) {
        limits.memory = container.limits.memory
      }

      if (container.limits.cpu) {
        limits.cpu = container.limits.cpu
      }

      resources.limits = limits
    }
    v1container.resources = resources

    if (container.volumeMounts) {
      def volumeMounts = new ArrayList<V1VolumeMount>()
      container.volumeMounts.collect { mount ->
        def res = new V1VolumeMount()
        res.name = mount.name
        res.mountPath = mount.mountPath
        res.readOnly = mount.readOnly
        volumeMounts.add(res)
      }
      v1container.volumeMounts = volumeMounts
    }

    if (container.envVars) {
      def envVars = container.envVars.collect { envVar ->
        def envVarRes = new V1EnvVar()
        envVarRes.name = envVar.name
        if (envVar.value) {
          envVarRes.value = envVar.value
        } else if (envVar.envSource) {
          V1EnvVarSource envVarSource = new V1EnvVarSource()

          if (envVar.envSource.configMapSource) {
            def configMap = envVar.envSource.configMapSource
            envVarSource.configMapKeyRef = configMap
          } else if (envVar.envSource.secretSource) {
            def secret = envVar.envSource.secretSource
            envVarSource.secretKeyRef = secret
          } else if (envVar.envSource.fieldRef) {
            V1ObjectFieldSelector fieldRef = new V1ObjectFieldSelector()
            fieldRef.fieldPath = envVar.envSource.fieldRef.fieldPath
            envVarSource.fieldRef = fieldRef
          } else if (envVar.envSource.resourceFieldRef) {
            def resource = envVar.envSource.resourceFieldRef.resource
            def containerName = envVar.envSource.resourceFieldRef.containerName
            def divisor = envVar.envSource.resourceFieldRef.divisor
            def resouceField = new V1ResourceFieldSelector()
            resouceField.resource = resource
            resouceField.containerName = containerName
            resouceField.divisor = divisor

            envVarSource.resourceFieldRef = resource
          }

          envVarRes.valueFrom = envVarSource
        } else {
          return null
        }
        return envVarRes
      } - null
      v1container.env = envVars
    }

    if (container.command) {
      v1container.command = container.command
    }

    if (container.args) {
      v1container.args = container.args
    }

    return v1container
  }

  static V1ExecAction toExecAction(KubernetesExecAction action) {
    def execAction = new V1ExecAction()
    execAction.command = action.commands

    return execAction
  }

  static V1TCPSocketAction toTcpSocketAction(KubernetesTcpSocketAction action) {
    def tcpAction = new V1TCPSocketAction()
    tcpAction.port = action.port

    return tcpAction
  }

  static V1HTTPGetAction toHttpGetAction(KubernetesHttpGetAction action) {
    def httpGetAction = new V1HTTPGetAction()
    if (action.host) {
      httpGetAction.host = action.host
    }

    if (action.path) {
      httpGetAction.path = action.path
    }

    httpGetAction.port = String.valueOf(action.port)

    if (action.uriScheme) {
      httpGetAction.scheme = action.uriScheme
    }

    if (action.httpHeaders) {
      def headers = action.httpHeaders.collect() {
        V1HTTPHeader header = new V1HTTPHeader()
        header.name = it.name
        header.value = it.value
        return header
      }
      httpGetAction.httpHeaders = headers
    }

    return httpGetAction
  }

  static V1Handler toHandler(KubernetesHandler handler) {
    def handlerBuilder = new V1Handler()
    switch (handler.type) {
      case KubernetesHandlerType.EXEC:
        handlerBuilder.exec = toExecAction(handler.execAction)
        break

      case KubernetesHandlerType.TCP:
        handler.tcpSocketAction = toHttpGetAction(handler.tcpSocketAction)
        break

      case KubernetesHandlerType.HTTP:
        handler.httpGetAction = toHttpGetAction(handler.httpGetAction)
        break
    }

    return handler
  }

  static V1HorizontalPodAutoscaler toAutoscaler(KubernetesAutoscalerDescription description,
                                                String resourceName,
                                                String resourceKind) {
    def autoscaler = new V1HorizontalPodAutoscaler()

    V1ObjectMeta metadata = new V1ObjectMeta()
    metadata.name = resourceName
    metadata.namespace = description.namespace

    autoscaler.metadata = metadata

    def spec = new V1HorizontalPodAutoscalerSpec()
    spec.minReplicas = description.capacity.min
    spec.maxReplicas = description.capacity.max
    spec.targetCPUUtilizationPercentage = description.scalingPolicy.cpuUtilization.target

    def targetRef = new V1CrossVersionObjectReference()
    targetRef.name = resourceName
    targetRef.kind = resourceKind

    spec.scaleTargetRef = targetRef
    autoscaler.spec = spec

    return autoscaler
  }

  static KubernetesControllerConverter toKubernetesController(V1beta1StatefulSet controllerSet) {
    //FIXME: Use this method for k8s client api transforms to fabric8 object till fully k8s client api compilant
    return (new KubernetesControllerConverter(controllerSet.kind, controllerSet.apiVersion, controllerSet.metadata))
  }

  static KubernetesControllerConverter toKubernetesController(V1beta1DaemonSet controllerSet) {
    //FIXME: Use this method for k8s client api transforms to fabric8 object till fully k8s client api compilant
    return (new KubernetesControllerConverter(controllerSet.kind, controllerSet.apiVersion, controllerSet.metadata))
  }

  /*
    TODO:Create some gneral purpose labels for helping identify a controller.  Feel free to expend or fix this function.
   */
  static Map<String, String> genericLabels(String appName, String name, String namespace) {
    def labels = [
      "app"      : appName,
      "cluster"  : name,
      "namespace": namespace,
    ]

    return labels
  }

  static V1beta1DaemonSet toDaemonSet(DeployKubernetesAtomicOperationDescription description,
                                      String daemonsetName) {
    def targetSize = description.targetSize ?: description.capacity?.desired

    def daemonset = new V1beta1DaemonSet()
    def spec = new V1beta1DaemonSetSpec()
    spec.template = toPodTemplateSpec(description, daemonsetName)

    def metadata = new V1ObjectMeta()
    def selector = new V1LabelSelector()
    metadata.labels = genericLabels(description.application, daemonsetName, description.namespace)
    if (description.controllerAnnotations) {
      metadata.annotations = new HashMap<String, String>()
      description.controllerAnnotations.forEach({ k, v ->
        metadata.annotations.put(k, v)
      })
    }
    spec.template.metadata = metadata
    selector.matchLabels = metadata.labels

    spec.template.metadata = metadata
    spec.selector = selector

    if (description.updateController) {
      def updateController = description.updateController
      def updateStrategy = new V1beta1DaemonSetUpdateStrategy()
      def rollingUpdate = new V1beta1RollingUpdateDaemonSet()

      if (updateController) {
        //Note: Do not handle OnDelete because it is default.
        if (updateController.updateStrategy.type.name() != "Recreate") {
          updateStrategy.type = updateController.updateStrategy.type
          if (updateController.updateStrategy.rollingUpdate) {
            rollingUpdate.maxUnavailable = updateController.updateStrategy.rollingUpdate.maxUnavailable
            updateStrategy.rollingUpdate = rollingUpdate
          }
          spec.updateStrategy = updateStrategy
        }
      }
    }

    metadata.name = daemonsetName
    metadata.namespace = description.namespace
    daemonset.metadata = metadata
    daemonset.spec = spec
    daemonset.apiVersion = description.apiVersion
    daemonset.kind = description.kind

    return daemonset
  }

  static boolean canUpdated(DeployKubernetesAtomicOperationDescription description) {
    return description.updateController?.enabled
  }
}
