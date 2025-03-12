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

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.InstanceGroupManager
import com.google.api.services.compute.model.InstanceGroupManagersSetInstanceTemplateRequest
import com.google.api.services.compute.model.InstanceGroupsListInstancesRequest
import com.google.api.services.compute.model.RegionInstanceGroupManagersSetTemplateRequest
import com.google.api.services.compute.model.RegionInstanceGroupsListInstancesRequest
import com.google.api.services.compute.model.Tags
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleServerGroupTagsDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleResourceNotFoundException
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import org.springframework.beans.factory.annotation.Autowired

/**
 * Update the set of tags defined on a managed instance group's instance template and on all of the group's instances.
 * As the instance template itself is immutable, a new instance template is created and set on the managed instance
 * group.
 *
 * Uses {@link https://cloud.google.com/compute/docs/reference/latest/instanceGroupManagers/setInstanceTemplate}
 * Uses {@link https://cloud.google.com/compute/docs/reference/latest/instances/setTags}
 */
class UpsertGoogleServerGroupTagsAtomicOperation extends GoogleAtomicOperation<Void> {
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

    def accountName = description.accountName
    def credentials = description.credentials
    def compute = credentials.compute
    def project = credentials.project
    def region = description.region
    def serverGroupName = description.serverGroupName
    def serverGroup = GCEUtil.queryServerGroup(googleClusterProvider, accountName, region, serverGroupName)
    def isRegional = serverGroup.regional
    // Will return null if this is a regional server group.
    def zone = serverGroup.zone
    def tagsDescription = description.tags ? "tags $description.tags" : "empty set of tags"

    def regionalInstanceGroupManagers = compute.regionInstanceGroupManagers()
    def instanceGroupManagers = compute.instanceGroupManagers()
    def instanceTemplates = compute.instanceTemplates()
    def instances = compute.instances()

    // Retrieve the managed instance group.
    def managedInstanceGroup =
      isRegional
      ? timeExecute(
            regionalInstanceGroupManagers.get(project, region, serverGroupName),
            "compute.regionInstanceGroupManagers.get",
            TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
      : timeExecute(
            instanceGroupManagers.get(project, zone, serverGroupName),
            "compute.instanceGroupManagers.get",
            TAG_SCOPE, SCOPE_ZONAL, TAG_ZONE, zone)

    def origInstanceTemplateName = GCEUtil.getLocalName(managedInstanceGroup.getInstanceTemplate())

    def instanceTemplate = GCEUtil.queryInstanceTemplate(origInstanceTemplateName, credentials, this)

    // Override the instance template's name.
    instanceTemplate.setName("$serverGroupName-${System.currentTimeMillis()}")

    // Override the instance template's tags with the new set.
    def tags = new Tags(items: description.tags)
    instanceTemplate.properties.setTags(tags)

    // Create a new instance template resource using the modified instance template.
    task.updateStatus BASE_PHASE, "Inserting new instance template $instanceTemplate.name with $tagsDescription..."

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

    def groupInstances

    if (isRegional) {
      def regionInstanceGroupManagersSetTemplateRequest =
        new RegionInstanceGroupManagersSetTemplateRequest(instanceTemplate: instanceTemplateUrl)
      def setInstanceTemplateOperation = timeExecute(
              regionalInstanceGroupManagers.setInstanceTemplate(
              project, region, serverGroupName, regionInstanceGroupManagersSetTemplateRequest),
              "compute.regionInstanceGroupManagers.setInstanceTemplate",
              TAG_SCOPE, SCOPE_REGIONAL. TAG_REGION, region)

      // Block on setting the instance template on the managed instance group.
      googleOperationPoller.waitForRegionalOperation(compute, project, region,
        setInstanceTemplateOperation.getName(), null, task, "server group $serverGroupName", BASE_PHASE)

      // Retrieve the instances in the instance group.
      groupInstances = timeExecute(
        compute.regionInstanceGroups().listInstances(project,
                                                     region,
                                                     serverGroupName,
                                                     new RegionInstanceGroupsListInstancesRequest()),
        "compute.regionInstanceGroups.listInstances",
        TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region).items

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

      // Retrieve the instances in the instance group.
      groupInstances = timeExecute(
        compute.instanceGroups().listInstances(project,
                                               zone,
                                               serverGroupName,
                                               new InstanceGroupsListInstancesRequest()),
        "compute.instanceGroups.listInstance",
        TAG_SCOPE, SCOPE_ZONAL, TAG_ZONE, zone).items
    }

    // Set the new tags on all instances in the group (in parallel).
    task.updateStatus BASE_PHASE, "Setting $tagsDescription on each instance in server group $serverGroupName..."

    def instanceUpdateOperations = []

    groupInstances.each { groupInstance ->
      def localInstanceName = GCEUtil.getLocalName(groupInstance.instance)
      def instanceZone = getZoneFromInstanceUrl(groupInstance.instance)
      def instance = timeExecute(instances.get(project, instanceZone, localInstanceName),
              "compute.instances.get",
              TAG_SCOPE, SCOPE_ZONAL, TAG_ZONE, instanceZone)
      def tagsFingerprint = instance.tags.fingerprint

      tags.fingerprint = tagsFingerprint

      instanceUpdateOperations << timeExecute(
          instances.setTags(project, instanceZone, localInstanceName, tags),
          "compute.instances.setTags",
          TAG_SCOPE, SCOPE_ZONAL, TAG_ZONE, instanceZone)
    }

    // Block on setting the tags on each instance.
    instanceUpdateOperations.each { instanceUpdateOperation ->
      def localInstanceName = GCEUtil.getLocalName(instanceUpdateOperation.targetLink)

      googleOperationPoller.waitForZonalOperation(compute,
                                                  project,
                                                  GCEUtil.getLocalName(instanceUpdateOperation.getZone()),
                                                  instanceUpdateOperation.getName(),
                                                  null,
                                                  task,
                                                  "instance $localInstanceName",
                                                  BASE_PHASE)
    }

    // Delete the original instance template.
    task.updateStatus BASE_PHASE, "Deleting original instance template $origInstanceTemplateName..."

    timeExecute(
        instanceTemplates.delete(project, origInstanceTemplateName),
        "compute.instanceTemplates.delete",
        TAG_SCOPE, SCOPE_GLOBAL)

    task.updateStatus BASE_PHASE, "Done tagging server group $serverGroupName in $region."
    null
  }

  private static String getZoneFromInstanceUrl(String instanceUrl) {
    if (!instanceUrl) {
      return null
    }

    int indexOfZonesSegment = instanceUrl.indexOf("/zones/")
    int indexOfInstancesSegment = instanceUrl.indexOf("/instances/")

    if (indexOfZonesSegment == -1 || indexOfInstancesSegment == -1) {
      return null
    }

    return instanceUrl.substring(indexOfZonesSegment + 7, indexOfInstancesSegment)
  }
}
