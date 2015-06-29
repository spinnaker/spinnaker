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

import com.google.api.services.compute.model.Tags
import com.google.api.services.replicapool.ReplicapoolScopes
import com.google.api.services.replicapool.model.InstanceGroupManagersSetInstanceTemplateRequest
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.GoogleOperationPoller
import com.netflix.spinnaker.kato.gce.deploy.description.UpsertGoogleServerGroupTagsDescription
import com.netflix.spinnaker.kato.gce.deploy.exception.GoogleResourceNotFoundException
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

/**
 * Update the set of tags defined on a managed instance group's instance template and on all of the group's instances.
 * As the instance template itself is immutable, a new instance template is created and set on the managed instance
 * group.
 *
 * Uses {@link https://cloud.google.com/compute/docs/instance-groups/manager/v1beta2/instanceGroupManagers/setInstanceTemplate}
 * Uses {@link https://cloud.google.com/compute/docs/reference/latest/instances/setTags}
 */
class UpsertGoogleServerGroupTagsAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "UPSERT_SERVER_GROUP_TAGS"

  @Autowired
  private GoogleOperationPoller googleOperationPoller

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final UpsertGoogleServerGroupTagsDescription description
  private final ReplicaPoolBuilder replicaPoolBuilder
  private final ResourceViewsBuilder resourceViewsBuilder

  UpsertGoogleServerGroupTagsAtomicOperation(UpsertGoogleServerGroupTagsDescription description,
                                             ReplicaPoolBuilder replicaPoolBuilder,
                                             ResourceViewsBuilder resourceViewsBuilder) {
    this.description = description
    this.replicaPoolBuilder = replicaPoolBuilder
    this.resourceViewsBuilder = resourceViewsBuilder
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertGoogleServerGroupTagsDescription": { "replicaPoolName": "myapp-dev-v000", "zone": "us-central1-b", "tags": ["some-tag-1", "some-tag-2"], "credentials": "my-account-name" }} ]' localhost:8501/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Upsert Google Server Group Tags operation for $description.replicaPoolName..."

    def compute = description.credentials.compute
    def project = description.credentials.project
    def zone = description.zone
    def tagsDescription = description.tags ? "tags $description.tags" : "empty set of tags"
    def replicaPoolName = description.replicaPoolName
    def credentialBuilder = description.credentials.createCredentialBuilder(ReplicapoolScopes.COMPUTE)

    def replicaPool = replicaPoolBuilder.buildReplicaPool(credentialBuilder, GCEUtil.APPLICATION_NAME)
    def instanceGroupManagers = replicaPool.instanceGroupManagers()
    def instanceTemplates = compute.instanceTemplates()
    def instances = compute.instances()

    // Retrieve the managed instance group.
    def managedInstanceGroup = instanceGroupManagers.get(project, zone, replicaPoolName).execute()
    def origInstanceTemplateName = GCEUtil.getLocalName(managedInstanceGroup.instanceTemplate)

    if (!origInstanceTemplateName) {
      throw new GoogleResourceNotFoundException("Unable to determine instance template for server group $replicaPoolName.")
    }

    // Retrieve the managed instance group's current instance template.
    def instanceTemplate = instanceTemplates.get(project, origInstanceTemplateName).execute()

    // Override the instance template's name.
    instanceTemplate.setName("$replicaPoolName-${System.currentTimeMillis()}")

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
    task.updateStatus BASE_PHASE, "Setting instance template $instanceTemplate.name on server group $replicaPoolName..."

    def instanceGroupManagersSetInstanceTemplateRequest =
        new InstanceGroupManagersSetInstanceTemplateRequest(instanceTemplate: instanceTemplateUrl)
    def setInstanceTemplateOperation = instanceGroupManagers.setInstanceTemplate(
        project, zone, replicaPoolName, instanceGroupManagersSetInstanceTemplateRequest).execute()

    // Block on setting the instance template on the managed instance group.
    googleOperationPoller.waitForReplicaPoolZonalOperation(replicaPool, project, zone,
        setInstanceTemplateOperation.getName(), null, task, "server group $replicaPoolName", BASE_PHASE)

    // Retrieve the name of the instance group being managed by the instance group manager.
    def instanceGroupName = GCEUtil.getLocalName(managedInstanceGroup.group)

    // Retrieve the instances in the instance group.
    def resourceViews = resourceViewsBuilder.buildResourceViews(credentialBuilder, GCEUtil.APPLICATION_NAME)
    def resourceItems = resourceViews.zoneViews().listResources(project,
                                                                zone,
                                                                instanceGroupName).execute().items

    // Set the new tags on all instances in the group (in parallel).
    task.updateStatus BASE_PHASE, "Setting $tagsDescription on each instance in server group $replicaPoolName..."

    def instanceUpdateOperations = []

    resourceItems.each { resourceItem ->
      def localInstanceName = GCEUtil.getLocalName(resourceItem.resource)
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

    task.updateStatus BASE_PHASE, "Done tagging server group $replicaPoolName."
    null
  }
}
