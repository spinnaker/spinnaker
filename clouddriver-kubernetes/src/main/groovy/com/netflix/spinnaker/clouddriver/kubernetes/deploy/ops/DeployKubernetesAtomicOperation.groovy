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
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.DeployKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.exception.KubernetesIllegalArgumentException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder

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
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "securityGroups": [], "loadBalancers":  [],  "containers": [ { "name": "nginx", "image": "nginx" } ], "credentials":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   *
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "securityGroups": ["frontend-sg"], "loadBalancers":  ["frontend-lb"],  "containers": [ { "name": "nginx", "image": "nginx" } ], "imagePullSecrets": ["my-docker-account"], "credentials":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
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

    def credentials = description.kubernetesCredentials
    def namespace = description.namespace ? description.namespace : "default"

    if (!credentials.isRegisteredNamespace(namespace)) {
      def error = "Registered namespaces are ${credentials.getNamespaces()}."
      if (description.namespace) {
        error = "Namespace '$namespace' was not registered with account '$description.credentials'. $error"
      } else {
        error = "No provided namespace assumed to mean 'default' was not registered with account '$description.credentials'. $error"
      }
      throw new KubernetesIllegalArgumentException(error)
    }

    def clusterName = KubernetesUtil.combineAppStackDetail(description.application, description.stack, description.freeFormDetails)
    task.updateStatus BASE_PHASE, "Looking up next sequence index..."
    def sequenceIndex = KubernetesUtil.getNextSequence(clusterName, namespace, credentials)
    task.updateStatus BASE_PHASE, "Sequence index chosen to be ${sequenceIndex}."
    def replicationControllerName = String.format("%s-v%s", clusterName, sequenceIndex)
    task.updateStatus BASE_PHASE, "Replication controller name chosen to be ${replicationControllerName}."

    def replicationControllerBuilder = new ReplicationControllerBuilder()
                        .withNewMetadata().withName(replicationControllerName)

    task.updateStatus BASE_PHASE, "Setting replication controller metadata labels..."

    replicationControllerBuilder = replicationControllerBuilder.addToLabels(KubernetesUtil.REPLICATION_CONTROLLER_LABEL, replicationControllerName)

    for (def securityGroup : description.securityGroups) {
      replicationControllerBuilder = replicationControllerBuilder.addToLabels(KubernetesUtil.securityGroupKey(securityGroup), "true")
    }

    for (def loadBalancer : description.loadBalancers) {
      replicationControllerBuilder = replicationControllerBuilder.addToLabels(KubernetesUtil.loadBalancerKey(loadBalancer), "true")
    }

    replicationControllerBuilder = replicationControllerBuilder.endMetadata()

    task.updateStatus BASE_PHASE, "Setting target size to ${description.targetSize}..."

    replicationControllerBuilder = replicationControllerBuilder.withNewSpec().withReplicas(description.targetSize)
                        .withNewTemplate()
                        .withNewMetadata()

    task.updateStatus BASE_PHASE, "Setting replication controller spec labels..."
    // Metadata in spec and replication controller need to match, hence the apparent duplication
    replicationControllerBuilder = replicationControllerBuilder.addToLabels(KubernetesUtil.REPLICATION_CONTROLLER_LABEL, replicationControllerName)

    for (def securityGroup : description.securityGroups) {
      replicationControllerBuilder = replicationControllerBuilder.addToLabels(KubernetesUtil.securityGroupKey(securityGroup), "true")
    }

    for (def loadBalancer : description.loadBalancers) {
      replicationControllerBuilder = replicationControllerBuilder.addToLabels(KubernetesUtil.loadBalancerKey(loadBalancer), "true")
    }

    replicationControllerBuilder = replicationControllerBuilder.endMetadata().withNewSpec()

    task.updateStatus BASE_PHASE, "Adding image pull secrets... "
    replicationControllerBuilder = replicationControllerBuilder.withImagePullSecrets()

    for (def imagePullSecret : credentials.imagePullSecrets[namespace]) {
      replicationControllerBuilder = replicationControllerBuilder.addNewImagePullSecret(imagePullSecret)
    }

    for (def container : description.containers) {
      task.updateStatus BASE_PHASE, "Adding container ${container.name} with image ${container.image}..."
      replicationControllerBuilder = replicationControllerBuilder.addNewContainer().withName(container.name).withImage(container.image)

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
      replicationControllerBuilder = replicationControllerBuilder.endResources().endContainer()
      task.updateStatus BASE_PHASE, "Finished adding container ${container.name}."
    }

    replicationControllerBuilder = replicationControllerBuilder.endSpec().endTemplate().endSpec()

    task.updateStatus BASE_PHASE, "Sending replication controller spec to the Kubernetes master."
	  ReplicationController replicationController = credentials.apiAdaptor.createReplicationController(namespace, replicationControllerBuilder.build())

    task.updateStatus BASE_PHASE, "Finished creating replication controller ${replicationController.metadata.name}."

    return replicationController
  }
}
