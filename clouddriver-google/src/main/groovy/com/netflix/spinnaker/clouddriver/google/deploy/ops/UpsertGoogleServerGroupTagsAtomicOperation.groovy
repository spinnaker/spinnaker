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
import com.google.api.services.compute.model.InstanceGroupsListInstancesRequest
import com.google.api.services.compute.model.Tags
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleServerGroupTagsDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleResourceNotFoundException
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

/**
 * Update the set of tags defined on a managed instance group's instance template and on all of the group's instances.
 * As the instance template itself is immutable, a new instance template is created and set on the managed instance
 * group.
 *
 * Uses {@link https://cloud.google.com/compute/docs/reference/latest/instanceGroupManagers/setInstanceTemplate}
 * Uses {@link https://cloud.google.com/compute/docs/reference/latest/instances/setTags}
 */
class UpsertGoogleServerGroupTagsAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "UPSERT_SERVER_GROUP_TAGS"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final UpsertGoogleServerGroupTagsDescription description

  @Autowired
  GoogleOperationPoller googleOperationPoller

  @Autowired
  GoogleClusterProvider googleClusterProvider

  UpsertGoogleServerGroupTagsAtomicOperation(UpsertGoogleServerGroupTagsDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertServerGroupTags": { "serverGroupName": "myapp-dev-v000", "zone": "us-central1-f", "tags": ["some-tag-1", "some-tag-2"], "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing upsert of server group tags for $description.serverGroupName in " +
      "$description.region..."

    def compute = description.credentials.compute
    def project = description.credentials.project
    def region = description.region
    def serverGroupName = description.serverGroupName
    def serverGroup = GCEUtil.queryServerGroup(googleClusterProvider, description.accountName, region, serverGroupName)
    def zone = serverGroup.zone
    def tagsDescription = description.tags ? "tags $description.tags" : "empty set of tags"

    def instanceGroupManagers = compute.instanceGroupManagers()
    def instanceTemplates = compute.instanceTemplates()
    def instances = compute.instances()

    // Retrieve the managed instance group.
    def managedInstanceGroup = instanceGroupManagers.get(project, zone, serverGroupName).execute()
    def origInstanceTemplateName = GCEUtil.getLocalName(managedInstanceGroup.instanceTemplate)

    if (!origInstanceTemplateName) {
      throw new GoogleResourceNotFoundException("Unable to determine instance template for server group " +
        "$serverGroupName.")
    }

    // Retrieve the managed instance group's current instance template.
    def instanceTemplate = instanceTemplates.get(project, origInstanceTemplateName).execute()

    // Override the instance template's name.
    instanceTemplate.setName("$serverGroupName-${System.currentTimeMillis()}")

    // Override the instance template's tags with the new set.
    def tags = new Tags(items: description.tags)
    instanceTemplate.properties.setTags(tags)

    // Create a new instance template resource using the modified instance template.
    task.updateStatus BASE_PHASE, "Inserting new instance template $instanceTemplate.name with $tagsDescription..."

    def instanceTemplateCreateOperation = instanceTemplates.insert(project, instanceTemplate).execute()
    def instanceTemplateUrl = instanceTemplateCreateOperation.targetLink

    // Block on creating the instance template.
    googleOperationPoller.waitForGlobalOperation(compute, project, instanceTemplateCreateOperation.getName(),
        null, task, "instance template $instanceTemplate.name", BASE_PHASE)

    // Set the new instance template on the managed instance group.
    task.updateStatus BASE_PHASE, "Setting instance template $instanceTemplate.name on server group $serverGroupName..."

    def instanceGroupManagersSetInstanceTemplateRequest =
        new InstanceGroupManagersSetInstanceTemplateRequest(instanceTemplate: instanceTemplateUrl)
    def setInstanceTemplateOperation = instanceGroupManagers.setInstanceTemplate(
        project, zone, serverGroupName, instanceGroupManagersSetInstanceTemplateRequest).execute()

    // Block on setting the instance template on the managed instance group.
    googleOperationPoller.waitForZonalOperation(compute, project, zone,
        setInstanceTemplateOperation.getName(), null, task, "server group $serverGroupName", BASE_PHASE)

    // Retrieve the name of the instance group being managed by the instance group manager.
    def instanceGroupName = GCEUtil.getLocalName(managedInstanceGroup.instanceGroup)

    // Retrieve the instances in the instance group.
    def groupInstances = compute.instanceGroups().listInstances(project,
                                                                zone,
                                                                instanceGroupName,
                                                                new InstanceGroupsListInstancesRequest()).execute().items

    // Set the new tags on all instances in the group (in parallel).
    task.updateStatus BASE_PHASE, "Setting $tagsDescription on each instance in server group $serverGroupName..."

    def instanceUpdateOperations = []

    groupInstances.each { groupInstance ->
      def localInstanceName = GCEUtil.getLocalName(groupInstance.instance)
      def instance = instances.get(project, zone, localInstanceName).execute()
      def tagsFingerprint = instance.tags.fingerprint

      tags.fingerprint = tagsFingerprint

      instanceUpdateOperations << instances.setTags(project, zone, localInstanceName, tags).execute()
    }

    // Block on setting the tags on each instance.
    instanceUpdateOperations.each { instanceUpdateOperation ->
      def localInstanceName = GCEUtil.getLocalName(instanceUpdateOperation.targetLink)

      googleOperationPoller.waitForZonalOperation(compute, project, zone, instanceUpdateOperation.getName(),
          null, task, "instance $localInstanceName", BASE_PHASE)
    }

    // Delete the original instance template.
    task.updateStatus BASE_PHASE, "Deleting original instance template $origInstanceTemplateName..."

    instanceTemplates.delete(project, origInstanceTemplateName).execute()

    task.updateStatus BASE_PHASE, "Done tagging server group $serverGroupName in $region."
    null
  }
}
