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
import com.netflix.spinnaker.clouddriver.kubernetes.api.KubernetesApiConverter
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.DeployKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.api.model.extensions.LabelSelectorRequirement
import io.fabric8.kubernetes.api.model.extensions.LabelSelectorRequirementBuilder
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetBuilder

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

    ReplicaSet replicaSet = deployDescription()
    DeploymentResult deploymentResult = new DeploymentResult()
    deploymentResult.serverGroupNames = Arrays.asList("${replicaSet.metadata.namespace}:${replicaSet.metadata.name}".toString())
    deploymentResult.serverGroupNameByRegion[replicaSet.metadata.namespace] = replicaSet.metadata.name
    return deploymentResult
  }

  ReplicaSet deployDescription() {
    task.updateStatus BASE_PHASE, "Initializing creation of replica set."
    task.updateStatus BASE_PHASE, "Looking up provided namespace..."

    def credentials = description.credentials.credentials
    def namespace = KubernetesUtil.validateNamespace(credentials, description.namespace)

    def serverGroupNameResolver = new KubernetesServerGroupNameResolver(namespace, credentials)
    def clusterName = serverGroupNameResolver.combineAppStackDetail(description.application, description.stack, description.freeFormDetails)

    task.updateStatus BASE_PHASE, "Looking up next sequence index for cluster ${clusterName}..."
    def replicaSetName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.freeFormDetails, false)
    task.updateStatus BASE_PHASE, "Replica set name chosen to be ${replicaSetName}."

    def replicaSetBuilder = new ReplicaSetBuilder().withNewMetadata().withName(replicaSetName).endMetadata()

    replicaSetBuilder = replicaSetBuilder.withNewSpec().withNewSelector().withMatchLabels(
      [(KubernetesUtil.REPLICATION_CONTROLLER_LABEL): replicaSetName]).endSelector()

    task.updateStatus BASE_PHASE, "Setting target size to ${description.targetSize}..."

    replicaSetBuilder = replicaSetBuilder.withReplicas(description.targetSize)
        .withNewTemplate()
        .withNewMetadata()

    task.updateStatus BASE_PHASE, "Setting replica set spec labels..."

    replicaSetBuilder = replicaSetBuilder.addToLabels(KubernetesUtil.REPLICATION_CONTROLLER_LABEL, replicaSetName)

    for (def loadBalancer : description.loadBalancers) {
      replicaSetBuilder = replicaSetBuilder.addToLabels(KubernetesUtil.loadBalancerKey(loadBalancer), "true")
    }

    replicaSetBuilder = replicaSetBuilder.endMetadata().withNewSpec()

    if (description.restartPolicy) {
      replicaSetBuilder.withRestartPolicy(description.restartPolicy)
    }

    task.updateStatus BASE_PHASE, "Adding image pull secrets... "
    replicaSetBuilder = replicaSetBuilder.withImagePullSecrets()

    for (def imagePullSecret : credentials.imagePullSecrets[namespace]) {
      replicaSetBuilder = replicaSetBuilder.addNewImagePullSecret(imagePullSecret)
    }

    if (description.volumeSources) {
      def volumeSources = description.volumeSources.findResults { volumeSource ->
        KubernetesApiConverter.toVolumeSource(volumeSource)
      }

      replicaSetBuilder = replicaSetBuilder.withVolumes(volumeSources)
    }

    def containers = description.containers.collect { container ->
      KubernetesApiConverter.toContainer(container)
    }

    replicaSetBuilder = replicaSetBuilder.withContainers(containers)

    replicaSetBuilder = replicaSetBuilder.endSpec().endTemplate().endSpec()

    task.updateStatus BASE_PHASE, "Sending replica set spec to the Kubernetes master."
	  ReplicaSet replicaSet = credentials.apiAdaptor.createReplicaSet(namespace, replicaSetBuilder.build())

    task.updateStatus BASE_PHASE, "Finished creating replica set ${replicaSet.metadata.name}."

    return replicaSet
  }
}
