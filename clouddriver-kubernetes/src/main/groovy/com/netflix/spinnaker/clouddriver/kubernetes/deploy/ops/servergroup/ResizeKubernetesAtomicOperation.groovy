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
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.ResizeKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.exception.KubernetesOperationException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet

class ResizeKubernetesAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "RESIZE"

  ResizeKubernetesAtomicOperation(ResizeKubernetesAtomicOperationDescription description) {
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  ResizeKubernetesAtomicOperationDescription description

  /*
   * curl -X POST -H "Content-Type: application/json" -d '[ { "resizeServerGroup": { "serverGroupName": "kub-test-v000", "capacity": { "desired": 7 }, "account": "my-kubernetes-account" }} ]' localhost:7002/kubernetes/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing resize of server group $description.serverGroupName..."

    def credentials = description.credentials.credentials

    task.updateStatus BASE_PHASE, "Looking up provided namespace..."
    def namespace = KubernetesUtil.validateNamespace(credentials, description.namespace)

    def size = description.capacity.desired
    def name = description.serverGroupName

    task.updateStatus BASE_PHASE, "Setting size to $size..."

    def desired = null
    def getGeneration = null
    def getResource = null
    if (credentials.apiAdaptor.getReplicationController(namespace, name)) {
      desired = credentials.apiAdaptor.resizeReplicationController(namespace, name, size)
      getGeneration = { ReplicationController rc ->
        return rc.metadata.generation
      }
      getResource = {
        return credentials.apiAdaptor.getReplicationController(namespace, name)
      }
    } else if (credentials.apiAdaptor.getReplicaSet(namespace, name)) {
      desired = credentials.apiAdaptor.resizeReplicaSet(namespace, name, size)
      getGeneration = { ReplicaSet rs ->
        return rs.metadata.generation
      }
      getResource = {
        return credentials.apiAdaptor.getReplicaSet(namespace, name)
      }
    } else {
      throw new KubernetesOperationException("Neither a replication controller nor a replica set could be found by that name.")
    }

    if (!credentials.apiAdaptor.blockUntilResourceConsistent(desired, getGeneration, getResource)) {
      throw new KubernetesOperationException("Failed waiting for server group to acknowledge its new size. This is likely a bug within Kubernetes itself.")
    }

    task.updateStatus BASE_PHASE, "Completed resize operation."
  }
}

