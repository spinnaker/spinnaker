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
    task.updateStatus BASE_PHASE, "Initializing destroy of replication controller."
    task.updateStatus BASE_PHASE, "Looking up provided namespace..."

    def credentials = description.credentials.credentials
    def namespace = KubernetesUtil.validateNamespace(credentials, description.namespace)

    task.updateStatus BASE_PHASE, "Destroying replication controller..."

    if (credentials.apiAdaptor.getReplicationController(namespace, description.serverGroupName)) {
      if (!credentials.apiAdaptor.hardDestroyReplicationController(namespace, description.serverGroupName)) {
        throw new KubernetesOperationException("Failed to delete $description.serverGroupName in $namespace.")
      }
    } else if (credentials.apiAdaptor.getReplicaSet(namespace, description.serverGroupName)) {
      if (!credentials.apiAdaptor.hardDestroyReplicaSet(namespace, description.serverGroupName)) {
        throw new KubernetesOperationException("Failed to delete $description.serverGroupName in $namespace.")
      }
    } else {
      throw new KubernetesOperationException("Failed to find replication controller or replica set $description in $namespace.")
    }

    task.updateStatus BASE_PHASE, "Successfully destroyed replication controller $description.serverGroupName."
  }
}
