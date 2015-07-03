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

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.Instance
import com.google.api.services.compute.model.InstanceProperties
import com.google.api.services.compute.model.InstanceTemplate
import com.google.api.services.compute.model.Operation
import com.google.api.services.compute.model.Tags
import com.google.api.services.replicapool.Replicapool
import com.google.api.services.replicapool.model.InstanceGroupManager
import com.google.api.services.resourceviews.Resourceviews
import com.google.api.services.resourceviews.model.ListResourceResponseItem
import com.google.api.services.resourceviews.model.ZoneViewsListResourcesResponse
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GoogleOperationPoller
import com.netflix.spinnaker.kato.gce.deploy.description.UpsertGoogleServerGroupTagsDescription
import spock.lang.Specification
import spock.lang.Subject

class UpsertGoogleServerGroupTagsAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final REPLICA_POOL_NAME = "spinnaker-test-v000"
  private static final ZONE = "us-central1-b"
  private static final TAGS = ["some-tag-1", "some-tag-2", "some-tag-3"]
  private static final ORIG_INSTANCE_TEMPLATE_NAME = "$REPLICA_POOL_NAME-123"
  private static final ORIG_INSTANCE_TEMPLATE_URL =
      "https://www.googleapis.com/compute/v1/projects/$PROJECT_NAME/global/instanceTemplates/$ORIG_INSTANCE_TEMPLATE_NAME"
  private static final NEW_INSTANCE_TEMPLATE_NAME = "new-instance-template"
  private static final INSTANCE_TEMPLATE_INSERTION_OP_NAME = "instance-template-insertion-op"
  private static final SET_INSTANCE_TEMPLATE_OP_NAME = "set-instance-template-op"
  private static final INSTANCE_1_NAME = "instance-1"
  private static final INSTANCE_2_NAME = "instance-2"
  private static final INSTANCE_1_URL =
      "https://www.googleapis.com/compute/v1/projects/$PROJECT_NAME/zones/$ZONE/instances/$INSTANCE_1_NAME"
  private static final INSTANCE_2_URL =
      "https://www.googleapis.com/compute/v1/projects/$PROJECT_NAME/zones/$ZONE/instances/$INSTANCE_2_NAME"
  private static final DONE = "DONE"
  private static final INSTANCES_SET_TAGS_1_OP_NAME = "instances-set-tags-1-op"
  private static final INSTANCES_SET_TAGS_2_OP_NAME = "instances-set-tags-2-op"

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should set tags on new instance template and on instances"() {
    setup:
      def computeMock = Mock(Compute)
      def globalOperations = Mock(Compute.GlobalOperations)
      def zonalOperations = Mock(Compute.ZoneOperations)
      def instanceTemplatesMock = Mock(Compute.InstanceTemplates)
      def instanceTemplatesGetMock = Mock(Compute.InstanceTemplates.Get)
      def instanceTemplateReal = new InstanceTemplate(properties: new InstanceProperties())
      def instanceTemplatesInsertMock = Mock(Compute.InstanceTemplates.Insert)
      def instanceTemplateInsertionOperationGetMock = Mock(Compute.GlobalOperations.Get)
      def instanceTemplateInsertionOperationReal = new Operation(targetLink: NEW_INSTANCE_TEMPLATE_NAME,
                                                                 name: INSTANCE_TEMPLATE_INSERTION_OP_NAME,
                                                                 status: DONE)
      def replicaPoolBuilderMock = Mock(ReplicaPoolBuilder)
      def replicaPoolMock = Mock(Replicapool)
      def replicaPoolZonalOperations = Mock(Replicapool.ZoneOperations)
      def instanceGroupManagersMock = Mock(Replicapool.InstanceGroupManagers)
      def instanceGroupManagersGetMock = Mock(Replicapool.InstanceGroupManagers.Get)
      def instanceGroupManagerReal = new InstanceGroupManager(instanceTemplate: ORIG_INSTANCE_TEMPLATE_URL, group: REPLICA_POOL_NAME)
      def setInstanceTemplateMock = Mock(Replicapool.InstanceGroupManagers.SetInstanceTemplate)
      def setInstanceTemplateOperationReal = new Operation(targetLink: REPLICA_POOL_NAME,
                                                           name: SET_INSTANCE_TEMPLATE_OP_NAME,
                                                           status: DONE)
      def setInstanceTemplateOperationGetMock = Mock(Replicapool.ZoneOperations.Get)
      def resourceViewsBuilderMock = Mock(ResourceViewsBuilder)
      def resourceViewsMock = Mock(Resourceviews)
      def resourceViewsZoneViewsMock = Mock(Resourceviews.ZoneViews)
      def resourceViewsZoneViewsListResourcesMock = Mock(Resourceviews.ZoneViews.ListResources)
      def listResourceResponseItem1Real = new ListResourceResponseItem(resource: INSTANCE_1_URL)
      def listResourceResponseItem2Real = new ListResourceResponseItem(resource: INSTANCE_2_URL)
      def listResourceResponseItemsReal = [listResourceResponseItem1Real, listResourceResponseItem2Real]
      def zoneViewsListResourcesResponseReal = new ZoneViewsListResourcesResponse(items: listResourceResponseItemsReal)
      def instancesMock = Mock(Compute.Instances)
      def instancesGet1Mock = Mock(Compute.Instances.Get)
      def instance1Real = new Instance(tags: new Tags())
      def instancesSetTags1Mock = Mock(Compute.Instances.SetTags)
      def instancesSetTagsOperation1Real = new Operation(targetLink: INSTANCE_1_URL,
                                                         name: INSTANCES_SET_TAGS_1_OP_NAME,
                                                         status: DONE)
      def instancesGet2Mock = Mock(Compute.Instances.Get)
      def instance2Real = new Instance(tags: new Tags())
      def instancesSetTags2Mock = Mock(Compute.Instances.SetTags)
      def instancesSetTagsOperation2Real = new Operation(targetLink: INSTANCE_2_URL,
                                                         name: INSTANCES_SET_TAGS_2_OP_NAME,
                                                         status: DONE)
      def instancesSetTagsOperation1GetMock = Mock(Compute.ZoneOperations.Get)
      def instancesSetTagsOperation2GetMock = Mock(Compute.ZoneOperations.Get)
      def instanceTemplatesDeleteMock = Mock(Compute.InstanceTemplates.Delete)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleServerGroupTagsDescription(replicaPoolName: REPLICA_POOL_NAME,
                                                                   zone: ZONE,
                                                                   tags: TAGS,
                                                                   accountName: ACCOUNT_NAME,
                                                                   credentials: credentials)
      @Subject def operation = new UpsertGoogleServerGroupTagsAtomicOperation(description, replicaPoolBuilderMock, resourceViewsBuilderMock)
      operation.googleOperationPoller =
          new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      // Query the managed instance group and its instance template.
      1 * replicaPoolBuilderMock.buildReplicaPool(_, _) >> replicaPoolMock
      1 * replicaPoolMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, REPLICA_POOL_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManagerReal
      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.get(PROJECT_NAME, ORIG_INSTANCE_TEMPLATE_NAME) >> instanceTemplatesGetMock
      1 * instanceTemplatesGetMock.execute() >> instanceTemplateReal

      // Insert the new instance template.
      1 * instanceTemplatesMock.insert(PROJECT_NAME, {
        // Verify that the new instance template has a different name than the original instance template.
        it.name != ORIG_INSTANCE_TEMPLATE_NAME && it.properties.tags.items == TAGS
      }) >> instanceTemplatesInsertMock
      1 * instanceTemplatesInsertMock.execute() >> instanceTemplateInsertionOperationReal
      1 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, INSTANCE_TEMPLATE_INSERTION_OP_NAME) >> instanceTemplateInsertionOperationGetMock
      1 * instanceTemplateInsertionOperationGetMock.execute() >> instanceTemplateInsertionOperationReal

      // Set the new instance template on the managed instance group.
      1 * instanceGroupManagersMock.setInstanceTemplate(PROJECT_NAME, ZONE, REPLICA_POOL_NAME, {
        // Verify that the target link of the instance creation operation is used to set the new instance template.
        it.instanceTemplate == NEW_INSTANCE_TEMPLATE_NAME
      }) >> setInstanceTemplateMock
      1 * setInstanceTemplateMock.execute() >> setInstanceTemplateOperationReal
      1 * replicaPoolMock.zoneOperations() >> replicaPoolZonalOperations
      1 * replicaPoolZonalOperations.get(PROJECT_NAME, ZONE, SET_INSTANCE_TEMPLATE_OP_NAME) >> setInstanceTemplateOperationGetMock
      1 * setInstanceTemplateOperationGetMock.execute() >> setInstanceTemplateOperationReal

      // Query the instance group's instances.
      1 * resourceViewsBuilderMock.buildResourceViews(_, _) >> resourceViewsMock
      1 * resourceViewsMock.zoneViews() >> resourceViewsZoneViewsMock
      1 * resourceViewsZoneViewsMock.listResources(PROJECT_NAME, ZONE, REPLICA_POOL_NAME) >> resourceViewsZoneViewsListResourcesMock
      1 * resourceViewsZoneViewsListResourcesMock.execute() >> zoneViewsListResourcesResponseReal

      // Query the first instance.
      1 * computeMock.instances() >> instancesMock
      1 * instancesMock.get(PROJECT_NAME, ZONE, INSTANCE_1_NAME) >> instancesGet1Mock
      1 * instancesGet1Mock.execute() >> instance1Real

      // Set the tags on the first instance.
      1 * instancesMock.setTags(PROJECT_NAME, ZONE, INSTANCE_1_NAME, { it.items == TAGS }) >> instancesSetTags1Mock
      1 * instancesSetTags1Mock.execute() >> instancesSetTagsOperation1Real

      // Query the second instance.
      1 * instancesMock.get(PROJECT_NAME, ZONE, INSTANCE_2_NAME) >> instancesGet2Mock
      1 * instancesGet2Mock.execute() >> instance2Real

      // Set the tags on the second instance.
      1 * instancesMock.setTags(PROJECT_NAME, ZONE, INSTANCE_2_NAME, { it.items == TAGS }) >> instancesSetTags2Mock
      1 * instancesSetTags2Mock.execute() >> instancesSetTagsOperation2Real

      // Poll until each set tags operation completes.
      2 * computeMock.zoneOperations() >> zonalOperations
      1 * zonalOperations.get(PROJECT_NAME, ZONE, INSTANCES_SET_TAGS_1_OP_NAME) >> instancesSetTagsOperation1GetMock
      1 * instancesSetTagsOperation1GetMock.execute() >> instancesSetTagsOperation1Real

      1 * zonalOperations.get(PROJECT_NAME, ZONE, INSTANCES_SET_TAGS_2_OP_NAME) >> instancesSetTagsOperation2GetMock
      1 * instancesSetTagsOperation2GetMock.execute() >> instancesSetTagsOperation2Real

      // Delete the original instance template.
      1 * instanceTemplatesMock.delete(PROJECT_NAME, ORIG_INSTANCE_TEMPLATE_NAME) >> instanceTemplatesDeleteMock
      1 * instanceTemplatesDeleteMock.execute()
  }

  void "should set tags on new instance template even if managed instance group has no instances"() {
    setup:
      def computeMock = Mock(Compute)
      def globalOperations = Mock(Compute.GlobalOperations)
      def instanceTemplatesMock = Mock(Compute.InstanceTemplates)
      def instanceTemplatesGetMock = Mock(Compute.InstanceTemplates.Get)
      def instanceTemplateReal = new InstanceTemplate(properties: new InstanceProperties())
      def instanceTemplatesInsertMock = Mock(Compute.InstanceTemplates.Insert)
      def instanceTemplateInsertionOperationGetMock = Mock(Compute.GlobalOperations.Get)
      def instanceTemplateInsertionOperationReal = new Operation(targetLink: NEW_INSTANCE_TEMPLATE_NAME,
                                                                 name: INSTANCE_TEMPLATE_INSERTION_OP_NAME,
                                                                 status: DONE)
      def replicaPoolBuilderMock = Mock(ReplicaPoolBuilder)
      def replicaPoolMock = Mock(Replicapool)
      def replicaPoolZonalOperations = Mock(Replicapool.ZoneOperations)
      def instanceGroupManagersMock = Mock(Replicapool.InstanceGroupManagers)
      def instanceGroupManagersGetMock = Mock(Replicapool.InstanceGroupManagers.Get)
      def instanceGroupManagerReal = new InstanceGroupManager(instanceTemplate: ORIG_INSTANCE_TEMPLATE_URL, group: REPLICA_POOL_NAME)
      def setInstanceTemplateMock = Mock(Replicapool.InstanceGroupManagers.SetInstanceTemplate)
      def setInstanceTemplateOperationReal = new Operation(targetLink: REPLICA_POOL_NAME,
                                                           name: SET_INSTANCE_TEMPLATE_OP_NAME,
                                                           status: DONE)
      def setInstanceTemplateOperationGetMock = Mock(Replicapool.ZoneOperations.Get)
      def resourceViewsBuilderMock = Mock(ResourceViewsBuilder)
      def resourceViewsMock = Mock(Resourceviews)
      def resourceViewsZoneViewsMock = Mock(Resourceviews.ZoneViews)
      def resourceViewsZoneViewsListResourcesMock = Mock(Resourceviews.ZoneViews.ListResources)
      def zoneViewsListResourcesResponseReal = new ZoneViewsListResourcesResponse()
      def instanceTemplatesDeleteMock = Mock(Compute.InstanceTemplates.Delete)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleServerGroupTagsDescription(replicaPoolName: REPLICA_POOL_NAME,
                                                                   zone: ZONE,
                                                                   tags: TAGS,
                                                                   accountName: ACCOUNT_NAME,
                                                                   credentials: credentials)
      @Subject def operation = new UpsertGoogleServerGroupTagsAtomicOperation(description, replicaPoolBuilderMock, resourceViewsBuilderMock)
      operation.googleOperationPoller =
          new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      // Query the managed instance group and its instance template.
      1 * replicaPoolBuilderMock.buildReplicaPool(_, _) >> replicaPoolMock
      1 * replicaPoolMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, REPLICA_POOL_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManagerReal
      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.get(PROJECT_NAME, ORIG_INSTANCE_TEMPLATE_NAME) >> instanceTemplatesGetMock
      1 * instanceTemplatesGetMock.execute() >> instanceTemplateReal

      // Insert the new instance template.
      1 * instanceTemplatesMock.insert(PROJECT_NAME, {
        // Verify that the new instance template has a different name than the original instance template.
        it.name != ORIG_INSTANCE_TEMPLATE_NAME && it.properties.tags.items == TAGS
      }) >> instanceTemplatesInsertMock
      1 * instanceTemplatesInsertMock.execute() >> instanceTemplateInsertionOperationReal
      1 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, INSTANCE_TEMPLATE_INSERTION_OP_NAME) >> instanceTemplateInsertionOperationGetMock
      1 * instanceTemplateInsertionOperationGetMock.execute() >> instanceTemplateInsertionOperationReal

      // Set the new instance template on the managed instance group.
      1 * instanceGroupManagersMock.setInstanceTemplate(PROJECT_NAME, ZONE, REPLICA_POOL_NAME, {
        // Verify that the target link of the instance creation operation is used to set the new instance template.
        it.instanceTemplate == NEW_INSTANCE_TEMPLATE_NAME
      }) >> setInstanceTemplateMock
      1 * setInstanceTemplateMock.execute() >> setInstanceTemplateOperationReal
      1 * replicaPoolMock.zoneOperations() >> replicaPoolZonalOperations
      1 * replicaPoolZonalOperations.get(PROJECT_NAME, ZONE, SET_INSTANCE_TEMPLATE_OP_NAME) >> setInstanceTemplateOperationGetMock
      1 * setInstanceTemplateOperationGetMock.execute() >> setInstanceTemplateOperationReal

      // Query the instance group's instances.
      1 * resourceViewsBuilderMock.buildResourceViews(_, _) >> resourceViewsMock
      1 * resourceViewsMock.zoneViews() >> resourceViewsZoneViewsMock
      1 * resourceViewsZoneViewsMock.listResources(PROJECT_NAME, ZONE, REPLICA_POOL_NAME) >> resourceViewsZoneViewsListResourcesMock
      1 * resourceViewsZoneViewsListResourcesMock.execute() >> zoneViewsListResourcesResponseReal

      // Delete the original instance template.
      1 * instanceTemplatesMock.delete(PROJECT_NAME, ORIG_INSTANCE_TEMPLATE_NAME) >> instanceTemplatesDeleteMock
      1 * instanceTemplatesDeleteMock.execute()
  }
}
