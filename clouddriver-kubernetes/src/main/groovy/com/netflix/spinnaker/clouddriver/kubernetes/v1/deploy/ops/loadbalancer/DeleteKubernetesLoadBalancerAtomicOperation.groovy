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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.ops.loadbalancer

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.loadbalancer.DeleteKubernetesLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.exception.KubernetesOperationException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation

class DeleteKubernetesLoadBalancerAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DESTROY_LOAD_BALANCER"

  DeleteKubernetesLoadBalancerAtomicOperationDescription description

  DeleteKubernetesLoadBalancerAtomicOperation(DeleteKubernetesLoadBalancerAtomicOperationDescription description) {
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  /*
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "deleteLoadBalancer": { "loadBalancerName": "kub-lb", "namespace": "default", "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
  */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing delete of load balancer $description.loadBalancerName..."
    task.updateStatus BASE_PHASE, "Looking up provided namespace..."

    def credentials = description.credentials.credentials
    def namespace = KubernetesUtil.validateNamespace(credentials, description.namespace)

    if (!credentials.apiAdaptor.deleteService(namespace, description.loadBalancerName)) {
      throw new KubernetesOperationException("Failed to delete service $description.loadBalancerName in $namespace")
    }

    task.updateStatus BASE_PHASE, "Successfully deleted load balancer $description.loadBalancerName."
  }
}
