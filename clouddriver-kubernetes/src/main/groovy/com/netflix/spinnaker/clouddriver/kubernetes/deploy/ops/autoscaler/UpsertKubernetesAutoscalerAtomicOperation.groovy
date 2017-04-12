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

package com.netflix.spinnaker.clouddriver.kubernetes.deploy.ops.autoscaler

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.kubernetes.api.KubernetesApiConverter
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.autoscaler.KubernetesAutoscalerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.Capacity
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesCpuUtilization
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesScalingPolicy
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.exception.KubernetesOperationException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation

class UpsertKubernetesAutoscalerAtomicOperation implements AtomicOperation<Void> {
  KubernetesAutoscalerDescription description
  String BASE_PHASE = "UPSERT_AUTOSCALER"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  UpsertKubernetesAutoscalerAtomicOperation(KubernetesAutoscalerDescription description) {
    this.description = description
  }

  /*
   * curl -X POST -H "Content-Type: application/json" -d  '[ { "upsertScalingPolicy": { "serverGroupName": "myapp-dev-v000", "capacity": { "min": 1, "max": 5 }, "scalingPolicy": { "cpuUtilization": { "target": 40 } }, "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ { "upsertScalingPolicy": { "serverGroupName": "myapp-dev-v000", "scalingPolicy": { "cpuUtilization": { "target": 40 } }, "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ { "upsertScalingPolicy": { "serverGroupName": "myapp-dev-v000", "capacity": { "min": 1, "max": 5 }, "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing upsert of autoscaler for server group $description.serverGroupName..."
    task.updateStatus BASE_PHASE, "Looking up provided namespace..."

    def credentials = description.credentials.credentials
    def namespace = KubernetesUtil.validateNamespace(credentials, description.namespace)
    def serverGroupName = description.serverGroupName
    def parsedName = Names.parseName(serverGroupName)

    def replicaSet = credentials.apiAdaptor.getReplicaSet(namespace, serverGroupName)
    def hasDeployment = credentials.apiAdaptor.hasDeployment(replicaSet)
    def name = hasDeployment ? parsedName.cluster : serverGroupName
    def kind = hasDeployment ? KubernetesUtil.DEPLOYMENT_KIND : KubernetesUtil.SERVER_GROUP_KIND

    task.updateStatus BASE_PHASE, "Looking up existing autoscaler..."

    def autoscaler = credentials.apiAdaptor.getAutoscaler(namespace, name)

    if (autoscaler) {
      task.updateStatus BASE_PHASE, "Updating autoscaler settings..."
      description.capacity = description.capacity ?: new Capacity()
      description.capacity.min = description.capacity.min != null ?
        description.capacity.min :
        autoscaler.spec.minReplicas
      description.capacity.max = description.capacity.max != null ?
        description.capacity.max :
        autoscaler.spec.maxReplicas

      description.scalingPolicy = description.scalingPolicy ?: new KubernetesScalingPolicy()
      description.scalingPolicy.cpuUtilization = description.scalingPolicy.cpuUtilization ?: new KubernetesCpuUtilization()
      description.scalingPolicy.cpuUtilization.target = description.scalingPolicy.cpuUtilization.target != null ?
        description.scalingPolicy.cpuUtilization.target :
        autoscaler.spec.cpuUtilization.targetPercentage

      task.updateStatus BASE_PHASE, "Deleting old autoscaler..."
      credentials.apiAdaptor.deleteAutoscaler(namespace, name)
    }

    if (!description.scalingPolicy || !description.scalingPolicy.cpuUtilization || description.scalingPolicy.cpuUtilization.target == null) {
      throw new KubernetesOperationException("Scaling policy must be specified when the target server group has no autoscaler.")
    }

    if (!description.capacity || description.capacity.min == null || description.capacity.max == null) {
      throw new KubernetesOperationException("Capacity min and max must be fully specified when the target server group has no autoscaler.")
    }


    task.updateStatus BASE_PHASE, "Creating autoscaler..."
    credentials.apiAdaptor.createAutoscaler(namespace, KubernetesApiConverter.toAutoscaler(description, name, kind))

    return null
  }
}
