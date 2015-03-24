/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.kato.gce.deploy.handlers

import com.google.api.services.compute.model.InstanceProperties
import com.google.api.services.compute.model.InstanceTemplate
import com.google.api.services.replicapool.ReplicapoolScopes
import com.google.api.services.replicapool.model.InstanceGroupManager
import com.netflix.spinnaker.kato.config.GceConfig
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.DeployDescription
import com.netflix.spinnaker.kato.deploy.DeployHandler
import com.netflix.spinnaker.kato.deploy.DeploymentResult
import com.netflix.spinnaker.kato.gce.deploy.GCEOperationUtil
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.kato.gce.deploy.ops.ReplicaPoolBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class BasicGoogleDeployHandler implements DeployHandler<BasicGoogleDeployDescription> {
  // TODO(duftler): This should move to a common location.
  private static final String BASE_PHASE = "DEPLOY"

  // TODO(duftler): These should be exposed/configurable.
  private static final String networkName = "default"
  private static final String accessConfigName = "External NAT"
  private static final String accessConfigType = "ONE_TO_ONE_NAT"

  @Autowired
  private GceConfig.DeployDefaults gceDeployDefaults

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  ReplicaPoolBuilder replicaPoolBuilder

  BasicGoogleDeployHandler() {
    replicaPoolBuilder = new ReplicaPoolBuilder()
  }

  @Override
  boolean handles(DeployDescription description) {
    description instanceof BasicGoogleDeployDescription
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "basicGoogleDeployDescription": { "application": "myapp", "stack": "dev", "image": "debian-7-wheezy-v20141108", "initialNumReplicas": 3, "instanceType": "f1-micro", "zone": "us-central1-b", "credentials": "my-account-name" }} ]' localhost:8501/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "basicGoogleDeployDescription": { "application": "myapp", "stack": "dev", "freeFormDetails": "something", "image": "debian-7-wheezy-v20141108", "initialNumReplicas": 3, "instanceType": "f1-micro", "zone": "us-central1-b", "credentials": "my-account-name" }} ]' localhost:8501/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "basicGoogleDeployDescription": { "application": "myapp", "stack": "dev", "image": "debian-7-wheezy-v20141108", "initialNumReplicas": 3, "instanceType": "f1-micro", "zone": "us-central1-b", "networkLoadBalancers": ["testlb"], "credentials": "my-account-name" }} ]' localhost:8501/ops
   *
   * @param description
   * @param priorOutputs
   * @return
   */
  @Override
  DeploymentResult handle(BasicGoogleDeployDescription description, List priorOutputs) {
    def clusterName = GCEUtil.combineAppStackDetail(description.application, description.stack, description.freeFormDetails)

    task.updateStatus BASE_PHASE, "Initializing creation of server group for cluster $clusterName..."

    def compute = description.credentials.compute
    def project = description.credentials.project
    def zone = description.zone

    task.updateStatus BASE_PHASE, "Looking up next sequence..."

    def region = GCEUtil.getRegionFromZone(project, zone, compute)

    def nextSequence = GCEUtil.getNextSequence(clusterName, project, region, description.credentials, replicaPoolBuilder)
    task.updateStatus BASE_PHASE, "Found next sequence ${nextSequence}."

    def serverGroupName = "${clusterName}-v${nextSequence}".toString()
    task.updateStatus BASE_PHASE, "Produced server group name: $serverGroupName"

    def sourceImage = GCEUtil.querySourceImage(project, description.image, compute, task, BASE_PHASE)

    def network = GCEUtil.queryNetwork(project, networkName, compute, task, BASE_PHASE)

    def networkLoadBalancers = []

    // We need the full url for each referenced network load balancer.
    if (description.networkLoadBalancers) {
      def forwardingRules =
              GCEUtil.queryForwardingRules(project, region, description.networkLoadBalancers, compute, task, BASE_PHASE)

      networkLoadBalancers = forwardingRules.collect { it.target }
    }

    task.updateStatus BASE_PHASE, "Composing server group $serverGroupName..."

    def attachedDisk = GCEUtil.buildAttachedDisk(project,
                                                 zone,
                                                 sourceImage,
                                                 description.diskSizeGb,
                                                 description.diskType,
                                                 false,
                                                 description.instanceType,
                                                 gceDeployDefaults)

    def networkInterface = GCEUtil.buildNetworkInterface(network, accessConfigName, accessConfigType)

    def metadata = GCEUtil.buildMetadataFromMap(description.instanceMetadata)

    def instanceProperties = new InstanceProperties(machineType: description.instanceType,
                                                    disks: [attachedDisk],
                                                    networkInterfaces: [networkInterface],
                                                    metadata: metadata)

    def instanceTemplate = new InstanceTemplate(name: "$serverGroupName-${System.currentTimeMillis()}",
                                                properties: instanceProperties)
    def instanceTemplateCreateOperation = compute.instanceTemplates().insert(project, instanceTemplate).execute()
    def instanceTemplateUrl = instanceTemplateCreateOperation.targetLink

    def credentialBuilder = description.credentials.createCredentialBuilder(ReplicapoolScopes.COMPUTE)

    def replicapool = replicaPoolBuilder.buildReplicaPool(credentialBuilder, GCEUtil.APPLICATION_NAME);

    // Before building the managed instance group we must check and wait until the instance template is built.
    GCEOperationUtil.waitForGlobalOperation(compute, project, instanceTemplateCreateOperation.getName(),
        null, task, "instance template " + GCEUtil.getLocalName(instanceTemplateUrl), BASE_PHASE)

    replicapool.instanceGroupManagers().insert(project,
                                               zone,
                                               description.initialNumReplicas,
                                               new InstanceGroupManager()
                                                       .setName(serverGroupName)
                                                       .setBaseInstanceName(serverGroupName)
                                                       .setInstanceTemplate(instanceTemplateUrl)
                                                       .setTargetPools(networkLoadBalancers)).execute()

    task.updateStatus BASE_PHASE, "Done creating server group $serverGroupName."
    new DeploymentResult(serverGroupNames: ["$region:$serverGroupName".toString()])
  }
}
