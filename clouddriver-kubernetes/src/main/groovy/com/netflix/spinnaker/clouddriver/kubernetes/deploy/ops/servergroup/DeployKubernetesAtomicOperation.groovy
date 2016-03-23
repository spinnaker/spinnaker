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

package com.netflix.spinnaker.clouddriver.kubernetes.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.DeployKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesHandlerType
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesStorageMediumType
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesVolumeSourceType
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import io.fabric8.kubernetes.api.model.*

class DeployKubernetesAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "DEPLOY"

  DeployKubernetesAtomicOperation(DeployKubernetesAtomicOperationDescription description) {
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  DeployKubernetesAtomicOperationDescription description

  /*
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "securityGroups": [], "loadBalancers":  [],  "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx" } } ], "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "loadBalancers":  ["frontend-lb"],  "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx", "tag": "latest", "registry": "index.docker.io" }, "ports": [ { "containerPort": "80", "hostPort": "80", "name": "http", "protocol": "TCP", "hostIp": "10.239.18.11" } ] } ], "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "loadBalancers":  [],  "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx", "tag": "latest", "registry": "index.docker.io" }, "livenessProbe": { "handler": { "type": "EXEC", "execAction": { "commands": [ "ls" ] } } } } ], "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "loadBalancers":  [],  "volumeSources": [ { "name": "storage", "type": "EMPTYDIR", "emptyDir": {} } ], "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx", "tag": "latest", "registry": "index.docker.io" }, "volumeMounts": [ { "name": "storage", "mountPath": "/storage", "readOnly": false } ] } ], "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
  */
  @Override
  DeploymentResult operate(List priorOutputs) {

    ReplicationController replicationController = deployDescription()
    DeploymentResult deploymentResult = new DeploymentResult()
    deploymentResult.serverGroupNames = Arrays.asList("${replicationController.metadata.namespace}:${replicationController.metadata.name}".toString())
    deploymentResult.serverGroupNameByRegion[replicationController.metadata.namespace] = replicationController.metadata.name
    return deploymentResult
  }

  ReplicationController deployDescription() {
    task.updateStatus BASE_PHASE, "Initializing creation of replication controller."
    task.updateStatus BASE_PHASE, "Looking up provided namespace..."

    def credentials = description.credentials
    def namespace = KubernetesUtil.validateNamespace(credentials, description.namespace)

    def clusterName = KubernetesUtil.combineAppStackDetail(description.application, description.stack, description.freeFormDetails)
    task.updateStatus BASE_PHASE, "Looking up next sequence index..."
    def sequenceIndex = KubernetesUtil.getNextSequence(clusterName, namespace, credentials)
    task.updateStatus BASE_PHASE, "Sequence index chosen to be ${sequenceIndex}."
    def replicationControllerName = String.format("%s-v%s", clusterName, sequenceIndex)
    task.updateStatus BASE_PHASE, "Replication controller name chosen to be ${replicationControllerName}."

    def replicationControllerBuilder = new ReplicationControllerBuilder().withNewMetadata().withName(replicationControllerName).endMetadata()

    replicationControllerBuilder = replicationControllerBuilder.withNewSpec().addToSelector(KubernetesUtil.REPLICATION_CONTROLLER_LABEL, replicationControllerName)

    task.updateStatus BASE_PHASE, "Setting target size to ${description.targetSize}..."

    replicationControllerBuilder = replicationControllerBuilder.withReplicas(description.targetSize)
        .withNewTemplate()
        .withNewMetadata()

    task.updateStatus BASE_PHASE, "Setting replication controller spec labels..."

    replicationControllerBuilder = replicationControllerBuilder.addToLabels(KubernetesUtil.REPLICATION_CONTROLLER_LABEL, replicationControllerName)

    for (def loadBalancer : description.loadBalancers) {
      replicationControllerBuilder = replicationControllerBuilder.addToLabels(KubernetesUtil.loadBalancerKey(loadBalancer), "true")
    }

    replicationControllerBuilder = replicationControllerBuilder.endMetadata().withNewSpec()

    if (description.restartPolicy) {
      replicationControllerBuilder.withRestartPolicy(description.restartPolicy)
    }

    task.updateStatus BASE_PHASE, "Adding image pull secrets... "
    replicationControllerBuilder = replicationControllerBuilder.withImagePullSecrets()

    for (def imagePullSecret : credentials.imagePullSecrets[namespace]) {
      replicationControllerBuilder = replicationControllerBuilder.addNewImagePullSecret(imagePullSecret)
    }


    if (description.volumeSources) {
      task.updateStatus BASE_PHASE, "Adding pod volume sources... "
      def volumeSources = description.volumeSources.findResults { volumeSource ->
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
            def res = new HostPathVolumeSourceBuilder().withPath(volume.hostPath.path)
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

      replicationControllerBuilder = replicationControllerBuilder.withVolumes(volumeSources)
    }

    for (def container : description.containers) {
      KubernetesUtil.normalizeImageDescription(container.imageDescription)
      def imageId = KubernetesUtil.getImageId(container.imageDescription)
      task.updateStatus BASE_PHASE, "Adding container ${container.name} with image ${imageId}..."
      replicationControllerBuilder = replicationControllerBuilder.addNewContainer().withName(container.name).withImage(imageId)

      if (container.ports) {
        task.updateStatus BASE_PHASE, "Setting container ports..."

        container.ports.forEach {
          replicationControllerBuilder = replicationControllerBuilder.addNewPort()
          if (it.name) {
            replicationControllerBuilder = replicationControllerBuilder.withName(it.name)
          }

          if (it.containerPort) {
            replicationControllerBuilder = replicationControllerBuilder.withContainerPort(it.containerPort)
          }

          if (it.hostPort) {
            replicationControllerBuilder = replicationControllerBuilder.withHostPort(it.hostPort)
          }

          if (it.protocol) {
            replicationControllerBuilder = replicationControllerBuilder.withProtocol(it.protocol)
          }

          if (it.hostIp) {
            replicationControllerBuilder = replicationControllerBuilder.withHostIP(it.hostIp)
          }
          replicationControllerBuilder = replicationControllerBuilder.endPort()
        }
      }

      [liveness: container.livenessProbe, readiness: container.readinessProbe].each { k, v ->
        def probe = v
        if (probe) {
          switch (k) {
            case 'liveness':
              task.updateStatus BASE_PHASE, 'Adding liveness probe...'
              replicationControllerBuilder = replicationControllerBuilder.withNewLivenessProbe()
              break
            case 'readiness':
              task.updateStatus BASE_PHASE, 'Adding readiness probe...'
              replicationControllerBuilder = replicationControllerBuilder.withNewReadinessProbe()
              break
          }

          replicationControllerBuilder = replicationControllerBuilder.withInitialDelaySeconds(probe.initialDelaySeconds)

          if (probe.timeoutSeconds) {
            replicationControllerBuilder = replicationControllerBuilder.withTimeoutSeconds(probe.timeoutSeconds)
          }

          if (probe.failureThreshold) {
            replicationControllerBuilder = replicationControllerBuilder.withFailureThreshold(probe.failureThreshold)
          }

          if (probe.successThreshold) {
            replicationControllerBuilder = replicationControllerBuilder.withSuccessThreshold(probe.successThreshold)
          }

          if (probe.periodSeconds) {
            replicationControllerBuilder = replicationControllerBuilder.withPeriodSeconds(probe.periodSeconds)
          }

          task.updateStatus BASE_PHASE, 'Adding probe handler..'
          switch (probe.handler.type) {
            case KubernetesHandlerType.EXEC:
              replicationControllerBuilder = replicationControllerBuilder.withNewExec().withCommand(probe.handler.execAction.commands).endExec()
              break

            case KubernetesHandlerType.TCP:
              replicationControllerBuilder = replicationControllerBuilder.withNewTcpSocket().withNewPort(probe.handler.tcpSocketAction.port).endTcpSocket()
              break

            case KubernetesHandlerType.HTTP:
              replicationControllerBuilder = replicationControllerBuilder.withNewHttpGet()
              def get = probe.handler.httpGetAction

              if (get.host) {
                replicationControllerBuilder = replicationControllerBuilder.withHost(get.host)
              }

              if (get.path) {
                replicationControllerBuilder = replicationControllerBuilder.withPath(get.path)
              }

              replicationControllerBuilder = replicationControllerBuilder.withPort(new IntOrString(get.port))

              if (get.uriScheme) {
                replicationControllerBuilder = replicationControllerBuilder.withScheme(get.uriScheme)
              }

              replicationControllerBuilder = replicationControllerBuilder.endHttpGetAction()
              break
          }

          switch (k) {
            case 'liveness':
              replicationControllerBuilder = replicationControllerBuilder.endLivenessProbe()
              break
            case 'readiness':
              replicationControllerBuilder = replicationControllerBuilder.endReadinessProbe()
              break
          }
        }
      }

      replicationControllerBuilder = replicationControllerBuilder.withNewResources()
      if (container.requests) {
        def requests = [:]

        if (container.requests.memory) {
          requests.memory = container.requests.memory
        }

        if (container.requests.cpu) {
          requests.cpu = container.requests.cpu
        }
        task.updateStatus BASE_PHASE, "Setting resource requests..."
        replicationControllerBuilder = replicationControllerBuilder.withRequests(requests)
      }

      if (container.limits) {
        def limits = [:]

        if (container.limits.memory) {
          limits.memory = container.limits.memory
        }

        if (container.limits.cpu) {
          limits.cpu = container.limits.cpu
        }

        task.updateStatus BASE_PHASE, "Setting resource limits..."
        replicationControllerBuilder = replicationControllerBuilder.withLimits(limits)
      }

      replicationControllerBuilder = replicationControllerBuilder.endResources()

      if (container.volumeMounts) {
        task.updateStatus BASE_PHASE, "Adding container volume mounts..."

        def volumeMounts = container.volumeMounts.collect { mount ->
          def res = new VolumeMountBuilder()

          return res.withMountPath(mount.mountPath)
              .withName(mount.name)
              .withReadOnly(mount.readOnly)
              .build()
        }

        replicationControllerBuilder = replicationControllerBuilder.withVolumeMounts(volumeMounts)
      }

      if (container.envVars) {
        task.updateStatus BASE_PHASE, "Setting container env vars..."

        def envVars = container.envVars.collect { envVar ->
          def res = new EnvVarBuilder()

          return res.withName(envVar.name)
              .withValue(envVar.value)
              .build()
        }

        replicationControllerBuilder = replicationControllerBuilder.withEnv(envVars)
      }

      if (container.command) {
        task.updateStatus BASE_PHASE, "Setting container command..."

        replicationControllerBuilder = replicationControllerBuilder.withCommand(container.command)
      }

      if (container.args) {
        task.updateStatus BASE_PHASE, "Setting container args..."

        replicationControllerBuilder = replicationControllerBuilder.withArgs(container.args)
      }

      replicationControllerBuilder = replicationControllerBuilder.endContainer()
      task.updateStatus BASE_PHASE, "Finished adding container ${container.name}."
    }

    replicationControllerBuilder = replicationControllerBuilder.endSpec().endTemplate().endSpec()

    task.updateStatus BASE_PHASE, "Sending replication controller spec to the Kubernetes master."
	  ReplicationController replicationController = credentials.apiAdaptor.createReplicationController(namespace, replicationControllerBuilder.build())

    task.updateStatus BASE_PHASE, "Finished creating replication controller ${replicationController.metadata.name}."

    return replicationController
  }
}
