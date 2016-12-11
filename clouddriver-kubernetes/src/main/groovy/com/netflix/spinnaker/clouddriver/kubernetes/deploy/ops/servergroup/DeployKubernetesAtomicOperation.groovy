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
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.kubernetes.api.KubernetesApiConverter
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.autoscaler.KubernetesAutoscalerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.DeployKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import io.fabric8.kubernetes.api.builder.BaseFluent
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.extensions.DeploymentBuilder
import io.fabric8.kubernetes.api.model.extensions.DeploymentFluentImpl
import io.fabric8.kubernetes.api.model.extensions.DoneableDeployment
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetBuilder

class DeployKubernetesAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "DEPLOY"

  DeployKubernetesAtomicOperation(DeployKubernetesAtomicOperationDescription description) {
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  final DeployKubernetesAtomicOperationDescription description

  /*
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "securityGroups": [], "loadBalancers":  [],  "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx" } } ], "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "loadBalancers":  ["frontend-lb"],  "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx", "tag": "latest", "registry": "index.docker.io" }, "ports": [ { "containerPort": "80", "hostPort": "80", "name": "http", "protocol": "TCP", "hostIp": "10.239.18.11" } ] } ], "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "loadBalancers":  [],  "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx", "tag": "latest", "registry": "index.docker.io" }, "livenessProbe": { "handler": { "type": "EXEC", "execAction": { "commands": [ "ls" ] } } } } ], "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "loadBalancers":  [],  "volumeSources": [ { "name": "storage", "type": "EMPTYDIR", "emptyDir": {} } ], "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx", "tag": "latest", "registry": "index.docker.io" }, "volumeMounts": [ { "name": "storage", "mountPath": "/storage", "readOnly": false } ] } ], "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "securityGroups": [], "loadBalancers":  [],  "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx" } } ], "capacity": { "min": 1, "max": 5 }, "scalingPolicy": { "cpuUtilization": { "target": 40 } }, "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "securityGroups": [], "loadBalancers":  [],  "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx" } } ], "account":  "my-kubernetes-account", "deployment": { "enabled": "true" } } } ]' localhost:7002/kubernetes/ops
   */

  @Override
  DeploymentResult operate(List priorOutputs) {

    HasMetadata serverGroup = deployDescription()
    DeploymentResult deploymentResult = new DeploymentResult()
    deploymentResult.serverGroupNames = Arrays.asList("${serverGroup.metadata.namespace}:${serverGroup.metadata.name}".toString())
    deploymentResult.serverGroupNameByRegion[serverGroup.metadata.namespace] = serverGroup.metadata.name
    return deploymentResult
  }

  HasMetadata deployDescription() {
    task.updateStatus BASE_PHASE, "Initializing creation of replica set."
    task.updateStatus BASE_PHASE, "Looking up provided namespace..."

    def credentials = description.credentials.credentials

    def namespace = KubernetesUtil.validateNamespace(credentials, description.namespace)
    description.imagePullSecrets = credentials.imagePullSecrets[namespace]

    def serverGroupNameResolver = new KubernetesServerGroupNameResolver(namespace, credentials)
    def clusterName = serverGroupNameResolver.combineAppStackDetail(description.application, description.stack, description.freeFormDetails)

    task.updateStatus BASE_PHASE, "Looking up next sequence index for cluster ${clusterName}..."
    def replicaSetName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.freeFormDetails, false)
    task.updateStatus BASE_PHASE, "Replica set name chosen to be ${replicaSetName}."

    task.updateStatus BASE_PHASE, "Building replica set..."
    def replicaSet = KubernetesApiConverter.toReplicaSet(new ReplicaSetBuilder(), description, replicaSetName)

    if (KubernetesApiConverter.hasDeployment(description)) {
      replicaSet.spec.replicas = 0
    }

    replicaSet = credentials.apiAdaptor.createReplicaSet(namespace, replicaSet)

    if (KubernetesApiConverter.hasDeployment(description)) {
      if (!credentials.apiAdaptor.getDeployment(namespace, clusterName)) {
        task.updateStatus BASE_PHASE, "Building deployment..."
        credentials.apiAdaptor.createDeployment(namespace, ((DeploymentBuilder) KubernetesApiConverter.toDeployment((DeploymentFluentImpl) new DeploymentBuilder(), description, replicaSetName)).build())
      } else {
        task.updateStatus BASE_PHASE, "Updating deployment..."
        ((DoneableDeployment) KubernetesApiConverter.toDeployment((DeploymentFluentImpl) credentials.apiAdaptor.editDeployment(namespace, clusterName),
          description,
          replicaSetName)).done()
      }
    }


    task.updateStatus BASE_PHASE, "Sending replica set spec to the Kubernetes master."

    task.updateStatus BASE_PHASE, "Finished creating replica set ${replicaSet.metadata.name}."

    if (description.scalingPolicy) {
      task.updateStatus BASE_PHASE, "Attaching a horizontal pod autoscaler..."

      def autoscaler = KubernetesApiConverter.toAutoscaler(new KubernetesAutoscalerDescription(replicaSetName, description))

      credentials.apiAdaptor.createAutoscaler(namespace, autoscaler)
    }

    return replicaSet
  }
}
