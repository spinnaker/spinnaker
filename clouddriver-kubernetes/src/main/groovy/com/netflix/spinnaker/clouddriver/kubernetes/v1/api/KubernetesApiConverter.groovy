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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.api

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.autoscaler.KubernetesAutoscalerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.loadbalancer.KubernetesLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.loadbalancer.KubernetesNamedServicePort
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.securitygroup.KubernetesHttpIngressPath
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.securitygroup.KubernetesHttpIngressRuleValue
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.securitygroup.KubernetesIngressBackend
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.securitygroup.KubernetesIngressRule
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.securitygroup.KubernetesIngressRuleValue
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.securitygroup.KubernetesIngressTlS
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.securitygroup.KubernetesSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.Capacity
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.DeployKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KeyValuePair
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesAwsElasticBlockStoreVolumeSource
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesCapabilities
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesConfigMapEnvSource
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesConfigMapSource
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesConfigMapVolumeSource
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesContainerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesContainerPort
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesCpuUtilization
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesDeployment
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesEmptyDir
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesEnvFromSource
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesEnvVar
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesEnvVarSource
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesExecAction
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesFieldRefSource
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesHandler
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesHandlerType
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesHostPath
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesHttpGetAction
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesKeyToPath
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesLifecycle
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesNfsVolumeSource
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesPersistentVolumeClaim
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesProbe
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesPullPolicy
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesResourceDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesResourceFieldRefSource
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesRollingUpdate
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesScalingPolicy
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesSeLinuxOptions
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesSecretEnvSource
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesSecretSource
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesSecretVolumeSource
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesSecurityContext
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesStorageMediumType
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesStrategy
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesTcpSocketAction
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesToleration
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesVolumeMount
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesVolumeSource
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesVolumeSourceType
import io.fabric8.kubernetes.api.model.AWSElasticBlockStoreVolumeSourceBuilder
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.ContainerBuilder
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSourceBuilder
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder
import io.fabric8.kubernetes.api.model.EnvVarBuilder
import io.fabric8.kubernetes.api.model.ExecAction
import io.fabric8.kubernetes.api.model.ExecActionBuilder
import io.fabric8.kubernetes.api.model.HTTPGetAction
import io.fabric8.kubernetes.api.model.HTTPGetActionBuilder
import io.fabric8.kubernetes.api.model.HTTPHeaderBuilder
import io.fabric8.kubernetes.api.model.Handler
import io.fabric8.kubernetes.api.model.HandlerBuilder
import io.fabric8.kubernetes.api.model.HorizontalPodAutoscaler
import io.fabric8.kubernetes.api.model.HorizontalPodAutoscalerBuilder
import io.fabric8.kubernetes.api.model.HorizontalPodAutoscalerFluentImpl
import io.fabric8.kubernetes.api.model.HostPathVolumeSourceBuilder
import io.fabric8.kubernetes.api.model.IntOrString
import io.fabric8.kubernetes.api.model.KeyToPath
import io.fabric8.kubernetes.api.model.NFSVolumeSourceBuilder
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSourceBuilder
import io.fabric8.kubernetes.api.model.PodTemplateSpec
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder
import io.fabric8.kubernetes.api.model.Probe
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.SecretVolumeSourceBuilder
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.TCPSocketAction
import io.fabric8.kubernetes.api.model.TCPSocketActionBuilder
import io.fabric8.kubernetes.api.model.Toleration
import io.fabric8.kubernetes.api.model.Volume
import io.fabric8.kubernetes.api.model.VolumeMountBuilder
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.DeploymentFluentImpl
import io.fabric8.kubernetes.api.model.extensions.Ingress
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.kubernetes.api.model.apps.ReplicaSetBuilder

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
    securityGroupDescription.annotations = ingress.metadata.annotations
    securityGroupDescription.labels = ingress.metadata.labels

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

    securityGroupDescription.tls = ingress.spec.tls?.collect{ tlsSpecEntry ->
      return new KubernetesIngressTlS(hosts: tlsSpecEntry.hosts, secretName: tlsSpecEntry.secretName)
    }

    securityGroupDescription
  }


  static KubernetesLoadBalancerDescription fromService(Service service, String accountName) {
    if (!service) {
      return null
    }

    def loadBalancerDescription = new KubernetesLoadBalancerDescription()

    loadBalancerDescription.account = accountName
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
    loadBalancerDescription.serviceAnnotations = service.metadata.annotations
    loadBalancerDescription.serviceLabels = service.metadata.labels

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
      case KubernetesVolumeSourceType.EmptyDir:
        def res = new EmptyDirVolumeSourceBuilder()

        switch (volumeSource.emptyDir.medium) {
          case KubernetesStorageMediumType.Memory:
            res = res.withMedium("Memory")
            break

          default:
            res = res.withMedium("") // Empty string is default...
        }

        volume.emptyDir = res.build()
        break

      case KubernetesVolumeSourceType.HostPath:
        def res = new HostPathVolumeSourceBuilder().withPath(volumeSource.hostPath.path)

        volume.hostPath = res.build()
        break

      case KubernetesVolumeSourceType.PersistentVolumeClaim:
        def res = new PersistentVolumeClaimVolumeSourceBuilder()
            .withClaimName(volumeSource.persistentVolumeClaim.claimName)
            .withReadOnly(volumeSource.persistentVolumeClaim.readOnly)

        volume.persistentVolumeClaim = res.build()
        break

      case KubernetesVolumeSourceType.Secret:
        def res = new SecretVolumeSourceBuilder()
            .withSecretName(volumeSource.secret.secretName)

        volume.secret = res.build()
        break

      case KubernetesVolumeSourceType.ConfigMap:
        def res = new ConfigMapVolumeSourceBuilder().withName(volumeSource.configMap.configMapName)
        def items = volumeSource.configMap.items?.collect { KubernetesKeyToPath item ->
          new KeyToPath(key: item.key, path: item.path)
        }

        res = res.withItems(items)
        volume.configMap = res.build()
        break

      case KubernetesVolumeSourceType.AwsElasticBlockStore:
        def res = new AWSElasticBlockStoreVolumeSourceBuilder().withVolumeID(volumeSource.awsElasticBlockStore.volumeId)
        res = res.withFsType(volumeSource.awsElasticBlockStore.fsType)

        if (volumeSource.awsElasticBlockStore.partition) {
          res = res.withPartition(volumeSource.awsElasticBlockStore.partition)
        }

        volume.awsElasticBlockStore = res.build()
        break

      case KubernetesVolumeSourceType.NFS:
        def res = new NFSVolumeSourceBuilder()
            .withServer(volumeSource.nfs.server)
            .withPath(volumeSource.nfs.path)
            .withReadOnly(volumeSource.nfs.readOnly)

        volume.nfs = res.build()
        break

      default:
        return null
    }

    return volume
  }

  static ExecAction toExecAction(KubernetesExecAction action) {
    def execActionBuilder = new ExecActionBuilder()
    execActionBuilder = execActionBuilder.withCommand(action.commands)
    return execActionBuilder.build()
  }

  static TCPSocketAction toTcpSocketAction(KubernetesTcpSocketAction action) {
    def tcpActionBuilder = new TCPSocketActionBuilder()
    tcpActionBuilder = tcpActionBuilder.withNewPort(action.port)
    return tcpActionBuilder.build()
  }

  static HTTPGetAction toHttpGetAction(KubernetesHttpGetAction action) {
    def httpGetActionBuilder = new HTTPGetActionBuilder()

    if (action.host) {
      httpGetActionBuilder = httpGetActionBuilder.withHost(action.host)
    }

    if (action.path) {
      httpGetActionBuilder = httpGetActionBuilder.withPath(action.path)
    }

    httpGetActionBuilder = httpGetActionBuilder.withPort(new IntOrString(action.port))

    if (action.uriScheme) {
      httpGetActionBuilder = httpGetActionBuilder.withScheme(action.uriScheme)
    }

    if (action.httpHeaders) {
      def headers = action.httpHeaders.collect() {
        def builder = new HTTPHeaderBuilder()
        return builder.withName(it.name).withValue(it.value).build()
      }

      httpGetActionBuilder.withHttpHeaders(headers)
    }

    return httpGetActionBuilder.build()
  }

  static Handler toHandler(KubernetesHandler handler) {
    def handlerBuilder = new HandlerBuilder()

    switch (handler.type) {
      case KubernetesHandlerType.EXEC:
        handlerBuilder = handlerBuilder.withExec(toExecAction(handler.execAction))
        break

      case KubernetesHandlerType.TCP:
        handlerBuilder = handlerBuilder.withTcpSocket(toTcpSocketAction(handler.tcpSocketAction))
        break

      case KubernetesHandlerType.HTTP:
        handlerBuilder = handlerBuilder.withHttpGet(toHttpGetAction(handler.httpGetAction))
        break
    }

    return handlerBuilder.build()
  }

  static Container toContainer(KubernetesContainerDescription container) {
    KubernetesUtil.normalizeImageDescription(container.imageDescription)
    def imageId = KubernetesUtil.getImageId(container.imageDescription)
    def containerBuilder = new ContainerBuilder().withName(container.name).withImage(imageId)

    if (container.imagePullPolicy) {
      containerBuilder = containerBuilder.withImagePullPolicy(container.imagePullPolicy.toString())
    } else {
      containerBuilder = containerBuilder.withImagePullPolicy("IfNotPresent")
    }

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

    if (container.securityContext) {
      def securityContext = container.securityContext

      containerBuilder = containerBuilder.withNewSecurityContext()

      containerBuilder.withRunAsNonRoot(securityContext.runAsNonRoot)
        .withRunAsUser(securityContext.runAsUser)
        .withPrivileged(securityContext.privileged)
        .withReadOnlyRootFilesystem(securityContext.readOnlyRootFilesystem)

      if (securityContext.seLinuxOptions) {
        def seLinuxOptions = securityContext.seLinuxOptions

        containerBuilder = containerBuilder.withNewSeLinuxOptions()
          .withUser(seLinuxOptions.user)
          .withRole(seLinuxOptions.role)
          .withType(seLinuxOptions.type)
          .withLevel(seLinuxOptions.level)
          .endSeLinuxOptions()
      }

      if (securityContext.capabilities) {
        def capabilities = securityContext.capabilities

        containerBuilder = containerBuilder.withNewCapabilities()
          .withAdd(capabilities.add)
          .withDrop(capabilities.drop)
          .endCapabilities()
      }

      containerBuilder = containerBuilder.endSecurityContext()
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
          default:
            throw new IllegalArgumentException("Probe type $k not supported")
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
            containerBuilder = containerBuilder.withExec(toExecAction(probe.handler.execAction))
            break

          case KubernetesHandlerType.TCP:
            containerBuilder = containerBuilder.withTcpSocket(toTcpSocketAction(probe.handler.tcpSocketAction))
            break

          case KubernetesHandlerType.HTTP:
            containerBuilder = containerBuilder.withHttpGet(toHttpGetAction(probe.handler.httpGetAction))
            break
        }

        switch (k) {
          case 'liveness':
            containerBuilder = containerBuilder.endLivenessProbe()
            break
          case 'readiness':
            containerBuilder = containerBuilder.endReadinessProbe()
            break
          default:
            throw new IllegalArgumentException("Probe type $k not supported")
        }
      }
    }

    if (container.lifecycle) {
      containerBuilder = containerBuilder.withNewLifecycle()
      if (container.lifecycle.postStart) {
        containerBuilder = containerBuilder.withPostStart(toHandler(container.lifecycle.postStart))
      }
      if (container.lifecycle.preStop) {
        containerBuilder = containerBuilder.withPreStop(toHandler(container.lifecycle.preStop))
      }
      containerBuilder = containerBuilder.endLifecycle()
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
            .withSubPath(mount.subPath)
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
            res = res.withNewConfigMapKeyRef(configMap.key, configMap.configMapName, configMap.optional)
          } else if (envVar.envSource.secretSource) {
            def secret = envVar.envSource.secretSource
            res = res.withNewSecretKeyRef(secret.key, secret.secretName, secret.optional)
          } else if (envVar.envSource.fieldRef) {
            def fieldPath = envVar.envSource.fieldRef.fieldPath
            res = res.withNewFieldRef().withFieldPath(fieldPath).endFieldRef()
          } else if (envVar.envSource.resourceFieldRef) {
            def resource = envVar.envSource.resourceFieldRef.resource
            def containerName = envVar.envSource.resourceFieldRef.containerName
            def divisor = envVar.envSource.resourceFieldRef.divisor
            res = res.withNewResourceFieldRef().withResource(resource)
            res = res.withContainerName(containerName)
            res = res.withNewDivisor(divisor).endResourceFieldRef()
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

    if (container.envFrom) {
      def envFrom = container.envFrom.collect { envFrom ->
        def res = (new EnvFromSourceBuilder()).withPrefix(envFrom.prefix ?: '')
        if (envFrom.configMapRef) {
          def configMapRef = envFrom.configMapRef
          res = res.withNewConfigMapRef(configMapRef.name, configMapRef.optional)
        } else if (envFrom.secretRef) {
          def secretRef = envFrom.secretRef
          res = res.withNewSecretRef(secretRef.name, secretRef.optional)
        } else {
          return null
        }
        return res.build()
      } - null

      containerBuilder.withEnvFrom(envFrom)
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

    if (container.imagePullPolicy) {
      containerDescription.imagePullPolicy = KubernetesPullPolicy.valueOf(container.imagePullPolicy)
    }

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
        } else if (envVar.valueFrom.fieldRef) {
          def fieldPath = envVar.valueFrom.fieldRef.fieldPath;
          source.fieldRef = new KubernetesFieldRefSource(fieldPath: fieldPath)
        } else if (envVar.valueFrom.resourceFieldRef) {
          def resource = envVar.valueFrom.resourceFieldRef.resource
          def containerName = envVar.valueFrom.resourceFieldRef.containerName
          def divisor = envVar.valueFrom.resourceFieldRef.divisor
          source.resourceFieldRef = new KubernetesResourceFieldRefSource(resource: resource,
                                                                         containerName: containerName,
                                                                         divisor: divisor)
        } else {
          return null
        }
        result.envSource = source
      } else {
        return null
      }
      return result
    } - null

    containerDescription.envFrom = container?.envFrom?.collect { envFrom ->
      def result = new KubernetesEnvFromSource(prefix: envFrom.prefix)
      if (envFrom.configMapRef) {
        def source = envFrom.configMapRef
        result.configMapRef = new KubernetesConfigMapEnvSource(name: source.name, optional: source.optional ?: false)
      } else if (envFrom.secretRef) {
        def source = envFrom.secretRef
        result.secretRef = new KubernetesSecretEnvSource(name: source.name, optional: source.optional ?: false)
      } else {
        return null
      }
      return result
    } - null

    containerDescription.volumeMounts = container?.volumeMounts?.collect { volumeMount ->
      new KubernetesVolumeMount(
          name: volumeMount.name,
          readOnly: volumeMount.readOnly,
          mountPath: volumeMount.mountPath,
          subPath: volumeMount.subPath
      )
    }

    containerDescription.args = container?.args ?: []
    containerDescription.command = container?.command ?: []

    return containerDescription
  }

  static KubernetesVolumeSource fromVolume(Volume volume) {
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
      def items = volume.configMap.items?.collect { KeyToPath item ->
        new KubernetesKeyToPath(key: item.key, path: item.path)
      }
      res.configMap = new KubernetesConfigMapVolumeSource(configMapName: volume.configMap.name, items: items)
    } else if (volume.awsElasticBlockStore) {
      res.type = KubernetesVolumeSourceType.AwsElasticBlockStore
      def ebs = volume.awsElasticBlockStore
      res.awsElasticBlockStore = new KubernetesAwsElasticBlockStoreVolumeSource(volumeId: ebs.volumeID,
                                                                                fsType: ebs.fsType,
                                                                                partition: ebs.partition)
    } else if (volume.nfs) {
      res.type = KubernetesVolumeSourceType.NFS
      def nfs = volume.nfs
      res.nfs = new KubernetesNfsVolumeSource(server: nfs.server, path: nfs.path, readOnly: nfs.readOnly)
    } else {
      res.type = KubernetesVolumeSourceType.Unsupported
    }

    return res
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
    deployDescription.replicaSetAnnotations = replicaSet?.metadata?.annotations
    deployDescription.podAnnotations = replicaSet?.spec?.template?.metadata?.annotations

    deployDescription.volumeSources = replicaSet?.spec?.template?.spec?.volumes?.collect {
      fromVolume(it)
    } ?: []

    deployDescription.hostNetwork = replicaSet?.spec?.template?.spec?.hostNetwork

    deployDescription.containers = replicaSet?.spec?.template?.spec?.containers?.collect {
      fromContainer(it)
    } ?: []

    deployDescription.initContainers = replicaSet?.spec?.template?.spec?.initContainers?.collect {
      fromContainer(it)
    } ?: []

    deployDescription.terminationGracePeriodSeconds = replicaSet?.spec?.template?.spec?.terminationGracePeriodSeconds
    deployDescription.serviceAccountName = replicaSet?.spec?.template?.spec?.serviceAccountName

    deployDescription.nodeSelector = replicaSet?.spec?.template?.spec?.nodeSelector
    deployDescription.dnsPolicy = replicaSet?.spec?.template?.spec?.dnsPolicy

    deployDescription.tolerations = replicaSet?.spec?.template?.spec?.tolerations?.collect {
      fromToleration(it)
    } ?: []

    return deployDescription
  }

  static void attachAutoscaler(DeployKubernetesAtomicOperationDescription description, HorizontalPodAutoscaler autoscaler) {
    description.capacity = new Capacity(min: autoscaler.spec.minReplicas,
                                        max: autoscaler.spec.maxReplicas,
                                        desired: description.targetSize)
    def cpuUtilization = new KubernetesCpuUtilization(target: autoscaler.spec.targetCPUUtilizationPercentage)
    description.scalingPolicy = new KubernetesScalingPolicy(cpuUtilization: cpuUtilization)
  }

  static HorizontalPodAutoscalerFluentImpl toAutoscaler(HorizontalPodAutoscalerFluentImpl autoscalerBuilder,
                                                        KubernetesAutoscalerDescription description,
                                                        String resourceName,
                                                        String resourceKind,
                                                        String apiVersion) {
    autoscalerBuilder.withNewMetadata()
      .withName(resourceName)
      .withNamespace(description.namespace)
      .endMetadata()
      .withNewSpec()
      .withMinReplicas(description.capacity.min)
      .withMaxReplicas(description.capacity.max)
      .withTargetCPUUtilizationPercentage(description.scalingPolicy.cpuUtilization.target)
      .withNewScaleTargetRef()
      .withKind(resourceKind)
      .withName(resourceName)
      .withApiVersion(apiVersion)
      .endScaleTargetRef()
      .endSpec()
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

    deployDescription.initContainers = replicationController?.spec?.template?.spec?.initContainers?.collect {
      fromContainer(it)
    } ?: []

    deployDescription.terminationGracePeriodSeconds = replicationController?.spec?.template?.spec?.terminationGracePeriodSeconds
    deployDescription.serviceAccountName = replicationController.spec?.template?.spec?.serviceAccountName

    deployDescription.nodeSelector = replicationController?.spec?.template?.spec?.nodeSelector
    deployDescription.dnsPolicy = replicationController?.spec?.template?.spec?.dnsPolicy

    return deployDescription
  }

  static KubernetesHandler fromHandler(Handler handler) {
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
    kubernetesTcpSocketAction.port = tcpSocket.port?.intVal ?: 0
    return kubernetesTcpSocketAction
  }

  static KubernetesHttpGetAction fromHttpGetAction(HTTPGetAction httpGet) {
    if (!httpGet) {
      return null
    }

    def kubernetesHttpGetAction = new KubernetesHttpGetAction()
    kubernetesHttpGetAction.host = httpGet.host
    kubernetesHttpGetAction.path = httpGet.path
    kubernetesHttpGetAction.port = httpGet.port?.intVal ?: 0
    kubernetesHttpGetAction.uriScheme = httpGet.scheme
    kubernetesHttpGetAction.httpHeaders = httpGet.httpHeaders?.collect() {
      new KeyValuePair(name: it.name, value: it.value)
    }
    return kubernetesHttpGetAction
  }

  static ReplicaSet toReplicaSet(ReplicaSetBuilder serverGroupBuilder,
                                 DeployKubernetesAtomicOperationDescription description,
                                 String replicaSetName) {

    def targetSize
    if (description.targetSize == 0) {
      targetSize = description.targetSize
    }
    else {
      targetSize = description.targetSize ?: description.capacity?.desired
    }

    return serverGroupBuilder.withNewMetadata()
      .withName(replicaSetName)
      .withAnnotations(description.replicaSetAnnotations)
      .endMetadata()
      .withNewSpec()
      .withNewSelector()
      .withMatchLabels(baseServerGroupLabels(description, replicaSetName) + restrictedServerGroupLabels(replicaSetName))
      .endSelector()
      .withReplicas(targetSize)
      .withNewTemplateLike(toPodTemplateSpec(description, replicaSetName))
      .endTemplate()
      .endSpec()
      .build()
  }

  static DeploymentFluentImpl toDeployment(DeploymentFluentImpl serverGroupBuilder,
                                        DeployKubernetesAtomicOperationDescription description,
                                        String replicaSetName) {

    def parsedName = Names.parseName(replicaSetName)
    def targetSize
    if (description.targetSize == 0) {
      targetSize = description.targetSize
    }
    else {
      targetSize = description.targetSize ?: description.capacity?.desired
    }

    def builder = serverGroupBuilder.withNewMetadata()
      .withName(parsedName.cluster)
      .withAnnotations(description.replicaSetAnnotations)
      .endMetadata()
      .withNewSpec()
      .withNewSelector()
      .withMatchLabels(baseServerGroupLabels(description, replicaSetName))
      .endSelector()
      .withReplicas(targetSize)
      .withNewTemplateLike(toPodTemplateSpec(description, replicaSetName))
      .endTemplate()
      .withMinReadySeconds(description.deployment.minReadySeconds)
      .withRevisionHistoryLimit(description.deployment.revisionHistoryLimit)

    if (description.deployment.deploymentStrategy) {
      def strategy = description.deployment.deploymentStrategy
      builder = builder.withNewStrategy()
        .withType(strategy.type.toString())

      if (strategy.rollingUpdate) {
        def rollingUpdate = strategy.rollingUpdate

        builder = builder.withNewRollingUpdate()

        if (rollingUpdate.maxSurge) {
          def maxSurge = rollingUpdate.maxSurge
          if (maxSurge.isInteger()) {
            maxSurge = maxSurge as int
          }
          builder = builder.withNewMaxSurge(maxSurge)
        }

        if (rollingUpdate.maxUnavailable) {
          def maxUnavailable = rollingUpdate.maxUnavailable
          if (maxUnavailable.isInteger()) {
            maxUnavailable = maxUnavailable as int
          }
          builder = builder.withNewMaxUnavailable(maxUnavailable)
        }

        builder = builder.endRollingUpdate()
      }

      builder = builder.endStrategy()
    }

    return builder.endSpec()
  }

  static KubernetesDeployment fromDeployment(Deployment deployment) {
    if (!deployment) {
      return null
    }

    def kubernetesDeployment = new KubernetesDeployment()

    kubernetesDeployment.enabled = true
    kubernetesDeployment.minReadySeconds = deployment.spec.minReadySeconds ?: 0
    kubernetesDeployment.revisionHistoryLimit = deployment.spec.revisionHistoryLimit

    if (deployment.spec.strategy) {
      def strategy = deployment.spec.strategy
      def deploymentStrategy = new KubernetesStrategy()

      deploymentStrategy.type = strategy.type

      if (strategy.rollingUpdate) {
        def update = strategy.rollingUpdate
        def rollingUpdate = new KubernetesRollingUpdate()

        rollingUpdate.maxSurge = update.maxSurge.getStrVal() ?: update.maxSurge.getIntVal().toString()
        rollingUpdate.maxUnavailable = update.maxUnavailable.getStrVal() ?: update.maxUnavailable.getIntVal().toString()

        deploymentStrategy.rollingUpdate = rollingUpdate
      }

      kubernetesDeployment.deploymentStrategy = deploymentStrategy
    }

    return kubernetesDeployment
  }

  static KubernetesToleration fromToleration(Toleration toleration) {
    def t = new KubernetesToleration()
    t.effect = toleration.effect
    t.key = toleration.key
    t.operator = toleration.operator
    t.tolerationSeconds = toleration.tolerationSeconds
    t.value = toleration.value
    return t
  }

  static Toleration toToleration(KubernetesToleration toleration) {
    return new Toleration(
      toleration.effect.toString(),
      toleration.key,
      toleration.operator.toString(),
      toleration.tolerationSeconds,
      toleration.value
    )
  }

  static PodTemplateSpec toPodTemplateSpec(DeployKubernetesAtomicOperationDescription description, String name) {
    def podTemplateSpecBuilder = new PodTemplateSpecBuilder()
      .withNewMetadata()
      .addToLabels(baseServerGroupLabels(description, name) + restrictedServerGroupLabels(name))

    for (def loadBalancer : description.loadBalancers) {
      podTemplateSpecBuilder = podTemplateSpecBuilder.addToLabels(KubernetesUtil.loadBalancerKey(loadBalancer), "true")
    }

    podTemplateSpecBuilder = podTemplateSpecBuilder.withAnnotations(description.podAnnotations)
      .endMetadata()
      .withNewSpec()

    if (description.restartPolicy) {
      podTemplateSpecBuilder.withRestartPolicy(description.restartPolicy)
    }

    if (description.dnsPolicy) {
      podTemplateSpecBuilder.withDnsPolicy(description.dnsPolicy.name())
    }

    if (description.terminationGracePeriodSeconds) {
      podTemplateSpecBuilder.withTerminationGracePeriodSeconds(description.terminationGracePeriodSeconds)
    }

    podTemplateSpecBuilder = podTemplateSpecBuilder.withImagePullSecrets()

    for (def imagePullSecret : description.imagePullSecrets) {
      podTemplateSpecBuilder = podTemplateSpecBuilder.addNewImagePullSecret(imagePullSecret)
    }

    if (description.serviceAccountName) {
      podTemplateSpecBuilder = podTemplateSpecBuilder.withServiceAccountName(description.serviceAccountName)
    }

    podTemplateSpecBuilder = podTemplateSpecBuilder.withNodeSelector(description.nodeSelector)

    if (description.volumeSources) {
      def volumeSources = description.volumeSources.findResults { volumeSource ->
        toVolumeSource(volumeSource)
      }

      podTemplateSpecBuilder = podTemplateSpecBuilder.withVolumes(volumeSources)
    }

    podTemplateSpecBuilder = podTemplateSpecBuilder.withHostNetwork(description.hostNetwork)

    def containers = description.containers.collect { container ->
      toContainer(container)
    }

    podTemplateSpecBuilder = podTemplateSpecBuilder.withContainers(containers)

    def initContainers = description.initContainers.collect { initContainer ->
      toContainer(initContainer)
    }

    podTemplateSpecBuilder = podTemplateSpecBuilder.withInitContainers(initContainers)


    def tolerations = description.tolerations.collect { toleration ->
      toToleration(toleration)
    }

    podTemplateSpecBuilder = podTemplateSpecBuilder.withTolerations(tolerations)

    return podTemplateSpecBuilder.endSpec().build()
  }

  static boolean hasDeployment(DeployKubernetesAtomicOperationDescription description) {
    return description.deployment?.enabled
  }

  /*
   * This represents the set of labels that ties deployments, replica sets, and pods together
   */
  static Map<String, String> baseServerGroupLabels(DeployKubernetesAtomicOperationDescription description, String name) {
    def parsedName = Names.parseName(name)
    return hasDeployment(description) ? [(parsedName.cluster): "true"] : [(name): "true"]
  }

  /*
   * This represents the set of labels that differentiate replica sets from deployments - these are needed so
   * different replica sets under the same deployment don't apply to the same pods
   */
  static Map<String, String> restrictedServerGroupLabels(String name) {
    def parsedName = Names.parseName(name)
    def labels = [
      "version": parsedName.sequence?.toString() ?: "na",
      "app": parsedName.app,
      "cluster": parsedName.cluster,
    ]

    if (parsedName.stack) {
      labels += ["stack": parsedName.stack]
    }

    if (parsedName.detail) {
      labels += ["detail": parsedName.detail]
    }

    labels += [(KubernetesUtil.SERVER_GROUP_LABEL): name]

    return labels
  }
}
