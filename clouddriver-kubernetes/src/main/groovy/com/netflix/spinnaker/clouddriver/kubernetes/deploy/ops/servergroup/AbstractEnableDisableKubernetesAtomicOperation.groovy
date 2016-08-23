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
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet

abstract class AbstractEnableDisableKubernetesAtomicOperation implements AtomicOperation<Void> {
  abstract String getBasePhase() // Either 'ENABLE' or 'DISABLE'.
  abstract String getAction() // Either 'true' or 'false', for Enable or Disable respectively.
  abstract String getVerb() // Either 'enabling' or 'disabling.
  KubernetesServerGroupDescription description

  AbstractEnableDisableKubernetesAtomicOperation(KubernetesServerGroupDescription description) {
    this.description = description
  }

  protected static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus basePhase, "Initializing ${basePhase.toLowerCase()} operation..."
    task.updateStatus basePhase, "Looking up provided namespace..."

    def credentials = description.credentials.credentials
    def namespace = KubernetesUtil.validateNamespace(credentials, description.namespace)

    task.updateStatus basePhase, "Finding requisite server group..."

    def replicationController = credentials.apiAdaptor.getReplicationController(namespace, description.serverGroupName)
    def replicaSet = credentials.apiAdaptor.getReplicaSet(namespace, description.serverGroupName)

    task.updateStatus basePhase, "Getting list of attached services..."

    List<String> services = KubernetesUtil.getLoadBalancers(replicationController ?: replicaSet)
    services = services.collect {
      KubernetesUtil.loadBalancerKey(it)
    }

    task.updateStatus basePhase, "Resetting server group service template labels and selectors..."

    def getGeneration = null
    def getResource = null
    def desired = null
    def pods = []
    if (replicationController) {
      desired = credentials.apiAdaptor.toggleReplicationControllerSpecLabels(namespace, description.serverGroupName, services, action)
      getGeneration = { ReplicationController rc ->
        return rc.metadata.generation
      }
      getResource = {
        return credentials.apiAdaptor.getReplicationController(namespace, description.serverGroupName)
      }
      pods = credentials.apiAdaptor.getReplicationControllerPods(namespace, description.serverGroupName)
    } else if (replicaSet) {
      desired = credentials.apiAdaptor.toggleReplicaSetSpecLabels(namespace, description.serverGroupName, services, action)
      getGeneration = { ReplicaSet rs ->
        return rs.metadata.generation
      }
      getResource = {
        return credentials.apiAdaptor.getReplicaSet(namespace, description.serverGroupName)
      }
      pods = credentials.apiAdaptor.getReplicaSetPods(namespace, description.serverGroupName)
    } else {
      throw new KubernetesOperationException("No replication controller or replica set $description.serverGroupName in $namespace.")
    }

    if (!credentials.apiAdaptor.blockUntilResourceConsistent(desired, getGeneration, getResource)) {
      throw new KubernetesOperationException("Server group failed to reach a consistent state. This is likely a bug with Kubernetes itself.")
    }

    task.updateStatus basePhase, "Resetting service labels for each pod..."

    pods.forEach { Pod pod ->
      List<String> podServices = KubernetesUtil.getLoadBalancers(pod)
      podServices = podServices.collect {
        KubernetesUtil.loadBalancerKey(it)
      }
      credentials.apiAdaptor.togglePodLabels(namespace, pod.metadata.name, podServices, action)
    }

    task.updateStatus basePhase, "Finished ${verb} server group."

    null // Return nothing from void
  }
}
