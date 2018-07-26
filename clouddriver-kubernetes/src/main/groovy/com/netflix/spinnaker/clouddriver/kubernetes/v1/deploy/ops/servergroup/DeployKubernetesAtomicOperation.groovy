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

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.kubernetes.v1.api.KubernetesApiConverter
import com.netflix.spinnaker.clouddriver.kubernetes.v1.api.KubernetesClientApiConverter
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.autoscaler.KubernetesAutoscalerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.DeployKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.exception.KubernetesClientOperationException
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.exception.KubernetesOperationException
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.exception.KubernetesResourceNotFoundException
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.HorizontalPodAutoscalerBuilder
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder
import io.fabric8.kubernetes.api.model.apps.DeploymentFluentImpl
import io.fabric8.kubernetes.api.model.apps.DoneableDeployment
import io.fabric8.kubernetes.api.model.apps.ReplicaSetBuilder
import io.kubernetes.client.models.V1Pod

class DeployKubernetesAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "DEPLOY"

  DeployKubernetesAtomicOperation(DeployKubernetesAtomicOperationDescription description) {
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  final DeployKubernetesAtomicOperationDescription description

  /*
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "securityGroups": [], "loadBalancers":  [],  "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx" } } ], "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "loadBalancers":  ["frontend-lb"],  "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx", "tag": "latest", "registry": "index.docker.io" }, "ports": [ { "containerPort": "80", "hostPort": "80", "name": "http", "protocol": "TCP", "hostIp": "10.239.18.11" } ] } ], "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "loadBalancers":  [],  "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx", "tag": "latest", "registry": "index.docker.io" }, "livenessProbe": { "handler": { "type": "EXEC", "execAction": { "commands": [ "ls" ] } } } } ], "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "loadBalancers":  [],  "volumeSources": [ { "name": "storage", "type": "EMPTYDIR", "emptyDir": {} } ], "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx", "tag": "latest", "registry": "index.docker.io" }, "volumeMounts": [ { "name": "storage", "mountPath": "/storage", "readOnly": false } ] } ], "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "securityGroups": [], "loadBalancers":  [],  "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx" } } ], "capacity": { "min": 1, "max": 5 }, "scalingPolicy": { "cpuUtilization": { "target": 40 } }, "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "securityGroups": [], "loadBalancers":  [],  "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx" } } ], "account":  "my-kubernetes-account", "deployment": { "enabled": "true" } } } ]' localhost:7002/kubernetes/ops
   */

  @Override
  DeploymentResult operate(List priorOutputs) {

    HasMetadata serverGroup = deployDescription()
    DeploymentResult deploymentResult = new DeploymentResult()
    deploymentResult.serverGroupNames = Arrays.asList("${serverGroup.metadata.namespace}:${serverGroup.metadata.name}".toString())
    deploymentResult.serverGroupNameByRegion[serverGroup.metadata.namespace] = serverGroup.metadata.name
    return deploymentResult
  }

  HasMetadata deployDescription() {
    task.updateStatus BASE_PHASE, "Initializing creation of replica set."
    task.updateStatus BASE_PHASE, "Looking up provided namespace..."

    def credentials = description.credentials.credentials

    /*
     * Prefer the source namespace when it is available
     * Fall back on the current namespace and use 'default' if both are not set.
     */
    def namespaceToValidate
    if (description.source?.namespace) {
      namespaceToValidate = description.source.namespace
    }
    else if (description.namespace) {
      namespaceToValidate = description.namespace
    }
    else { namespaceToValidate = "default" }

    def namespace = KubernetesUtil.validateNamespace(credentials, namespaceToValidate)
    description.imagePullSecrets = credentials.imagePullSecrets[namespace]

    def serverGroupNameResolver = new KubernetesServerGroupNameResolver(namespace, credentials)
    def clusterName = serverGroupNameResolver.combineAppStackDetail(description.application, description.stack, description.freeFormDetails)

    if (description.kind) {
      return deployController(credentials, serverGroupNameResolver, clusterName, namespace)
    }

    task.updateStatus BASE_PHASE, "Looking up next sequence index for cluster ${clusterName}..."

    String replicaSetName
    if (description.sequence) {
      replicaSetName = serverGroupNameResolver.generateServerGroupName(description.application, description.stack, description.freeFormDetails, description.sequence, false)
    } else {
      replicaSetName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.freeFormDetails, false)
    }

    task.updateStatus BASE_PHASE, "Replica set name chosen to be ${replicaSetName}."
    def hasDeployment = KubernetesApiConverter.hasDeployment(description)
    def replicaSet

    if (description.source?.useSourceCapacity) {
      task.updateStatus BASE_PHASE, "Searching for ancestor server group ${description.source.serverGroupName}..."
      def ancestorServerGroup = credentials.apiAdaptor.getReplicationController(namespace, description.source.serverGroupName)
      if (!ancestorServerGroup) {
        ancestorServerGroup = credentials.apiAdaptor.getReplicaSet(namespace, description.source.serverGroupName)
      }
      if (!ancestorServerGroup) {
        throw new KubernetesResourceNotFoundException("Source Server Group: $description.source.serverGroupName does not exist in Namespace: ${namespace}!")
      }
      task.updateStatus BASE_PHASE, "Ancestor Server Group Located: ${ancestorServerGroup}"

      description.targetSize = ancestorServerGroup.spec?.replicas
      task.updateStatus BASE_PHASE, "Building replica set..."
      replicaSet = KubernetesApiConverter.toReplicaSet(new ReplicaSetBuilder(), description, replicaSetName)
      if (hasDeployment) {
        replicaSet.spec.replicas = 0
      }
    }
    //User might set targetSize and useSourceCapacity to false
    else {
      task.updateStatus BASE_PHASE, "Building replica set..."
      replicaSet = KubernetesApiConverter.toReplicaSet(new ReplicaSetBuilder(), description, replicaSetName)

      if (hasDeployment) {
        replicaSet.spec.replicas = 0
      }
    }

    replicaSet = credentials.apiAdaptor.createReplicaSet(namespace, replicaSet)
    task.updateStatus BASE_PHASE, "Deployed replica set ${replicaSet.metadata.name}"

    if (hasDeployment) {
      if (!credentials.apiAdaptor.getDeployment(namespace, clusterName)) {
        task.updateStatus BASE_PHASE, "Building deployment..."
        credentials.apiAdaptor.createDeployment(namespace, ((DeploymentBuilder) KubernetesApiConverter.toDeployment((DeploymentFluentImpl) new DeploymentBuilder(), description, replicaSetName)).build())
      } else {
        task.updateStatus BASE_PHASE, "Updating deployment..."
        ((DoneableDeployment) KubernetesApiConverter.toDeployment((DeploymentFluentImpl) credentials.apiAdaptor.editDeployment(namespace, clusterName),
          description,
          replicaSetName)).done()
      }
      task.updateStatus BASE_PHASE, "Configured deployment $clusterName"
    }

    if (description.scalingPolicy) {
      task.updateStatus BASE_PHASE, "Attaching a horizontal pod autoscaler..."

      def name = hasDeployment ? clusterName : replicaSetName
      def kind = hasDeployment ? KubernetesUtil.DEPLOYMENT_KIND : KubernetesUtil.SERVER_GROUP_KIND
      def version = hasDeployment ? credentials.apiAdaptor.getDeployment(namespace, clusterName).getApiVersion() : replicaSet.getApiVersion()
      def autoscaler = ((HorizontalPodAutoscalerBuilder) KubernetesApiConverter.toAutoscaler(new HorizontalPodAutoscalerBuilder(), new KubernetesAutoscalerDescription(replicaSetName, description), name, kind, version)).build()

      if (credentials.apiAdaptor.getAutoscaler(namespace, name)) {
        credentials.apiAdaptor.deleteAutoscaler(namespace, name)
      }

      credentials.apiAdaptor.createAutoscaler(namespace, autoscaler)
    }

    return replicaSet
  }

  HasMetadata deployController(KubernetesV1Credentials credentials, def serverGroupNameResolver, String clusterName, String namespace) {
    def controllerSet
    def isUpdateControllerEnabled = KubernetesClientApiConverter.isUpdateControllerEnabled(description)
    def controllerName
    if (KubernetesClientApiConverter.validateSequence(description)) {
      controllerName = serverGroupNameResolver.generateServerGroupName(description.application, description.stack, description.freeFormDetails, description.sequence.intValue(), false)
    } else {
      controllerName = clusterName
    }

    switch(description.kind) {
      case KubernetesUtil.CONTROLLERS_STATEFULSET_KIND:
        controllerSet = deployStatefulSet(credentials, controllerName, namespace, isUpdateControllerEnabled)
        break
      case KubernetesUtil.CONTROLLERS_DAEMONSET_KIND:
        controllerSet = deployDaemonSet(credentials, controllerName, namespace, isUpdateControllerEnabled)
        break
      default:
        throw new KubernetesOperationException("Controller type $description.kind is not support.")
    }

    return KubernetesClientApiConverter.toKubernetesController(controllerSet)
  }

  def deployStatefulSet(KubernetesV1Credentials credentials, String controllerName, String namespace,  Boolean isUpdateControllerEnabled) {
    task.updateStatus BASE_PHASE, "Building stateful set..."
    def controllerSet = KubernetesClientApiConverter.toStatefulSet(description, controllerName)
    if (isUpdateControllerEnabled) {
      def deployedControllerSet = credentials.clientApiAdaptor.getStatefulSet(controllerName, namespace)
      if (deployedControllerSet) {
        task.updateStatus BASE_PHASE, "Update stateful set ${controllerName}"
        controllerSet = credentials.clientApiAdaptor.replaceStatfulSet(controllerName, namespace, controllerSet)
        if (description.updateController?.updateStrategy?.type.name() == "Recreate") {
          deletePods(credentials, namespace, controllerSet)
        }
      } else {
        task.updateStatus BASE_PHASE, "Deployed stateful set ${controllerName}"
        controllerSet = credentials.clientApiAdaptor.createStatfulSet(namespace, controllerSet)
      }
    } else {
      task.updateStatus BASE_PHASE, "Deployed stateful set ${controllerName}"
      controllerSet = credentials.clientApiAdaptor.createStatfulSet(namespace, controllerSet)
    }

    if (description.scalingPolicy) {
      task.updateStatus BASE_PHASE, "Attaching a horizontal pod autoscaler..."

      def autoscaler = KubernetesClientApiConverter.toAutoscaler(new KubernetesAutoscalerDescription(controllerName, description), controllerName, description.kind)

      if (credentials.clientApiAdaptor.getAutoscaler(namespace, controllerName)) {
        credentials.clientApiAdaptor.deleteAutoscaler(namespace, controllerName, null, null, null, true)
      }
      credentials.clientApiAdaptor.createAutoscaler(namespace, autoscaler)
    }

    return controllerSet
  }

  def deployDaemonSet(KubernetesV1Credentials credentials, String controllerName, String namespace, Boolean isUpdateControllerEnabled) {
    task.updateStatus BASE_PHASE, "Building daemon set..."
    def controllerSet = KubernetesClientApiConverter.toDaemonSet(description, controllerName)
    if (isUpdateControllerEnabled) {
      def deployedControllerSet = credentials.clientApiAdaptor.getDaemonSet(controllerName, namespace)
      if (deployedControllerSet) {
        task.updateStatus BASE_PHASE, "Update daemon set ${controllerName}"
        controllerSet = credentials.clientApiAdaptor.replaceDaemonSet(controllerName, namespace, controllerSet)
        if (description.updateController?.updateStrategy?.type.name() == "Recreate") {
          deletePods(credentials, namespace, controllerSet)
        }
      } else {
        task.updateStatus BASE_PHASE, "Deployed daemon set ${controllerName}"
        controllerSet = credentials.clientApiAdaptor.createDaemonSet(namespace, controllerSet)
      }
    } else {
      task.updateStatus BASE_PHASE, "Deployed daemon set ${controllerName}"
      controllerSet = credentials.clientApiAdaptor.createDaemonSet(namespace, controllerSet)
    }

    return controllerSet
  }

  void deletePods(KubernetesV1Credentials credentials, String namespace, def controllerSet) {
    Map<String, String> podNameList = new LinkedHashMap<String, String>()

    credentials.clientApiAdaptor.getPods(namespace, controllerSet.metadata.labels).items.forEach({ item ->
      podNameList.put(item.metadata.name, item.metadata.uid)
    })

    def getPodState = null
    podNameList.toSorted(Map.Entry.comparingByKey().reversed()).forEach ({ k, v ->
      credentials.clientApiAdaptor.deletePod(k, namespace, null, null, null, false)

      getPodState = {
        V1Pod pod = credentials.clientApiAdaptor.getPodStatus(k, namespace)
        if (pod) {
          if (v != pod.metadata?.uid && pod.status?.phase == "Running") {
            return true
          }
        } else {
          if (controllerSet.kind == KubernetesUtil.CONTROLLERS_DAEMONSET_KIND) {
            return true
          }
        }
        return false
      }

      if (!credentials.clientApiAdaptor.blockUntilResourceConsistent(getPodState)) {
        throw new KubernetesClientOperationException("Failed to launch a new pod($k) for ServerGroup $controllerSet.metadata.name in $namespace.")
      }
    })
  }
}
