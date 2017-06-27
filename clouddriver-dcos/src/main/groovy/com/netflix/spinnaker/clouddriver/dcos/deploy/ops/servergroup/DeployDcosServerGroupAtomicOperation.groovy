/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.DcosServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DeployDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.mapper.DeployDcosServerGroupDescriptionToAppMapper
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation

class DeployDcosServerGroupAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "DEPLOY"

  final DcosClientProvider dcosClientProvider
  final DeployDcosServerGroupDescription description
  final DeployDcosServerGroupDescriptionToAppMapper descriptionToAppMapper

  DeployDcosServerGroupAtomicOperation(DcosClientProvider dcosClientProvider, DeployDcosServerGroupDescriptionToAppMapper descriptionToAppMapper, DeployDcosServerGroupDescription description) {
    this.dcosClientProvider = dcosClientProvider
    this.descriptionToAppMapper = descriptionToAppMapper
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

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
    task.updateStatus BASE_PHASE, "Initializing creation of application."

    def dcosClient = dcosClientProvider.getDcosClient(description.credentials, description.dcosCluster)
    def serverGroupNameResolver = new DcosServerGroupNameResolver(dcosClient, description.credentials.account, description.group)

    task.updateStatus BASE_PHASE, "Looking up next sequence index"

    def resolvedServerGroupName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.freeFormDetails, false)
    def dcosPathId = DcosSpinnakerAppId.fromVerbose(description.credentials.account, description.group, resolvedServerGroupName).get()

    task.updateStatus BASE_PHASE, "Spinnaker ID chosen to be ${resolvedServerGroupName}."
    task.updateStatus BASE_PHASE, "Marathon ID chosen to be $dcosPathId."
    task.updateStatus BASE_PHASE, "Building application..."

    dcosClient.updateApp(dcosPathId.toString(), descriptionToAppMapper.map(dcosPathId.toString(), description), description.forceDeployment)

    task.updateStatus BASE_PHASE, "Deployed service ${resolvedServerGroupName}"

    def deploymentResult = new DeploymentResult()
    deploymentResult.serverGroupNames = Arrays.asList("${description.region.replaceAll("/", DcosSpinnakerAppId.SAFE_REGION_SEPARATOR)}:${resolvedServerGroupName}".toString())
    deploymentResult.serverGroupNameByRegion[description.region.replaceAll("/", DcosSpinnakerAppId.SAFE_REGION_SEPARATOR)] = resolvedServerGroupName

    return deploymentResult
  }
}
