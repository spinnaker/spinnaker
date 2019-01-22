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
import com.google.api.services.compute.model.Instance
import com.google.api.services.compute.model.InstanceGroupManager
import com.google.api.services.compute.model.InstanceGroupsListInstances
import com.google.api.services.compute.model.InstanceProperties
import com.google.api.services.compute.model.InstanceTemplate
import com.google.api.services.compute.model.InstanceWithNamedPorts
import com.google.api.services.compute.model.Operation
import com.google.api.services.compute.model.Tags
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleServerGroupTagsDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class UpsertGoogleServerGroupTagsAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final SERVER_GROUP_NAME = "spinnaker-test-v000"
  private static final REGION = "us-central1"
  private static final ZONE = "us-central1-b"
  private static final TAGS = ["some-tag-1", "some-tag-2", "some-tag-3"]
  private static final ORIG_INSTANCE_TEMPLATE_NAME = "$SERVER_GROUP_NAME-123"
  private static final ORIG_INSTANCE_TEMPLATE_URL =
      "https://compute.googleapis.com/compute/v1/projects/$PROJECT_NAME/global/instanceTemplates/$ORIG_INSTANCE_TEMPLATE_NAME"
  private static final NEW_INSTANCE_TEMPLATE_NAME = "new-instance-template"
  private static final INSTANCE_TEMPLATE_INSERTION_OP_NAME = "instance-template-insertion-op"
  private static final SET_INSTANCE_TEMPLATE_OP_NAME = "set-instance-template-op"
  private static final INSTANCE_1_NAME = "instance-1"
  private static final INSTANCE_2_NAME = "instance-2"
  private static final INSTANCE_1_URL =
      "https://compute.googleapis.com/compute/v1/projects/$PROJECT_NAME/zones/$ZONE/instances/$INSTANCE_1_NAME"
  private static final INSTANCE_2_URL =
      "https://compute.googleapis.com/compute/v1/projects/$PROJECT_NAME/zones/$ZONE/instances/$INSTANCE_2_NAME"
  private static final DONE = "DONE"
  private static final INSTANCES_SET_TAGS_1_OP_NAME = "instances-set-tags-1-op"
  private static final INSTANCES_SET_TAGS_2_OP_NAME = "instances-set-tags-2-op"

  @Shared
  def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)
  @Shared
  def registry = new DefaultRegistry()
  @Shared
  SafeRetry safeRetry

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
    safeRetry = new SafeRetry(maxRetries: 10, maxWaitInterval: 60000, retryIntervalBase: 0, jitterMultiplier: 0)
  }

  void "should set tags on new instance template and on instances"() {
    setup:
      def googleClusterProviderMock = Mock(GoogleClusterProvider)
      def serverGroup = new GoogleServerGroup(zone: ZONE).view
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
      def computeZonalOperations = Mock(Compute.ZoneOperations)
      def instanceGroupManagersMock = Mock(Compute.InstanceGroupManagers)
      def instanceGroupManagersGetMock = Mock(Compute.InstanceGroupManagers.Get)
      def instanceGroupManagerReal = new InstanceGroupManager(instanceTemplate: ORIG_INSTANCE_TEMPLATE_URL,
                                                              instanceGroup: SERVER_GROUP_NAME)
      def setInstanceTemplateMock = Mock(Compute.InstanceGroupManagers.SetInstanceTemplate)
      def setInstanceTemplateOperationReal = new Operation(targetLink: SERVER_GROUP_NAME,
                                                           name: SET_INSTANCE_TEMPLATE_OP_NAME,
                                                           status: DONE)
      def setInstanceTemplateOperationGetMock = Mock(Compute.ZoneOperations.Get)
      def instanceGroupsMock = Mock(Compute.InstanceGroups)
      def instanceGroupsListInstancesMock = Mock(Compute.InstanceGroups.ListInstances)
      def instanceWithNamedPorts1Real = new InstanceWithNamedPorts(instance: INSTANCE_1_URL)
      def instanceWithNamedPorts2Real = new InstanceWithNamedPorts(instance: INSTANCE_2_URL)
      def listResourceResponseItemsReal = [instanceWithNamedPorts1Real, instanceWithNamedPorts2Real]
      def instanceGroupsListInstancesReal = new InstanceGroupsListInstances(items: listResourceResponseItemsReal)
      def instancesMock = Mock(Compute.Instances)
      def instancesGet1Mock = Mock(Compute.Instances.Get)
      def instance1Real = new Instance(tags: new Tags())
      def instancesSetTags1Mock = Mock(Compute.Instances.SetTags)
      def instancesSetTagsOperation1Real = new Operation(targetLink: INSTANCE_1_URL,
                                                         name: INSTANCES_SET_TAGS_1_OP_NAME,
                                                         zone: ZONE,
                                                         status: DONE)
      def instancesGet2Mock = Mock(Compute.Instances.Get)
      def instance2Real = new Instance(tags: new Tags())
      def instancesSetTags2Mock = Mock(Compute.Instances.SetTags)
      def instancesSetTagsOperation2Real = new Operation(targetLink: INSTANCE_2_URL,
                                                         name: INSTANCES_SET_TAGS_2_OP_NAME,
                                                         zone: ZONE,
                                                         status: DONE)
      def instancesSetTagsOperation1GetMock = Mock(Compute.ZoneOperations.Get)
      def instancesSetTagsOperation2GetMock = Mock(Compute.ZoneOperations.Get)
      def instanceTemplatesDeleteMock = Mock(Compute.InstanceTemplates.Delete)
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new UpsertGoogleServerGroupTagsDescription(serverGroupName: SERVER_GROUP_NAME,
                                                                   region: REGION,
                                                                   tags: TAGS,
                                                                   accountName: ACCOUNT_NAME,
                                                                   credentials: credentials)
      @Subject def operation = new UpsertGoogleServerGroupTagsAtomicOperation(description)
      operation.registry = registry
      operation.googleOperationPoller =
          new GoogleOperationPoller(
            googleConfigurationProperties: new GoogleConfigurationProperties(),
            threadSleeper: threadSleeperMock,
            registry: registry,
            safeRetry: safeRetry
          )
      operation.googleClusterProvider = googleClusterProviderMock

    when:
      operation.operate([])

    then:
      1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

      // Query the managed instance group and its instance template.
      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, SERVER_GROUP_NAME) >> instanceGroupManagersGetMock
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
      1 * instanceGroupManagersMock.setInstanceTemplate(PROJECT_NAME, ZONE, SERVER_GROUP_NAME, {
        // Verify that the target link of the instance creation operation is used to set the new instance template.
        it.instanceTemplate == NEW_INSTANCE_TEMPLATE_NAME
      }) >> setInstanceTemplateMock
      1 * setInstanceTemplateMock.execute() >> setInstanceTemplateOperationReal
      1 * computeMock.zoneOperations() >> computeZonalOperations
      1 * computeZonalOperations.get(PROJECT_NAME, ZONE, SET_INSTANCE_TEMPLATE_OP_NAME) >> setInstanceTemplateOperationGetMock
      1 * setInstanceTemplateOperationGetMock.execute() >> setInstanceTemplateOperationReal

      // Query the instance group's instances.
      1 * computeMock.instanceGroups() >> instanceGroupsMock
      1 * instanceGroupsMock.listInstances(PROJECT_NAME, ZONE, SERVER_GROUP_NAME, _) >> instanceGroupsListInstancesMock
      1 * instanceGroupsListInstancesMock.execute() >> instanceGroupsListInstancesReal

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
      def googleClusterProviderMock = Mock(GoogleClusterProvider)
      def serverGroup = new GoogleServerGroup(zone: ZONE).view
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
      def computeZonalOperations = Mock(Compute.ZoneOperations)
      def instanceGroupManagersMock = Mock(Compute.InstanceGroupManagers)
      def instanceGroupManagersGetMock = Mock(Compute.InstanceGroupManagers.Get)
      def instanceGroupManagerReal = new InstanceGroupManager(instanceTemplate: ORIG_INSTANCE_TEMPLATE_URL,
                                                              instanceGroup: SERVER_GROUP_NAME)
      def setInstanceTemplateMock = Mock(Compute.InstanceGroupManagers.SetInstanceTemplate)
      def setInstanceTemplateOperationReal = new Operation(targetLink: SERVER_GROUP_NAME,
                                                           name: SET_INSTANCE_TEMPLATE_OP_NAME,
                                                           status: DONE)
      def setInstanceTemplateOperationGetMock = Mock(Compute.ZoneOperations.Get)
      def instanceGroupsMock = Mock(Compute.InstanceGroups)
      def instanceGroupsListInstancesMock = Mock(Compute.InstanceGroups.ListInstances)
      def instanceGroupsListInstancesReal = new InstanceGroupsListInstances()
      def instanceTemplatesDeleteMock = Mock(Compute.InstanceTemplates.Delete)
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new UpsertGoogleServerGroupTagsDescription(serverGroupName: SERVER_GROUP_NAME,
                                                                   region: REGION,
                                                                   tags: TAGS,
                                                                   accountName: ACCOUNT_NAME,
                                                                   credentials: credentials)
      @Subject def operation = new UpsertGoogleServerGroupTagsAtomicOperation(description)
      operation.registry = registry
      operation.googleOperationPoller =
          new GoogleOperationPoller(
            googleConfigurationProperties: new GoogleConfigurationProperties(),
            threadSleeper: threadSleeperMock,
            registry: registry,
            safeRetry: safeRetry
          )
      operation.googleClusterProvider = googleClusterProviderMock

    when:
      operation.operate([])

    then:
      1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

      // Query the managed instance group and its instance template.
      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, SERVER_GROUP_NAME) >> instanceGroupManagersGetMock
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
      1 * instanceGroupManagersMock.setInstanceTemplate(PROJECT_NAME, ZONE, SERVER_GROUP_NAME, {
        // Verify that the target link of the instance creation operation is used to set the new instance template.
        it.instanceTemplate == NEW_INSTANCE_TEMPLATE_NAME
      }) >> setInstanceTemplateMock
      1 * setInstanceTemplateMock.execute() >> setInstanceTemplateOperationReal
      1 * computeMock.zoneOperations() >> computeZonalOperations
      1 * computeZonalOperations.get(PROJECT_NAME, ZONE, SET_INSTANCE_TEMPLATE_OP_NAME) >> setInstanceTemplateOperationGetMock
      1 * setInstanceTemplateOperationGetMock.execute() >> setInstanceTemplateOperationReal

      // Query the instance group's instances.
      1 * computeMock.instanceGroups() >> instanceGroupsMock
      1 * instanceGroupsMock.listInstances(PROJECT_NAME, ZONE, SERVER_GROUP_NAME, _) >> instanceGroupsListInstancesMock
      1 * instanceGroupsListInstancesMock.execute() >> instanceGroupsListInstancesReal

      // Delete the original instance template.
      1 * instanceTemplatesMock.delete(PROJECT_NAME, ORIG_INSTANCE_TEMPLATE_NAME) >> instanceTemplatesDeleteMock
      1 * instanceTemplatesDeleteMock.execute()
  }
}
