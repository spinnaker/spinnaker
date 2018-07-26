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

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.ResizeKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.exception.KubernetesOperationException
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.ReplicaSet

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

    if (resizeController(credentials, namespace, name, size)) {
      return
    }

    def desired = null
    def getGeneration = null
    def getResource = null
    def replicationController = credentials.apiAdaptor.getReplicationController(namespace, name)
    def replicaSet = credentials.apiAdaptor.getReplicaSet(namespace, name)
    if (replicationController) {
      task.updateStatus BASE_PHASE, "Resizing replication controller..."
      desired = credentials.apiAdaptor.resizeReplicationController(namespace, name, size)
      getGeneration = { ReplicationController rc ->
        return rc.metadata.generation
      }
      getResource = {
        return credentials.apiAdaptor.getReplicationController(namespace, name)
      }
    } else if (replicaSet) {
      if (credentials.apiAdaptor.hasDeployment(replicaSet)) {
        String clusterName = Names.parseName(name).cluster
        task.updateStatus BASE_PHASE, "Resizing deployment..."
        desired = credentials.apiAdaptor.resizeDeployment(namespace, clusterName, size)
        getGeneration = { Deployment d ->
          return d.metadata.generation
        }
        getResource = {
          return credentials.apiAdaptor.getDeployment(namespace, clusterName)
        }
      } else {
        task.updateStatus BASE_PHASE, "Resizing replica set..."
        desired = credentials.apiAdaptor.resizeReplicaSet(namespace, name, size)
        getGeneration = { ReplicaSet rs ->
          return rs.metadata.generation
        }
        getResource = {
          return credentials.apiAdaptor.getReplicaSet(namespace, name)
        }
      }
    } else {
      throw new KubernetesOperationException("Neither a replication controller nor a replica set could be found by that name.")
    }

    if (!credentials.apiAdaptor.blockUntilResourceConsistent(desired, getGeneration, getResource)) {
      throw new KubernetesOperationException("Failed waiting for server group to acknowledge its new size. This is likely a bug within Kubernetes itself.")
    }

    task.updateStatus BASE_PHASE, "Completed resize operation."
  }

  boolean resizeController(KubernetesV1Credentials credentials, String namespace, String serverGroupName, int size) {
    boolean isStatefulSetOrDaemonSet = false
    def controllerKind = description.kind
    if (!description.kind) {
      controllerKind = credentials.clientApiAdaptor.getControllerKind(serverGroupName, namespace, null)
    }

    if (controllerKind == KubernetesUtil.CONTROLLERS_STATEFULSET_KIND) {
      def deployedControllerSet = credentials.clientApiAdaptor.getStatefulSet(serverGroupName, namespace)
      if (deployedControllerSet) {
        credentials.apiClientAdaptor.resizeStatefulSet(serverGroupName, namespace, size)
      }
      isStatefulSetOrDaemonSet = true
    } else if (controllerKind == KubernetesUtil.CONTROLLERS_DAEMONSET_KIND) {
      throw new KubernetesOperationException("Does not support resizing DaemoneSet.")
    }

    task.updateStatus BASE_PHASE, "Completed resize operation."
    return isStatefulSetOrDaemonSet
  }
}

