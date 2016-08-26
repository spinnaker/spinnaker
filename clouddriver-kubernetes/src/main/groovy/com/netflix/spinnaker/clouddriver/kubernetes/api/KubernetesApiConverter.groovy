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
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.job.RunKubernetesJobDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.loadbalancer.KubernetesLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.loadbalancer.KubernetesNamedServicePort
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.securitygroup.KubernetesHttpIngressPath
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.securitygroup.KubernetesHttpIngressRuleValue
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.securitygroup.KubernetesIngressBackend
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.securitygroup.KubernetesIngressRule
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.securitygroup.KubernetesIngressRuleValue
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.securitygroup.KubernetesSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.*
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.api.model.extensions.Ingress
import io.fabric8.kubernetes.api.model.extensions.Job
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet

class KubernetesApiConverter {
  static KubernetesSecurityGroupDescription fromIngress(Ingress ingress) {
    if (!ingress) {
      return null
    }

    def securityGroupDescription = new KubernetesSecurityGroupDescription()

    securityGroupDescription.securityGroupName = ingress.metadata.name
    def parse = Names.parseName(securityGroupDescription.securityGroupName)
    securityGroupDescription.app = parse.app
    securityGroupDescription.stack = parse.stack
    securityGroupDescription.detail = parse.detail
    securityGroupDescription.namespace = ingress.metadata.namespace

    securityGroupDescription.ingress = new KubernetesIngressBackend()
    securityGroupDescription.ingress.port = ingress.spec.backend?.servicePort?.intVal ?: 0
    securityGroupDescription.ingress.serviceName = ingress.spec.backend?.serviceName

    securityGroupDescription.rules = ingress.spec.rules.collect { rule ->
      def resRule = new KubernetesIngressRule()
      resRule.host = rule.host
      if (rule.http) {
        resRule.value = new KubernetesIngressRuleValue(http: new KubernetesHttpIngressRuleValue())
        resRule.value.http.paths = rule.http.paths?.collect { path ->
          def resPath = new KubernetesHttpIngressPath()
          resPath.path = path.path
          if (path.backend) {
            resPath.ingress = new KubernetesIngressBackend(port: path.backend.servicePort?.intVal ?: 0,
                                                           serviceName: path.backend.serviceName)
          }

          return resPath
        }
      }

      return resRule
    }

    securityGroupDescription
  }


  static KubernetesLoadBalancerDescription fromService(Service service) {
    if (!service) {
      return null
    }

    def loadBalancerDescription = new KubernetesLoadBalancerDescription()

    loadBalancerDescription.name = service.metadata.name
    def parse = Names.parseName(loadBalancerDescription.name)
    loadBalancerDescription.app = parse.app
    loadBalancerDescription.stack = parse.stack
    loadBalancerDescription.detail = parse.detail
    loadBalancerDescription.namespace = service.metadata.namespace

    loadBalancerDescription.clusterIp = service.spec.clusterIP
    loadBalancerDescription.loadBalancerIp = service.spec.loadBalancerIP
    loadBalancerDescription.sessionAffinity = service.spec.sessionAffinity
    loadBalancerDescription.serviceType = service.spec.type

    loadBalancerDescription.externalIps = service.spec.externalIPs ?: []
    loadBalancerDescription.ports = service.spec.ports?.collect { port ->
      new KubernetesNamedServicePort(
          name: port.name,
          protocol: port.protocol,
          port: port.port ?: 0,
          targetPort: port.targetPort?.intVal ?: 0,
          nodePort: port.nodePort ?: 0
      )
    }

    return loadBalancerDescription
  }

  static Volume toVolumeSource(KubernetesVolumeSource volumeSource) {
    Volume volume = new Volume(name: volumeSource.name)

    switch (volumeSource.type) {
      case KubernetesVolumeSourceType.EMPTYDIR:
        def res = new EmptyDirVolumeSourceBuilder()

        switch (volumeSource.emptyDir.medium) {
          case KubernetesStorageMediumType.MEMORY:
            res = res.withMedium("Memory")
            break

          default:
            res = res.withMedium("") // Empty string is default...
        }

        volume.emptyDir = res.build()
        break

      case KubernetesVolumeSourceType.HOSTPATH:
        def res = new HostPathVolumeSourceBuilder().withPath(volumeSource.hostPath.path)
        volume.hostPath = res.build()
        break

      case KubernetesVolumeSourceType.PERSISTENTVOLUMECLAIM:
        def res = new PersistentVolumeClaimVolumeSourceBuilder()
            .withClaimName(volumeSource.persistentVolumeClaim.claimName)
            .withReadOnly(volumeSource.persistentVolumeClaim.readOnly)
        volume.persistentVolumeClaim = res.build()
        break

      case KubernetesVolumeSourceType.SECRET:
        def res = new SecretVolumeSourceBuilder()
            .withSecretName(volumeSource.secret.secretName)
        volume.secret = res.build()
        break

      default:
        return null
    }

    return volume
  }

  static Container toContainer(KubernetesContainerDescription container) {
    KubernetesUtil.normalizeImageDescription(container.imageDescription)
    def imageId = KubernetesUtil.getImageId(container.imageDescription)
    def containerBuilder = new ContainerBuilder().withName(container.name).withImage(imageId)

    if (container.ports) {
      container.ports.forEach {
        containerBuilder = containerBuilder.addNewPort()
        if (it.name) {
          containerBuilder = containerBuilder.withName(it.name)
        }

        if (it.containerPort) {
          containerBuilder = containerBuilder.withContainerPort(it.containerPort)
        }

        if (it.hostPort) {
          containerBuilder = containerBuilder.withHostPort(it.hostPort)
        }

        if (it.protocol) {
          containerBuilder = containerBuilder.withProtocol(it.protocol)
        }

        if (it.hostIp) {
          containerBuilder = containerBuilder.withHostIP(it.hostIp)
        }
        containerBuilder = containerBuilder.endPort()
      }
    }

    [liveness: container.livenessProbe, readiness: container.readinessProbe].each { k, v ->
      def probe = v
      if (probe) {
        switch (k) {
          case 'liveness':
            containerBuilder = containerBuilder.withNewLivenessProbe()
            break
          case 'readiness':
            containerBuilder = containerBuilder.withNewReadinessProbe()
            break
        }

        containerBuilder = containerBuilder.withInitialDelaySeconds(probe.initialDelaySeconds)

        if (probe.timeoutSeconds) {
          containerBuilder = containerBuilder.withTimeoutSeconds(probe.timeoutSeconds)
        }

        if (probe.failureThreshold) {
          containerBuilder = containerBuilder.withFailureThreshold(probe.failureThreshold)
        }

        if (probe.successThreshold) {
          containerBuilder = containerBuilder.withSuccessThreshold(probe.successThreshold)
        }

        if (probe.periodSeconds) {
          containerBuilder = containerBuilder.withPeriodSeconds(probe.periodSeconds)
        }

        switch (probe.handler.type) {
          case KubernetesHandlerType.EXEC:
            containerBuilder = containerBuilder.withNewExec().withCommand(probe.handler.execAction.commands).endExec()
            break

          case KubernetesHandlerType.TCP:
            containerBuilder = containerBuilder.withNewTcpSocket().withNewPort(probe.handler.tcpSocketAction.port).endTcpSocket()
            break

          case KubernetesHandlerType.HTTP:
            containerBuilder = containerBuilder.withNewHttpGet()
            def get = probe.handler.httpGetAction

            if (get.host) {
              containerBuilder = containerBuilder.withHost(get.host)
            }

            if (get.path) {
              containerBuilder = containerBuilder.withPath(get.path)
            }

            containerBuilder = containerBuilder.withPort(new IntOrString(get.port))

            if (get.uriScheme) {
              containerBuilder = containerBuilder.withScheme(get.uriScheme)
            }

            if (get.httpHeaders) {
              def headers = get.httpHeaders.collect() {
                def builder = new HTTPHeaderBuilder()
                return builder.withName(it.name).withValue(it.value).build()
              }

              containerBuilder.withHttpHeaders(headers)
            }

            containerBuilder = containerBuilder.endHttpGet()
            break
        }

        switch (k) {
          case 'liveness':
            containerBuilder = containerBuilder.endLivenessProbe()
            break
          case 'readiness':
            containerBuilder = containerBuilder.endReadinessProbe()
            break
        }
      }
    }

    containerBuilder = containerBuilder.withNewResources()
    if (container.requests) {
      def requests = [:]

      if (container.requests.memory) {
        requests.memory = container.requests.memory
      }

      if (container.requests.cpu) {
        requests.cpu = container.requests.cpu
      }
      containerBuilder = containerBuilder.withRequests(requests)
    }

    if (container.limits) {
      def limits = [:]

      if (container.limits.memory) {
        limits.memory = container.limits.memory
      }

      if (container.limits.cpu) {
        limits.cpu = container.limits.cpu
      }

      containerBuilder = containerBuilder.withLimits(limits)
    }

    containerBuilder = containerBuilder.endResources()

    if (container.volumeMounts) {
      def volumeMounts = container.volumeMounts.collect { mount ->
        def res = new VolumeMountBuilder()

        return res.withMountPath(mount.mountPath)
            .withName(mount.name)
            .withReadOnly(mount.readOnly)
            .build()
      }

      containerBuilder = containerBuilder.withVolumeMounts(volumeMounts)
    }

    if (container.envVars) {
      def envVars = container.envVars.collect { envVar ->
        def res = (new EnvVarBuilder()).withName(envVar.name)
        if (envVar.value) {
          res = res.withValue(envVar.value)
        } else if (envVar.envSource) {
          res = res.withNewValueFrom()
          if (envVar.envSource.configMapSource) {
            def configMap = envVar.envSource.configMapSource
            res = res.withNewConfigMapKeyRef(configMap.key, configMap.configMapName)
          } else if (envVar.envSource.secretSource) {
            def secret = envVar.envSource.secretSource
            res = res.withNewSecretKeyRef(secret.key, secret.secretName)
          } else {
            return null
          }
          res = res.endValueFrom()
        } else {
          return null
        }
        return res.build()
      } - null

      containerBuilder = containerBuilder.withEnv(envVars)
    }

    if (container.command) {
      containerBuilder = containerBuilder.withCommand(container.command)
    }

    if (container.args) {
      containerBuilder = containerBuilder.withArgs(container.args)
    }

    return containerBuilder.build()
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
      def result = new KubernetesEnvVar(name: envVar.name)
      if (envVar.value) {
        result.value = envVar.value
      } else if (envVar.valueFrom) {
        def source = new KubernetesEnvVarSource()
        if (envVar.valueFrom.configMapKeyRef) {
          def configMap = envVar.valueFrom.configMapKeyRef
          source.configMapSource = new KubernetesConfigMapSource(key: configMap.key, configMapName: configMap.name)
        } else if (envVar.valueFrom.secretKeyRef) {
          def secret = envVar.valueFrom.secretKeyRef
          source.secretSource = new KubernetesSecretSource(key: secret.key, secretName: secret.name)
        } else {
          return null
        }
        result.envSource = source
      } else {
        return null
      }
      return result
    } - null

    containerDescription.volumeMounts = container?.volumeMounts?.collect { volumeMount ->
      new KubernetesVolumeMount(name: volumeMount.name, readOnly: volumeMount.readOnly, mountPath: volumeMount.mountPath)
    }

    containerDescription.args = container?.args ?: []
    containerDescription.command = container?.command ?: []

    return containerDescription
  }

  static KubernetesVolumeSource fromVolume(Volume volume) {
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
    } else if (volume.secret) {
      res.type = KubernetesVolumeSourceType.SECRET
      res.secret = new KubernetesSecretVolumeSource(secretName: volume.secret.secretName)
    } else {
      res.type = KubernetesVolumeSourceType.UNSUPPORTED
    }

    return res
  }

  static RunKubernetesJobDescription fromJob(Job job) {
    def deployDescription = new RunKubernetesJobDescription()
    def parsedName = Names.parseName(job?.metadata?.name)

    deployDescription.application = parsedName?.app
    deployDescription.stack = parsedName?.stack
    deployDescription.freeFormDetails = parsedName?.detail
    deployDescription.loadBalancers = KubernetesUtil?.getLoadBalancers(job)
    deployDescription.namespace = job?.metadata?.namespace
    deployDescription.completions = job?.spec?.completions
    deployDescription.parallelism = job?.spec?.parallelism

    deployDescription.volumeSources = job?.spec?.template?.spec?.volumes?.collect {
      fromVolume(it)
    } ?: []

    deployDescription.containers = job?.spec?.template?.spec?.containers?.collect {
      fromContainer(it)
    } ?: []

    return deployDescription
  }

  static DeployKubernetesAtomicOperationDescription fromReplicaSet(ReplicaSet replicaSet) {
    def deployDescription = new DeployKubernetesAtomicOperationDescription()
    def parsedName = Names.parseName(replicaSet?.metadata?.name)

    deployDescription.application = parsedName?.app
    deployDescription.stack = parsedName?.stack
    deployDescription.freeFormDetails = parsedName?.detail
    deployDescription.loadBalancers = KubernetesUtil?.getLoadBalancers(replicaSet)
    deployDescription.namespace = replicaSet?.metadata?.namespace
    deployDescription.targetSize = replicaSet?.spec?.replicas
    deployDescription.securityGroups = []

    deployDescription.volumeSources = replicaSet?.spec?.template?.spec?.volumes?.collect {
      fromVolume(it)
    } ?: []

    deployDescription.containers = replicaSet?.spec?.template?.spec?.containers?.collect {
      fromContainer(it)
    } ?: []

    return deployDescription
  }

  static DeployKubernetesAtomicOperationDescription fromReplicationController(ReplicationController replicationController) {
    def deployDescription = new DeployKubernetesAtomicOperationDescription()
    def parsedName = Names.parseName(replicationController?.metadata?.name)

    deployDescription.application = parsedName?.app
    deployDescription.stack = parsedName?.stack
    deployDescription.freeFormDetails = parsedName?.detail
    deployDescription.loadBalancers = KubernetesUtil?.getLoadBalancers(replicationController)
    deployDescription.namespace = replicationController?.metadata?.namespace
    deployDescription.targetSize = replicationController?.spec?.replicas
    deployDescription.securityGroups = []

    deployDescription.volumeSources = replicationController?.spec?.template?.spec?.volumes?.collect {
      fromVolume(it)
    } ?: []

    deployDescription.containers = replicationController?.spec?.template?.spec?.containers?.collect {
      fromContainer(it)
    } ?: []

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
    kubernetesHttpGetAction.httpHeaders = httpGet.httpHeaders?.collect() {
      new KeyValuePair(name: it.name, value: it.value)
    }
    return kubernetesHttpGetAction
  }
}
