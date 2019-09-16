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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.ops.securitygroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.securitygroup.DeleteKubernetesSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.exception.KubernetesOperationException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation

class DeleteKubernetesSecurityGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "UPSERT_SECURITY_GROUP"

  DeleteKubernetesSecurityGroupAtomicOperation(DeleteKubernetesSecurityGroupDescription description) {
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  DeleteKubernetesSecurityGroupDescription description

  /*
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteSecurityGroup": { "securityGroupName": "kub-sg", "namespace": "default", "credentials": "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing delete of ingress."
    task.updateStatus BASE_PHASE, "Looking up provided namespace..."

    def credentials = description.credentials.credentials
    def namespace = KubernetesUtil.validateNamespace(credentials, description.namespace)

    if (!credentials.apiAdaptor.deleteIngress(namespace, description.securityGroupName)) {
      throw new KubernetesOperationException("Failed to delete ingress $description.securityGroupName in $namespace")
    }

    task.updateStatus BASE_PHASE, "Successfully deleted $description.securityGroupName in $namespace."
  }
}
