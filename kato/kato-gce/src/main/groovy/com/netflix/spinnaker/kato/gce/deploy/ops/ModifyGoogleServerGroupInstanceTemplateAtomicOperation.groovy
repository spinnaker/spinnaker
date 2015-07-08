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

package com.netflix.spinnaker.kato.gce.deploy.ops

import com.google.api.services.replicapool.ReplicapoolScopes
import com.google.api.services.replicapool.model.InstanceGroupManagersSetInstanceTemplateRequest
import com.netflix.spinnaker.kato.config.GceConfig
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.GoogleOperationPoller
import com.netflix.spinnaker.kato.gce.deploy.description.BaseGoogleInstanceDescription
import com.netflix.spinnaker.kato.gce.deploy.description.ModifyGoogleServerGroupInstanceTemplateDescription
import com.netflix.spinnaker.kato.gce.deploy.exception.GoogleResourceNotFoundException
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

/**
 * Update the managed instance group's instance template. As the instance template itself is immutable, a new instance
 * template is created and set on the managed instance group. No changes are made to the managed instance group's
 * instances.
 *
 * Uses {@link https://cloud.google.com/compute/docs/instance-groups/manager/v1beta2/instanceGroupManagers/setInstanceTemplate}
 */
class ModifyGoogleServerGroupInstanceTemplateAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "MODIFY_SERVER_GROUP_INSTANCE_TEMPLATE"

  private static final String accessConfigName = "External NAT"
  private static final String accessConfigType = "ONE_TO_ONE_NAT"

  @Autowired
  private GceConfig.DeployDefaults gceDeployDefaults

  @Autowired
  private GoogleOperationPoller googleOperationPoller

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final ModifyGoogleServerGroupInstanceTemplateDescription description
  private final ReplicaPoolBuilder replicaPoolBuilder

  ModifyGoogleServerGroupInstanceTemplateAtomicOperation(ModifyGoogleServerGroupInstanceTemplateDescription description,
                                                         ReplicaPoolBuilder replicaPoolBuilder) {
    this.description = description
    this.replicaPoolBuilder = replicaPoolBuilder
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "modifyGoogleServerGroupInstanceTemplateDescription": { "replicaPoolName": "myapp-dev-v000", "zone": "us-central1-f", "instanceType": "n1-standard-2", "tags": ["some-tag-1", "some-tag-2"], "credentials": "my-account-name" }} ]' localhost:7002/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Modify Google Server Group Instance Template operation for $description.replicaPoolName..."

    def compute = description.credentials.compute
    def project = description.credentials.project
    def zone = description.zone
    def replicaPoolName = description.replicaPoolName
    def credentialBuilder = description.credentials.createCredentialBuilder(ReplicapoolScopes.COMPUTE)

    def replicaPool = replicaPoolBuilder.buildReplicaPool(credentialBuilder, GCEUtil.APPLICATION_NAME)
    def instanceGroupManagers = replicaPool.instanceGroupManagers()
    def instanceTemplates = compute.instanceTemplates()

    // Retrieve the managed instance group.
    def managedInstanceGroup = instanceGroupManagers.get(project, zone, replicaPoolName).execute()
    def origInstanceTemplateName = GCEUtil.getLocalName(managedInstanceGroup.instanceTemplate)

    if (!origInstanceTemplateName) {
      throw new GoogleResourceNotFoundException("Unable to determine instance template for server group $replicaPoolName.")
    }

    // Retrieve the managed instance group's current instance template.
    def instanceTemplate = instanceTemplates.get(project, origInstanceTemplateName).execute()

    // Create a description to represent the current instance template.
    def originalDescription = GCEUtil.buildInstanceDescriptionFromTemplate(instanceTemplate)

    // Collect the properties of the description passed to the operation.
    def properties = [:] + description.properties

    // Remove the properties we don't want to compare or override.
    properties.keySet().removeAll(["class", "replicaPoolName", "zone", "accountName", "credentials"])

    // Collect all of the map entries with non-null values into a new map.
    def overriddenProperties = properties.findResults { key, value ->
      value != null ? [(key): value] : null
    }.collectEntries()

    // Build a new set of properties by overriding the existing set with any that were specified in the call.
    def newDescriptionProperties = [:] + originalDescription.properties + overriddenProperties

    // Remove the properties we don't want to compare or override.
    newDescriptionProperties.keySet().removeAll(["class"])

    // Create a description to represent the current instance template after overriding the specified properties.
    def newDescription = new BaseGoogleInstanceDescription(newDescriptionProperties)

    if (newDescription == originalDescription) {
      task.updateStatus BASE_PHASE, "No changes required for instance template of $replicaPoolName in $zone."
    } else {
      def instanceTemplateProperties = instanceTemplate.properties

      // Override the instance template's name.
      instanceTemplate.setName("$replicaPoolName-${System.currentTimeMillis()}")

      // Override the instance template's disk configuration if image, diskType, diskSizeGb or instanceType was specified.
      if (overriddenProperties.image
          || overriddenProperties.diskType
          || overriddenProperties.diskSizeGb
          || overriddenProperties.instanceType) {
        def sourceImage = GCEUtil.querySourceImage(project, newDescription.image, compute, task, BASE_PHASE)
        def attachedDisk = GCEUtil.buildAttachedDisk(project,
                                                     zone,
                                                     sourceImage,
                                                     newDescription.diskSizeGb,
                                                     newDescription.diskType,
                                                     false,
                                                     newDescription.instanceType,
                                                     gceDeployDefaults)

        instanceTemplateProperties.setDisks([attachedDisk])
      }

      // Override the instance template's machine type if instanceType was specified.
      if (overriddenProperties.instanceType) {
        def machineType = GCEUtil.queryMachineType(project, zone, description.instanceType, compute, task, BASE_PHASE)

        instanceTemplateProperties.setMachineType(machineType.name)
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

      // Override the instance template's network if network was specified.
      if (overriddenProperties.network) {
        def network = GCEUtil.queryNetwork(project, newDescription.network, compute, task, BASE_PHASE)
        def networkInterface = GCEUtil.buildNetworkInterface(network, accessConfigName, accessConfigType)

        instanceTemplateProperties.setNetworkInterfaces([networkInterface])
      }

      // Create a new instance template resource using the modified instance template.
      task.updateStatus BASE_PHASE, "Inserting new instance template $instanceTemplate.name..."

      def instanceTemplateCreateOperation = instanceTemplates.insert(project, instanceTemplate).execute()
      def instanceTemplateUrl = instanceTemplateCreateOperation.targetLink

      // Block on creating the instance template.
      googleOperationPoller.waitForGlobalOperation(compute, project, instanceTemplateCreateOperation.getName(),
          null, task, "instance template $instanceTemplate.name", BASE_PHASE)

      // Set the new instance template on the managed instance group.
      task.updateStatus BASE_PHASE, "Setting instance template $instanceTemplate.name on server group $replicaPoolName..."

      def instanceGroupManagersSetInstanceTemplateRequest =
          new InstanceGroupManagersSetInstanceTemplateRequest(instanceTemplate: instanceTemplateUrl)
      def setInstanceTemplateOperation = instanceGroupManagers.setInstanceTemplate(
          project, zone, replicaPoolName, instanceGroupManagersSetInstanceTemplateRequest).execute()

      // Block on setting the instance template on the managed instance group.
      googleOperationPoller.waitForReplicaPoolZonalOperation(replicaPool, project, zone,
          setInstanceTemplateOperation.getName(), null, task, "server group $replicaPoolName", BASE_PHASE)

      // Delete the original instance template.
      task.updateStatus BASE_PHASE, "Deleting original instance template $origInstanceTemplateName..."

      instanceTemplates.delete(project, origInstanceTemplateName).execute()
    }

    task.updateStatus BASE_PHASE, "Done modifying instance template of $replicaPoolName in $zone."
    null
  }
}
