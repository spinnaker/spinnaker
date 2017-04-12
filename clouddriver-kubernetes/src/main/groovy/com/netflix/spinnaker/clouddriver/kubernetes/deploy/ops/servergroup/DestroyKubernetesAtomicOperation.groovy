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

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesServerGroupDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.exception.KubernetesOperationException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation

class DestroyKubernetesAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DESTROY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final KubernetesServerGroupDescription description

  DestroyKubernetesAtomicOperation(KubernetesServerGroupDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "destroyServerGroup": { "serverGroupName": "kub-test-v000", "namespace": "default", "credentials": "my-kubernetes-account" }} ]' localhost:7002/kubernetes/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing destroy of server group."
    task.updateStatus BASE_PHASE, "Looking up provided namespace..."

    def credentials = description.credentials.credentials
    def namespace = KubernetesUtil.validateNamespace(credentials, description.namespace)

    def autoscalerName = description.serverGroupName
    def parsedName = Names.parseName(description.serverGroupName)
    def deploymentName = parsedName.cluster
    def deployment = credentials.apiAdaptor.getDeployment(namespace, deploymentName)
    def replicaSet = credentials.apiAdaptor.getReplicaSet(namespace, description.serverGroupName)
    def destroyAutoscalerIfExists = true

    if (deployment && replicaSet) {
      task.updateStatus BASE_PHASE, "Checking if deployment ${deploymentName} needs to be destroyed..."
      // If we selected to delete the replica set in the currently active deployment, this will delete everything owned by the deployment.
      if (credentials.apiAdaptor.getDeploymentRevision(deployment) == credentials.apiAdaptor.getDeploymentRevision(replicaSet)) {
        task.updateStatus BASE_PHASE, "Destroying deployment ${deploymentName}..."
        if (!credentials.apiAdaptor.deleteDeployment(namespace, deploymentName)) {
          throw new KubernetesOperationException("Failed to delete deployment ${deploymentName} in $namespace")
        }

        task.updateStatus BASE_PHASE, "Successfully destroyed deployment ${deploymentName}..."
      } else {
        destroyAutoscalerIfExists = false
      }
    }

    if (credentials.apiAdaptor.getAutoscaler(namespace, autoscalerName) && destroyAutoscalerIfExists) {
      task.updateStatus BASE_PHASE, "Destroying autoscaler..."
      if (!credentials.apiAdaptor.deleteAutoscaler(namespace, autoscalerName)) {
        throw new KubernetesOperationException("Failed to delete associated autoscaler $autoscalerName in $namespace.")
      }
    }

    task.updateStatus BASE_PHASE, "Destroying server group..."

    if (credentials.apiAdaptor.getReplicationController(namespace, description.serverGroupName)) {
      task.updateStatus BASE_PHASE, "Underlying kind is 'ReplicationController'..."
      if (!credentials.apiAdaptor.hardDestroyReplicationController(namespace, description.serverGroupName)) {
        throw new KubernetesOperationException("Failed to delete $description.serverGroupName in $namespace.")
      }
    } else if (replicaSet) {
      task.updateStatus BASE_PHASE, "Underlying kind is 'ReplicaSet'..."
      credentials.apiAdaptor.hardDestroyReplicaSet(namespace, description.serverGroupName)
    }

    task.updateStatus BASE_PHASE, "Successfully destroyed server group $description.serverGroupName."
  }
}
