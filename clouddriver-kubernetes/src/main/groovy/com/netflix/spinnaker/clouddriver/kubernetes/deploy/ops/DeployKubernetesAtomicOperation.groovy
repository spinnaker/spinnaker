/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.kubernetes.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.DeployKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder
import org.springframework.beans.factory.annotation.Autowired

class DeployKubernetesAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "DEPLOY"

  @Autowired
  KubernetesUtil kubernetesUtil

  DeployKubernetesAtomicOperation(DeployKubernetesAtomicOperationDescription description) {
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  DeployKubernetesAtomicOperationDescription description

  /*
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "k8s", "stack": "test",  "targetSize": "3", "securityGroups": [], "loadBalancers":  [],  "containers": [ { "name": "nginx", "image": "nginx" } ], "credentials":  "my-k8s-account" } } ]' localhost:7002/kubernetes/ops
   *
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "k8s", "stack": "test",  "targetSize": "3", "securityGroups": ["frontend-sg"], "loadBalancers":  ["frontend-lb"],  "containers": [ { "name": "nginx", "image": "nginx" } ], "credentials":  "my-k8s-account" } } ]' localhost:7002/kubernetes/ops
  */
  @Override
  DeploymentResult operate(List priorOutputs) {

    ReplicationController rc = deployDescription()
    DeploymentResult deploymentResult = new DeploymentResult()
    deploymentResult.serverGroupNames = Arrays.asList(rc.metadata.name)
    return deploymentResult
  }

  ReplicationController deployDescription() {
    task.updateStatus BASE_PHASE, "Initializing creation of replication controller."

    def credentials = description.kubernetesCredentials
    def clusterName = KubernetesUtil.combineAppStackDetail(description.application, description.stack, description.freeFormDetails)
    task.updateStatus BASE_PHASE, "Looking up next sequence index..."
    def sequenceIndex = kubernetesUtil.getNextSequence(clusterName, credentials)
    task.updateStatus BASE_PHASE, "Sequence index chosen to be ${sequenceIndex}."
    def replicationControllerName = String.format("%s-v%s", clusterName, sequenceIndex)
    task.updateStatus BASE_PHASE, "Replication controller name chosen to be ${replicationControllerName}."

    task.updateStatus BASE_PHASE, "Collecting ports for associated security groups..."
    def ports = []
    for (def securityGroupName : description.securityGroups) {
      def securityGroup = kubernetesUtil.getSecurityGroup(credentials, securityGroupName)

      for (def port : securityGroup.getSpec().getPorts()) {
        ports.add(port.getTargetPort().intVal)
      }
    }

    def replicationControllerBuilder = new ReplicationControllerBuilder()
                        .withNewMetadata().withName(replicationControllerName)

    task.updateStatus BASE_PHASE, "Setting replication controller metadata labels..."

    replicationControllerBuilder = replicationControllerBuilder.addToLabels(sequenceIndex, "true")

    for (def securityGroup : description.securityGroups) {
      replicationControllerBuilder = replicationControllerBuilder.addToLabels(KubernetesUtil.securityGroupKey(securityGroup), "true")
    }

    for (def loadBalancer : description.loadBalancers) {
      replicationControllerBuilder = replicationControllerBuilder.addToLabels(KubernetesUtil.loadBalancerKey(loadBalancer), "true")
    }

    replicationControllerBuilder = replicationControllerBuilder.endMetadata()

    task.updateStatus BASE_PHASE, "Setting target size to ${description.targetSize}..."

    replicationControllerBuilder = replicationControllerBuilder.withNewSpec().withReplicas(description.targetSize)
                        .withNewTemplate()
                        .withNewMetadata()

    task.updateStatus BASE_PHASE, "Setting replication controller spec labels..."
    // Metadata in spec and replication controller need to match, hence the apparent duplication
    replicationControllerBuilder = replicationControllerBuilder.addToLabels(sequenceIndex, "true")

    for (def securityGroup : description.securityGroups) {
      replicationControllerBuilder = replicationControllerBuilder.addToLabels(KubernetesUtil.securityGroupKey(securityGroup), "true")
    }

    for (def loadBalancer : description.loadBalancers) {
      replicationControllerBuilder = replicationControllerBuilder.addToLabels(KubernetesUtil.loadBalancerKey(loadBalancer), "true")
    }

    replicationControllerBuilder = replicationControllerBuilder.endMetadata().withNewSpec()

    for (def container : description.containers) {
      task.updateStatus BASE_PHASE, "Adding container ${container.name} with image ${container.image}..."
      replicationControllerBuilder = replicationControllerBuilder.addNewContainer().withName(container.name).withImage(container.image)
      task.updateStatus BASE_PHASE, "Opening container ports..."
      for (def port : ports) {
        replicationControllerBuilder = replicationControllerBuilder.addNewPort().withContainerPort(port).endPort()
      }

      replicationControllerBuilder = replicationControllerBuilder.withNewResources()
      if (container.requests) {
        def requests = [memory: container.requests.memory,
                        cpu: container.requests.cpu]
        task.updateStatus BASE_PHASE, "Setting resource requests..."
        replicationControllerBuilder = replicationControllerBuilder.withRequests(requests)
      }

      if (container.limits) {
        def limits = [memory: container.limits.memory,
                      cpu: container.limits.cpu]
        task.updateStatus BASE_PHASE, "Setting resource limits..."
        replicationControllerBuilder = replicationControllerBuilder.withLimits(limits)
      }
      replicationControllerBuilder = replicationControllerBuilder.endResources().endContainer()
      task.updateStatus BASE_PHASE, "Finished adding container ${container.name}."
    }

    replicationControllerBuilder = replicationControllerBuilder.endSpec().endTemplate().endSpec().build()

    task.updateStatus BASE_PHASE, "Sending replication controller spec to the Kubernetes master."
	  ReplicationController rc = credentials.client.replicationControllers().inNamespace(credentials.namespace).create(replicationControllerBuilder)

    task.updateStatus BASE_PHASE, "Finished creating replication controller ${rc.metadata.name}."

    return rc
  }
}
