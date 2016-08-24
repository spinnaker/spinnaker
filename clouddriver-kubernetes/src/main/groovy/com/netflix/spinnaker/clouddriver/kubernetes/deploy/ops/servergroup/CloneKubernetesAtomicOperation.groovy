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
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.kubernetes.api.KubernetesApiConverter
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.CloneKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.exception.KubernetesResourceNotFoundException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet

class CloneKubernetesAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "CLONE_SERVER_GROUP"

  CloneKubernetesAtomicOperation(CloneKubernetesAtomicOperationDescription description) {
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  CloneKubernetesAtomicOperationDescription description

  /*
   * curl -X POST -H "Content-Type: application/json" -d  '[ { "cloneServerGroup": { "source": { "serverGroupName": "kub-test-v000" }, "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ { "cloneServerGroup": { "stack": "prod", "freeFormDetails": "mdservice", "targetSize": "4", "source": { "serverGroupName": "kub-test-v000" }, "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
  */
  @Override
  DeploymentResult operate(List priorOutputs) {
    CloneKubernetesAtomicOperationDescription newDescription = cloneAndOverrideDescription()

    task.updateStatus BASE_PHASE, "Initializing copy of server group for " +
      "${description.source.serverGroupName}..."

    DeployKubernetesAtomicOperation deployer = new DeployKubernetesAtomicOperation(newDescription)
    DeploymentResult deploymentResult = deployer.operate(priorOutputs)

    task.updateStatus BASE_PHASE, "Finished copying server group for " +
      "${description.source.serverGroupName}."

    task.updateStatus BASE_PHASE, "Finished copying server group for " +
      "${description.source.serverGroupName}. " +
      "New server group = ${deploymentResult.serverGroupNames[0]}."

    return deploymentResult
  }

  CloneKubernetesAtomicOperationDescription cloneAndOverrideDescription() {
    CloneKubernetesAtomicOperationDescription newDescription = description.clone()

    task.updateStatus BASE_PHASE, "Reading ancestor server group ${description.source.serverGroupName}..."

    def credentials = description.credentials.credentials

    description.source.namespace = description.source.namespace ?: "default"
    def ancestorServerGroup = credentials.apiAdaptor.getReplicationController(description.source.namespace, description.source.serverGroupName)
    if (!ancestorServerGroup) {
      ancestorServerGroup = credentials.apiAdaptor.getReplicaSet(description.source.namespace, description.source.serverGroupName)
    }

    if (!ancestorServerGroup) {
      throw new KubernetesResourceNotFoundException("Source server group $description.source.serverGroupName does not exist.")
    }

    def ancestorNames = Names.parseName(description.source.serverGroupName)

    // Build description object from ancestor, override any values that were specified on the clone call
    newDescription.application = description.application ?: ancestorNames.app
    newDescription.stack = description.stack ?: ancestorNames.stack
    newDescription.freeFormDetails = description.freeFormDetails ?: ancestorNames.detail
    newDescription.targetSize = description.targetSize ?: ancestorServerGroup.spec?.replicas
    newDescription.namespace = description.namespace ?: description.source.namespace
    newDescription.loadBalancers = description.loadBalancers != null ? description.loadBalancers : KubernetesUtil.getLoadBalancers(ancestorServerGroup)
    newDescription.restartPolicy = description.restartPolicy ?: ancestorServerGroup.spec?.template?.spec?.restartPolicy
    if (!description.containers) {
      newDescription.containers = ancestorServerGroup.spec?.template?.spec?.containers?.collect { it ->
        KubernetesApiConverter.fromContainer(it)
      }
    }

    return newDescription
  }
}
