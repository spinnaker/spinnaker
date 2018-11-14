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

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.google.api.services.compute.model.InstanceGroupManagersSetInstanceTemplateRequest
import com.google.api.services.compute.model.RegionInstanceGroupManagersSetTemplateRequest
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.config.GoogleConfiguration
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.BaseGoogleInstanceDescription
import com.netflix.spinnaker.clouddriver.google.deploy.description.ModifyGoogleServerGroupInstanceTemplateDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleResourceNotFoundException
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleNetworkProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleSubnetProvider
import org.springframework.beans.factory.annotation.Autowired

/**
 * Update the managed instance group's instance template. As the instance template itself is immutable, a new instance
 * template is created and set on the managed instance group. No changes are made to the managed instance group's
 * instances.
 *
 * Uses {@link https://cloud.google.com/compute/docs/reference/latest/instanceGroupManagers/setInstanceTemplate}
 */
class ModifyGoogleServerGroupInstanceTemplateAtomicOperation extends GoogleAtomicOperation<Void> {
  private static final String BASE_PHASE = "MODIFY_SERVER_GROUP_INSTANCE_TEMPLATE"

  private static final String accessConfigName = "External NAT"
  private static final String accessConfigType = "ONE_TO_ONE_NAT"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final ModifyGoogleServerGroupInstanceTemplateDescription description

  @Autowired
  GoogleConfigurationProperties googleConfigurationProperties

  @Autowired
  GoogleConfiguration.DeployDefaults googleDeployDefaults

  @Autowired
  GoogleOperationPoller googleOperationPoller

  @Autowired
  GoogleClusterProvider googleClusterProvider

  @Autowired
  GoogleNetworkProvider googleNetworkProvider

  @Autowired
  GoogleSubnetProvider googleSubnetProvider

  @Autowired
  String clouddriverUserAgentApplicationName

  @Autowired
  SafeRetry safeRetry

  ModifyGoogleServerGroupInstanceTemplateAtomicOperation(ModifyGoogleServerGroupInstanceTemplateDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "updateLaunchConfig": { "serverGroupName": "myapp-dev-v000", "region": "us-central1", "instanceType": "n1-standard-2", "tags": ["some-tag-1", "some-tag-2"], "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing modification of instance template for $description.serverGroupName " +
      "in $description.region..."

    def accountName = description.accountName
    def credentials = description.credentials
    def compute = credentials.compute
    def project = credentials.project
    def region = description.region
    def canIpForward = description.canIpForward
    def serverGroupName = description.serverGroupName
    def serverGroup = GCEUtil.queryServerGroup(googleClusterProvider, accountName, region, serverGroupName)
    def isRegional = serverGroup.regional
    // Will return null if this is a regional server group.
    def zone = serverGroup.zone
    def location = isRegional ? region : zone

    def instanceGroupManagers = isRegional ? compute.regionInstanceGroupManagers() : compute.instanceGroupManagers()
    def instanceTemplates = compute.instanceTemplates()

    // Retrieve the managed instance group.
    def managedInstanceGroup =
      isRegional
      ? timeExecute(instanceGroupManagers.get(project, region, serverGroupName),
                    "compute.regionInstanceGroupManagers",
                    TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
      : timeExecute(instanceGroupManagers.get(project, zone, serverGroupName),
                    "compute.instanceGroupManagers.get",
                    TAG_SCOPE, SCOPE_ZONAL, TAG_ZONE, zone)
    def origInstanceTemplateName = GCEUtil.getLocalName(managedInstanceGroup.instanceTemplate)

    if (!origInstanceTemplateName) {
      throw new GoogleResourceNotFoundException("Unable to determine instance template for server group $serverGroupName.")
    }

    // Retrieve the managed instance group's current instance template.
    def instanceTemplate = timeExecute(
        instanceTemplates.get(project, origInstanceTemplateName),
        "compute.instanceTemplates.get",
        TAG_SCOPE, SCOPE_GLOBAL)

    // Create a description to represent the current instance template.
    def originalDescription = GCEUtil.buildInstanceDescriptionFromTemplate(instanceTemplate)

    // Collect the properties of the description passed to the operation.
    def properties = [:] + description.properties

    // Remove the properties we don't want to compare or override.
    properties.keySet().removeAll(["class", "serverGroupName", "region", "accountName", "credentials", "applications"])

    // Collect all of the map entries with non-null values into a new map.
    def overriddenProperties = properties.findResults { key, value ->
      value != null ? [(key): value] : null
    }.collectEntries()

    // Build a new set of properties by overriding the existing set with any that were specified in the call.
    def newDescriptionProperties = [:] + originalDescription.properties + overriddenProperties

    // Remove the properties we don't want to compare or override.
    newDescriptionProperties.keySet().removeAll(["class", "accountName", "credentials", "account"])

    // Resolve the auth scopes since the scopes returned on the existing instance template will be fully-resolved.
    newDescriptionProperties.authScopes = GCEUtil.resolveAuthScopes(newDescriptionProperties.authScopes)

    // Create a description to represent the current instance template after overriding the specified properties.
    def newDescription = new BaseGoogleInstanceDescription(newDescriptionProperties)

    if (newDescription == originalDescription) {
      task.updateStatus BASE_PHASE, "No changes required for instance template of $serverGroupName in $region."
    } else {
      def instanceTemplateProperties = instanceTemplate.properties

      // Override the instance template's name.
      instanceTemplate.setName("$serverGroupName-${System.currentTimeMillis()}")

      // Override the instance template's disk configuration if image, disks or instanceType was specified.
      if (overriddenProperties.image
          || overriddenProperties.disks
          || overriddenProperties.instanceType) {
        def clonedDescription = description.clone()

        clonedDescription.disks = overriddenProperties.disks

        clonedDescription.baseDeviceName = description.serverGroupName
        def attachedDisks = GCEUtil.buildAttachedDisks(clonedDescription,
                                                       null,
                                                       false,
                                                       googleDeployDefaults,
                                                       task,
                                                       BASE_PHASE,
                                                       clouddriverUserAgentApplicationName,
                                                       googleConfigurationProperties.baseImageProjects,
                                                       safeRetry,
                                                       this)

        instanceTemplateProperties.setDisks(attachedDisks)
      }

      // Override the instance template's machine type if instanceType was specified.
      if (overriddenProperties.instanceType) {
        def machineTypeName

        if (description.instanceType.startsWith('custom')) {
          machineTypeName = description.instanceType
        } else {
          machineTypeName = GCEUtil.queryMachineType(description.instanceType, location, credentials, task, BASE_PHASE)
        }

        instanceTemplateProperties.setMachineType(machineTypeName)
      }

      // Override the instance template's minCpuPlatform property if it was specified.
      if (overriddenProperties.minCpuPlatform) {
        instanceTemplateProperties.setMinCpuPlatform(description.minCpuPlatform)
      }

      // Override the instance template's canIpForward property if it was specified.
      if (overriddenProperties.canIpForward) {
        instanceTemplateProperties.setCanIpForward(canIpForward)
      }

      // Override the instance template's metadata if instanceMetadata was specified.
      if (overriddenProperties.instanceMetadata) {
        def metadata = GCEUtil.buildMetadataFromMap(description.instanceMetadata)

        instanceTemplateProperties.setMetadata(metadata)
      }

      // Override the instance template's tags if tags was specified.
      if (overriddenProperties.tags) {
        def tags = GCEUtil.buildTagsFromList(description.tags)

        instanceTemplateProperties.setTags(tags)
      }

      // Override the instance template's labels if labels was specified.
      if (overriddenProperties.labels) {
        instanceTemplateProperties.setLabels(description.labels)
      }

      // Override the instance template's service account if serviceAccountEmail or authScopes was specified.
      // Note that we want to explicitly allow for either the service account or auth scopes to be empty, but non-null.
      if (overriddenProperties.serviceAccountEmail != null || overriddenProperties.authScopes != null) {
        def serviceAccount = GCEUtil.buildServiceAccount(description.serviceAccountEmail, description.authScopes)

        instanceTemplateProperties.setServiceAccounts(serviceAccount)
      }

      // Override the instance template's network if network was specified.
      if (overriddenProperties.network) {
        def network = GCEUtil.queryNetwork(accountName, newDescription.network, task, BASE_PHASE, googleNetworkProvider)
        def subnet =
          description.subnet ? GCEUtil.querySubnet(accountName, region, description.subnet, task, BASE_PHASE, googleSubnetProvider) : null
        def networkInterface = GCEUtil.buildNetworkInterface(network,
                                                             subnet,
                                                             description.associatePublicIpAddress == null || description.associatePublicIpAddress,
                                                             accessConfigName,
                                                             accessConfigType)

        instanceTemplateProperties.setNetworkInterfaces([networkInterface])
      }

      // Create a new instance template resource using the modified instance template.
      task.updateStatus BASE_PHASE, "Inserting new instance template $instanceTemplate.name..."

      def instanceTemplateCreateOperation = timeExecute(
          instanceTemplates.insert(project, instanceTemplate),
          "compute.instanceTemplates.insert",
          TAG_SCOPE, SCOPE_GLOBAL)
      def instanceTemplateUrl = instanceTemplateCreateOperation.targetLink

      // Block on creating the instance template.
      googleOperationPoller.waitForGlobalOperation(compute, project, instanceTemplateCreateOperation.getName(),
          null, task, "instance template $instanceTemplate.name", BASE_PHASE)

      // Set the new instance template on the managed instance group.
      task.updateStatus BASE_PHASE, "Setting instance template $instanceTemplate.name on server group $serverGroupName..."

      if (isRegional) {
        def regionInstanceGroupManagersSetTemplateRequest =
          new RegionInstanceGroupManagersSetTemplateRequest(instanceTemplate: instanceTemplateUrl)
        def setInstanceTemplateOperation = timeExecute(
          instanceGroupManagers.setInstanceTemplate(
            project, region, serverGroupName, regionInstanceGroupManagersSetTemplateRequest),
          "compute.regionInstanceGroupManagers.setInstanceTemplate",
          TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)

        // Block on setting the instance template on the managed instance group.
        googleOperationPoller.waitForRegionalOperation(compute, project, region,
          setInstanceTemplateOperation.getName(), null, task, "server group $serverGroupName", BASE_PHASE)
      } else {
        def instanceGroupManagersSetInstanceTemplateRequest =
          new InstanceGroupManagersSetInstanceTemplateRequest(instanceTemplate: instanceTemplateUrl)
        def setInstanceTemplateOperation = timeExecute(
          instanceGroupManagers.setInstanceTemplate(
            project, zone, serverGroupName, instanceGroupManagersSetInstanceTemplateRequest),
          "compute.instanceGroupManagers.setInstanceTemplate",
          TAG_SCOPE, SCOPE_ZONAL, TAG_ZONE, zone)

        // Block on setting the instance template on the managed instance group.
        googleOperationPoller.waitForZonalOperation(compute, project, zone,
          setInstanceTemplateOperation.getName(), null, task, "server group $serverGroupName", BASE_PHASE)
      }

      // Delete the original instance template.
      task.updateStatus BASE_PHASE, "Deleting original instance template $origInstanceTemplateName..."

      timeExecute(
          instanceTemplates.delete(project, origInstanceTemplateName),
          "compute.instanceTemplates.delete",
          TAG_SCOPE, SCOPE_GLOBAL)
    }

    task.updateStatus BASE_PHASE, "Done modifying instance template of $serverGroupName in $region."
    null
  }
}
