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

package com.netflix.spinnaker.clouddriver.google.deploy.handlers

import com.google.api.services.compute.model.Autoscaler
import com.google.api.services.compute.model.InstanceGroupManager
import com.google.api.services.compute.model.InstanceProperties
import com.google.api.services.compute.model.InstanceTemplate
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.google.GoogleConfiguration
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GCEServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleSecurityGroupProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class BasicGoogleDeployHandler implements DeployHandler<BasicGoogleDeployDescription> {

  // TODO(duftler): This should move to a common location.
  private static final String BASE_PHASE = "DEPLOY"

  // TODO(duftler): These should be exposed/configurable.
  private static final String DEFAULT_NETWORK_NAME = "default"
  private static final String ACCESS_CONFIG_NAME = "External NAT"
  private static final String ACCESS_CONFIG_TYPE = "ONE_TO_ONE_NAT"

  @Autowired
  private GoogleConfigurationProperties googleConfigurationProperties

  @Autowired
  private GoogleConfiguration.DeployDefaults googleDeployDefaults

  @Autowired
  private GoogleOperationPoller googleOperationPoller

  @Autowired
  GoogleSecurityGroupProvider googleSecurityGroupProvider

  @Autowired
  String googleApplicationName

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  boolean handles(DeployDescription description) {
    description instanceof BasicGoogleDeployDescription
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "dev", "image": "ubuntu-1404-trusty-v20160509a", "targetSize": 3, "instanceType": "f1-micro", "zone": "us-central1-f", "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "dev", "freeFormDetails": "something", "image": "ubuntu-1404-trusty-v20160509a", "targetSize": 3, "instanceType": "f1-micro", "zone": "us-central1-f", "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "dev", "image": "ubuntu-1404-trusty-v20160509a", "targetSize": 3, "instanceType": "f1-micro", "zone": "us-central1-f", "loadBalancers": ["testlb"], "instanceMetadata": { "load-balancer-names": "myapp-testlb" }, "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "dev", "image": "ubuntu-1404-trusty-v20160509a", "targetSize": 3, "instanceType": "f1-micro", "zone": "us-central1-f", "tags": ["my-tag-1", "my-tag-2"], "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   *
   * @param description
   * @param priorOutputs
   * @return
   */
  @Override
  DeploymentResult handle(BasicGoogleDeployDescription description, List priorOutputs) {
    def accountName = description.accountName
    def credentials = description.credentials
    def compute = credentials.compute
    def project = credentials.project
    def isRegional = description.regional
    def zone = description.zone
    def region = description.region ?: GCEUtil.getRegionFromZone(project, zone, compute)

    def serverGroupNameResolver = new GCEServerGroupNameResolver(project, region, credentials)
    def clusterName = serverGroupNameResolver.combineAppStackDetail(description.application, description.stack, description.freeFormDetails)

    task.updateStatus BASE_PHASE, "Initializing creation of server group for cluster $clusterName in ${isRegional ? region : zone}..."

    task.updateStatus BASE_PHASE, "Looking up next sequence..."

    def serverGroupName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.freeFormDetails, false)

    task.updateStatus BASE_PHASE, "Produced server group name: $serverGroupName"

    def machineType = GCEUtil.queryMachineType(project, description.instanceType, compute, task, BASE_PHASE)

    def sourceImage = GCEUtil.querySourceImage(project,
                                               description,
                                               compute,
                                               task,
                                               BASE_PHASE,
                                               googleApplicationName,
                                               googleConfigurationProperties.baseImageProjects)

    def network = GCEUtil.queryNetwork(project, description.network ?: DEFAULT_NETWORK_NAME, compute, task, BASE_PHASE)

    def subnet =
      description.subnet ? GCEUtil.querySubnet(project, region, description.subnet, compute, task, BASE_PHASE) : null

    def networkLoadBalancers = []

    // We need the full url for each referenced network load balancer.
    if (description.loadBalancers) {
      def forwardingRules =
        GCEUtil.queryForwardingRules(project, region, description.loadBalancers, compute, task, BASE_PHASE)

      networkLoadBalancers = forwardingRules.collect { it.target }
    }

    def securityGroupTags = GCEUtil.querySecurityGroupTags(description.securityGroups, accountName,
        googleSecurityGroupProvider, task, BASE_PHASE)

    if (securityGroupTags) {
      description.tags = GCEUtil.mergeDescriptionAndSecurityGroupTags(description.tags, securityGroupTags)
    }

    task.updateStatus BASE_PHASE, "Composing server group $serverGroupName..."

    def attachedDisks = GCEUtil.buildAttachedDisks(project,
                                                   null,
                                                   sourceImage,
                                                   description.disks,
                                                   false,
                                                   description.instanceType,
                                                   googleDeployDefaults)

    def networkInterface = GCEUtil.buildNetworkInterface(network, subnet, ACCESS_CONFIG_NAME, ACCESS_CONFIG_TYPE)

    def metadata = GCEUtil.buildMetadataFromMap(description.instanceMetadata)

    def tags = GCEUtil.buildTagsFromList(description.tags)

    if (description.authScopes && !description.serviceAccountEmail) {
      description.serviceAccountEmail = "default"
    }

    def serviceAccount = GCEUtil.buildServiceAccount(description.serviceAccountEmail, description.authScopes)

    def scheduling = GCEUtil.buildScheduling(description)

    def instanceProperties = new InstanceProperties(machineType: machineType.name,
                                                    disks: attachedDisks,
                                                    networkInterfaces: [networkInterface],
                                                    metadata: metadata,
                                                    tags: tags,
                                                    scheduling: scheduling,
                                                    serviceAccounts: serviceAccount)

    def instanceTemplate = new InstanceTemplate(name: "$serverGroupName-${System.currentTimeMillis()}",
                                                properties: instanceProperties)
    def instanceTemplateCreateOperation = compute.instanceTemplates().insert(project, instanceTemplate).execute()
    def instanceTemplateUrl = instanceTemplateCreateOperation.targetLink

    // Before building the managed instance group we must check and wait until the instance template is built.
    googleOperationPoller.waitForGlobalOperation(compute, project, instanceTemplateCreateOperation.getName(),
        null, task, "instance template " + GCEUtil.getLocalName(instanceTemplateUrl), BASE_PHASE)

    if (autoscalerIsSpecified(description)) {
      GCEUtil.calibrateTargetSizeWithAutoscaler(description)
    }

    if (isRegional) {
      def migCreateOperation = compute.regionInstanceGroupManagers().insert(project,
                                                                            region,
                                                                            new InstanceGroupManager()
                                                                                .setName(serverGroupName)
                                                                                .setBaseInstanceName(serverGroupName)
                                                                                .setInstanceTemplate(instanceTemplateUrl)
                                                                                .setTargetSize(description.targetSize)
                                                                                .setTargetPools(networkLoadBalancers)).execute()

      if (autoscalerIsSpecified(description)) {
        // Before creating the Autoscaler we must wait until the managed instance group is created.
        googleOperationPoller.waitForRegionalOperation(compute, project, region, migCreateOperation.getName(),
          null, task, "managed instance group $serverGroupName", BASE_PHASE)

        task.updateStatus BASE_PHASE, "Creating regional autoscaler for $serverGroupName..."

        Autoscaler autoscaler = GCEUtil.buildAutoscaler(serverGroupName, migCreateOperation, description)

        compute.regionAutoscalers().insert(project, region, autoscaler).execute()
      }
    } else {
      def migCreateOperation = compute.instanceGroupManagers().insert(project,
                                                                      zone,
                                                                      new InstanceGroupManager()
                                                                          .setName(serverGroupName)
                                                                          .setBaseInstanceName(serverGroupName)
                                                                          .setInstanceTemplate(instanceTemplateUrl)
                                                                          .setTargetSize(description.targetSize)
                                                                          .setTargetPools(networkLoadBalancers)).execute()

      if (autoscalerIsSpecified(description)) {
        // Before creating the Autoscaler we must wait until the managed instance group is created.
        googleOperationPoller.waitForZonalOperation(compute, project, zone, migCreateOperation.getName(),
          null, task, "managed instance group $serverGroupName", BASE_PHASE)

        task.updateStatus BASE_PHASE, "Creating zonal autoscaler for $serverGroupName..."

        Autoscaler autoscaler = GCEUtil.buildAutoscaler(serverGroupName, migCreateOperation, description)

        compute.autoscalers().insert(project, zone, autoscaler).execute()
      }
    }

    task.updateStatus BASE_PHASE, "Done creating server group $serverGroupName in ${isRegional ? region : zone}."

    DeploymentResult deploymentResult = new DeploymentResult()
    deploymentResult.serverGroupNames = ["$region:$serverGroupName".toString()]
    deploymentResult.serverGroupNameByRegion[region] = serverGroupName
    deploymentResult
  }

  private boolean autoscalerIsSpecified(BasicGoogleDeployDescription description) {
    return description.autoscalingPolicy?.with {
      cpuUtilization || loadBalancingUtilization || customMetricUtilizations
    }
  }
}
