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
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.EnableDisableKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import io.fabric8.kubernetes.api.model.Pod

abstract class AbstractEnableDisableKubernetesAtomicOperation implements AtomicOperation<Void> {
  abstract String getBasePhase() // Either 'ENABLE' or 'DISABLE'.
  abstract String getAction() // Either 'true' or 'false', for Enable or Disable respectively.
  abstract String getVerb() // Either 'enabling' or 'disabling.
  EnableDisableKubernetesAtomicOperationDescription description

  AbstractEnableDisableKubernetesAtomicOperation(EnableDisableKubernetesAtomicOperationDescription description) {
    this.description = description
  }

  protected static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus basePhase, "Initializing ${basePhase.toLowerCase()} operation..."
    task.updateStatus basePhase, "Looking up provided namespace..."

    def credentials = description.credentials
    def namespace = KubernetesUtil.validateNamespace(credentials, description.namespace)

    task.updateStatus basePhase, "Finding requisite replication controller..."

    def replicationController = credentials.apiAdaptor.getReplicationController(namespace, description.serverGroupName)

    task.updateStatus basePhase, "Getting list of attached services..."

    List<String> replicationControllerServices = KubernetesUtil.getDescriptionLoadBalancers(replicationController)
    replicationControllerServices = replicationControllerServices.collect {
      KubernetesUtil.loadBalancerKey(it)
    }

    task.updateStatus basePhase, "Removing replication controller service template labels and selectors..."

    credentials.apiAdaptor.removeReplicationControllerSpecLabels(namespace, description.serverGroupName, replicationControllerServices)

    task.updateStatus basePhase, "Finding affected pods..."

    List<Pod> pods = credentials.apiAdaptor.getPods(namespace, description.serverGroupName)

    task.updateStatus basePhase, "Resetting service labels for each pod..."

    // It's important that we remove the pod labels before reinserting the replication controller labels, otherwise, if
    // the replication controller has labels not present on the pod, it will start spinning up new pods to compensate.
    pods.forEach { pod ->
      List<String> podServices = KubernetesUtil.getPodLoadBalancers(pod)
      podServices = podServices.collect {
        KubernetesUtil.loadBalancerKey(it)
      }
      credentials.apiAdaptor.removePodLabels(namespace, pod.metadata.name, podServices)
      credentials.apiAdaptor.addPodLabels(namespace, pod.metadata.name, podServices, 'false')
    }

    task.updateStatus basePhase, "Adding new replication controller service template labels and selectors..."

    credentials.apiAdaptor.addReplicationControllerSpecLabels(namespace, description.serverGroupName, replicationControllerServices, action)

    task.updateStatus basePhase, "Finished ${verb} replication controller."

    null // Return nothing from void
  }
}
