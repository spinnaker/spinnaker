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
import com.google.api.services.compute.model.AttachedDisk
import com.google.api.services.compute.model.AttachedDiskInitializeParams
import com.google.api.services.compute.model.InstanceGroupManager
import com.google.api.services.compute.model.InstanceProperties
import com.google.api.services.compute.model.InstanceTemplate
import com.google.api.services.compute.model.Metadata
import com.google.api.services.compute.model.NetworkInterface
import com.google.api.services.compute.model.Operation
import com.google.api.services.compute.model.Tags
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.ModifyGoogleServerGroupInstanceTemplateDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

// After GCEUtil is refactored to be dependency-injected:
//  TODO(duftler): Add test to verify that referenced GCE resources are queried.
//  TODO(duftler): Add test to verify that instance template is not created if a referenced resource is invalid.
class ModifyGoogleServerGroupInstanceTemplateAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final SERVER_GROUP_NAME = "spinnaker-test-v000"
  private static final ZONE = "us-central1-b"
  private static final REGION = "us-central1"
  private static final REGION_URL = "https://compute.googleapis.com/compute/v1/projects/$PROJECT_NAME/regions/$REGION"

  private static final MACHINE_TYPE = "f1-micro"
  private static final NETWORK_1 = "projects/$PROJECT_NAME/networks/default"
  private static final NETWORK_2 = "projects/$PROJECT_NAME/networks/other-network"
  private static final IMAGE = "debian"
  private static final DISK_TYPE = "pd-standard"
  private static final DISK_SIZE_GB = 120
  private static final METADATA_1 = ["startup-script": "sudo apt-get update"]
  private static final METADATA_2 = ["some-key": "some-value"]
  private static final TAGS_1 = ["some-tag-1", "some-tag-2", "some-tag-3"]
  private static final TAGS_2 = ["some-tag-4", "some-tag-5"]
  private static final ORIG_INSTANCE_TEMPLATE_NAME = "$SERVER_GROUP_NAME-123"
  private static final ORIG_INSTANCE_TEMPLATE_URL =
      "https://compute.googleapis.com/compute/v1/projects/$PROJECT_NAME/global/instanceTemplates/$ORIG_INSTANCE_TEMPLATE_NAME"
  private static final NEW_INSTANCE_TEMPLATE_NAME = "new-instance-template"
  private static final INSTANCE_TEMPLATE_INSERTION_OP_NAME = "instance-template-insertion-op"
  private static final SET_INSTANCE_TEMPLATE_OP_NAME = "set-instance-template-op"
  private static final DONE = "DONE"

  @Shared def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)
  @Shared def registry = new DefaultRegistry()
  @Shared SafeRetry safeRetry

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
    safeRetry = new SafeRetry(maxRetries: 10, maxWaitInterval: 60000, retryIntervalBase: 0, jitterMultiplier: 0)
  }

  void "should not make any changes if no properties are overridden"() {
    setup:
      def googleClusterProviderMock = Mock(GoogleClusterProvider)
      def serverGroup = new GoogleServerGroup(zone: ZONE).view
      def computeMock = Mock(Compute)
      def instanceTemplatesMock = Mock(Compute.InstanceTemplates)
      def instanceTemplatesGetMock = Mock(Compute.InstanceTemplates.Get)
      def instanceTemplateReal = new InstanceTemplate(
        properties: new InstanceProperties(
          machineType: MACHINE_TYPE,
          networkInterfaces: [
            new NetworkInterface(network: NETWORK_1)
          ],
          disks: [
            new AttachedDisk(autoDelete: true,
                             initializeParams: new AttachedDiskInitializeParams(
              sourceImage: IMAGE,
              diskType: DISK_TYPE,
              diskSizeGb: DISK_SIZE_GB
            ))
          ],
          metadata: new Metadata(GCEUtil.buildMetadataFromMap(METADATA_1)),
          tags: new Tags(items: TAGS_1)
        )
      )
      def instanceGroupManagersMock = Mock(Compute.InstanceGroupManagers)
      def instanceGroupManagersGetMock = Mock(Compute.InstanceGroupManagers.Get)
      def instanceGroupManagerReal = new InstanceGroupManager(instanceTemplate: ORIG_INSTANCE_TEMPLATE_URL, group: SERVER_GROUP_NAME)
      def credentials = new GoogleNamedAccountCredentials.Builder().name("gce").project(PROJECT_NAME).compute(computeMock).build()
      def description = new ModifyGoogleServerGroupInstanceTemplateDescription(serverGroupName: SERVER_GROUP_NAME,
                                                                               region: REGION,
                                                                               accountName: ACCOUNT_NAME,
                                                                               credentials: credentials)
      @Subject def operation = new ModifyGoogleServerGroupInstanceTemplateAtomicOperation(description)
      operation.googleOperationPoller =
          new GoogleOperationPoller(
            googleConfigurationProperties: new GoogleConfigurationProperties(),
            threadSleeper: threadSleeperMock,
            safeRetry: safeRetry
          )
      operation.googleClusterProvider = googleClusterProviderMock
      operation.registry = registry

    when:
      operation.operate([])

    then:
      1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

    then:
      // Query the managed instance group and its instance template.
      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, SERVER_GROUP_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManagerReal
      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.get(PROJECT_NAME, ORIG_INSTANCE_TEMPLATE_NAME) >> instanceTemplatesGetMock
      1 * instanceTemplatesGetMock.execute() >> instanceTemplateReal
  }

  void "should set metadata and tags on new instance template"() {
    setup:
      def googleClusterProviderMock = Mock(GoogleClusterProvider)
      def serverGroup = new GoogleServerGroup(zone: ZONE).view
      def computeMock = Mock(Compute)
      def globalOperations = Mock(Compute.GlobalOperations)
      def instanceTemplatesMock = Mock(Compute.InstanceTemplates)
      def instanceTemplatesGetMock = Mock(Compute.InstanceTemplates.Get)
      def instanceTemplateReal = new InstanceTemplate(
        properties: new InstanceProperties(
          machineType: MACHINE_TYPE,
          networkInterfaces: [
            new NetworkInterface(network: NETWORK_1)
          ],
          disks: [
            new AttachedDisk(autoDelete: true,
                             initializeParams: new AttachedDiskInitializeParams(
              sourceImage: IMAGE,
              diskType: DISK_TYPE,
              diskSizeGb: DISK_SIZE_GB
            ))
          ],
          metadata: new Metadata(GCEUtil.buildMetadataFromMap(METADATA_1)),
          tags: new Tags(items: TAGS_1)
        )
      )
      def instanceTemplatesInsertMock = Mock(Compute.InstanceTemplates.Insert)
      def instanceTemplateInsertionOperationGetMock = Mock(Compute.GlobalOperations.Get)
      def instanceTemplateInsertionOperationReal = new Operation(targetLink: NEW_INSTANCE_TEMPLATE_NAME,
                                                                 name: INSTANCE_TEMPLATE_INSERTION_OP_NAME,
                                                                 status: DONE)
      def instanceGroupManagersMock = Mock(Compute.InstanceGroupManagers)
      def instanceGroupManagersGetMock = Mock(Compute.InstanceGroupManagers.Get)
      def computeZonalOperations = Mock(Compute.ZoneOperations)
      def instanceGroupManagerReal = new InstanceGroupManager(instanceTemplate: ORIG_INSTANCE_TEMPLATE_URL, group: SERVER_GROUP_NAME)
      def setInstanceTemplateMock = Mock(Compute.InstanceGroupManagers.SetInstanceTemplate)
      def setInstanceTemplateOperationReal = new Operation(targetLink: SERVER_GROUP_NAME,
                                                           name: SET_INSTANCE_TEMPLATE_OP_NAME,
                                                           status: DONE)
      def setInstanceTemplateOperationGetMock = Mock(Compute.ZoneOperations.Get)
      def instanceTemplatesDeleteMock = Mock(Compute.InstanceTemplates.Delete)
      def credentials = new GoogleNamedAccountCredentials.Builder().name("gce").project(PROJECT_NAME).compute(computeMock).build()
      def description = new ModifyGoogleServerGroupInstanceTemplateDescription(serverGroupName: SERVER_GROUP_NAME,
                                                                               region: REGION,
                                                                               instanceMetadata: METADATA_2,
                                                                               tags: TAGS_2,
                                                                               accountName: ACCOUNT_NAME,
                                                                               credentials: credentials)
      @Subject def operation = new ModifyGoogleServerGroupInstanceTemplateAtomicOperation(description)
      operation.googleOperationPoller =
          new GoogleOperationPoller(
            googleConfigurationProperties: new GoogleConfigurationProperties(),
            threadSleeper: threadSleeperMock,
            registry: new DefaultRegistry(),
            safeRetry: safeRetry
          )
      operation.googleClusterProvider = googleClusterProviderMock
      operation.registry = registry

    when:
      operation.operate([])

    then:
      1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

    then:
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
        (it.name != ORIG_INSTANCE_TEMPLATE_NAME
          && GCEUtil.buildMapFromMetadata(it.properties.metadata) == METADATA_2
          && it.properties.tags.items == TAGS_2)
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

      // Delete the original instance template.
      1 * instanceTemplatesMock.delete(PROJECT_NAME, ORIG_INSTANCE_TEMPLATE_NAME) >> instanceTemplatesDeleteMock
      1 * instanceTemplatesDeleteMock.execute()
  }

  void "should throw exception if no original instance template properties can be resolved"() {
    setup:
      def googleClusterProviderMock = Mock(GoogleClusterProvider)
      def serverGroup = new GoogleServerGroup(zone: ZONE).view
      def computeMock = Mock(Compute)
      def instanceTemplatesMock = Mock(Compute.InstanceTemplates)
      def instanceTemplatesGetMock = Mock(Compute.InstanceTemplates.Get)
      def instanceTemplateReal = new InstanceTemplate(name: ORIG_INSTANCE_TEMPLATE_NAME)
      def instanceGroupManagersMock = Mock(Compute.InstanceGroupManagers)
      def instanceGroupManagersGetMock = Mock(Compute.InstanceGroupManagers.Get)
      def instanceGroupManagerReal = new InstanceGroupManager(instanceTemplate: ORIG_INSTANCE_TEMPLATE_URL, group: SERVER_GROUP_NAME)
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new ModifyGoogleServerGroupInstanceTemplateDescription(serverGroupName: SERVER_GROUP_NAME,
                                                                               region: REGION,
                                                                               accountName: ACCOUNT_NAME,
                                                                               credentials: credentials)
      @Subject def operation = new ModifyGoogleServerGroupInstanceTemplateAtomicOperation(description)
      operation.googleOperationPoller =
          new GoogleOperationPoller(
            googleConfigurationProperties: new GoogleConfigurationProperties(),
            threadSleeper: threadSleeperMock,
            safeRetry: safeRetry
          )
      operation.googleClusterProvider = googleClusterProviderMock
      operation.registry = registry

    when:
      operation.operate([])

    then:
      1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

    then:
      // Query the managed instance group and its instance template.
      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, SERVER_GROUP_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManagerReal
      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.get(PROJECT_NAME, ORIG_INSTANCE_TEMPLATE_NAME) >> instanceTemplatesGetMock
      1 * instanceTemplatesGetMock.execute() >> instanceTemplateReal

      def exc = thrown GoogleOperationException
      exc.message == "Unable to determine properties of instance template $ORIG_INSTANCE_TEMPLATE_NAME."
  }

  @Unroll
  void "should throw exception if the original instance template defines a number of network interfaces other than one"() {
    setup:
      def googleClusterProviderMock = Mock(GoogleClusterProvider)
      def serverGroup = new GoogleServerGroup(zone: ZONE).view
      def computeMock = Mock(Compute)
      def instanceTemplatesMock = Mock(Compute.InstanceTemplates)
      def instanceTemplatesGetMock = Mock(Compute.InstanceTemplates.Get)
      def instanceTemplateReal = new InstanceTemplate(
        name: ORIG_INSTANCE_TEMPLATE_NAME,
        properties: new InstanceProperties(
          machineType: MACHINE_TYPE,
          networkInterfaces: networkInterfaces,
          disks: [
            new AttachedDisk(autoDelete: true,
                             initializeParams: new AttachedDiskInitializeParams(
              sourceImage: IMAGE,
              diskType: DISK_TYPE,
              diskSizeGb: DISK_SIZE_GB
            ))
          ],
          metadata: new Metadata(GCEUtil.buildMetadataFromMap(METADATA_1)),
          tags: new Tags(items: TAGS_1)
        )
      )
      def instanceGroupManagersMock = Mock(Compute.InstanceGroupManagers)
      def instanceGroupManagersGetMock = Mock(Compute.InstanceGroupManagers.Get)
      def instanceGroupManagerReal = new InstanceGroupManager(instanceTemplate: ORIG_INSTANCE_TEMPLATE_URL, group: SERVER_GROUP_NAME)
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new ModifyGoogleServerGroupInstanceTemplateDescription(serverGroupName: SERVER_GROUP_NAME,
                                                                               region: REGION,
                                                                               accountName: ACCOUNT_NAME,
                                                                               credentials: credentials)
      @Subject def operation = new ModifyGoogleServerGroupInstanceTemplateAtomicOperation(description)
      operation.googleOperationPoller =
          new GoogleOperationPoller(
            googleConfigurationProperties: new GoogleConfigurationProperties(),
            threadSleeper: threadSleeperMock,
            safeRetry: safeRetry
          )
      operation.googleClusterProvider = googleClusterProviderMock
      operation.registry = registry

    when:
      operation.operate([])

    then:
      1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

    then:
      // Query the managed instance group and its instance template.
      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, SERVER_GROUP_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManagerReal
      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.get(PROJECT_NAME, ORIG_INSTANCE_TEMPLATE_NAME) >> instanceTemplatesGetMock
      1 * instanceTemplatesGetMock.execute() >> instanceTemplateReal

      def exc = thrown GoogleOperationException
      exc.message == "Instance templates must have exactly one network interface defined. " +
                     "Instance template $ORIG_INSTANCE_TEMPLATE_NAME has $exceptionMsgSizeDescriptor."

    where:
      networkInterfaces                                                                    | exceptionMsgSizeDescriptor
      null                                                                                 | null
      []                                                                                   | 0
      [new NetworkInterface(network: NETWORK_1), new NetworkInterface(network: NETWORK_2)] | 2
  }

  @Unroll
  void "should throw exception if the original instance template does not define any disks"() {
    setup:
      def googleClusterProviderMock = Mock(GoogleClusterProvider)
      def serverGroup = new GoogleServerGroup(zone: ZONE).view
      def computeMock = Mock(Compute)
      def instanceTemplatesMock = Mock(Compute.InstanceTemplates)
      def instanceTemplatesGetMock = Mock(Compute.InstanceTemplates.Get)
      def instanceTemplateReal = new InstanceTemplate(
        name: ORIG_INSTANCE_TEMPLATE_NAME,
        properties: new InstanceProperties(
          machineType: MACHINE_TYPE,
          networkInterfaces: [
            new NetworkInterface(network: NETWORK_1)
          ],
          disks: disks,
          metadata: new Metadata(GCEUtil.buildMetadataFromMap(METADATA_1)),
          tags: new Tags(items: TAGS_1)
        )
      )
      def instanceGroupManagersMock = Mock(Compute.InstanceGroupManagers)
      def instanceGroupManagersGetMock = Mock(Compute.InstanceGroupManagers.Get)
      def instanceGroupManagerReal = new InstanceGroupManager(instanceTemplate: ORIG_INSTANCE_TEMPLATE_URL, group: SERVER_GROUP_NAME)
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new ModifyGoogleServerGroupInstanceTemplateDescription(serverGroupName: SERVER_GROUP_NAME,
                                                                               region: REGION,
                                                                               accountName: ACCOUNT_NAME,
                                                                               credentials: credentials)
      @Subject def operation = new ModifyGoogleServerGroupInstanceTemplateAtomicOperation(description)
      operation.googleOperationPoller =
          new GoogleOperationPoller(
            googleConfigurationProperties: new GoogleConfigurationProperties(),
            threadSleeper: threadSleeperMock,
            safeRetry: safeRetry
          )
      operation.googleClusterProvider = googleClusterProviderMock
      operation.registry = registry

    when:
      operation.operate([])

    then:
      1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

    then:
      // Query the managed instance group and its instance template.
      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, SERVER_GROUP_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManagerReal
      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.get(PROJECT_NAME, ORIG_INSTANCE_TEMPLATE_NAME) >> instanceTemplatesGetMock
      1 * instanceTemplatesGetMock.execute() >> instanceTemplateReal

      def exc = thrown GoogleOperationException
      exc.message == "Instance templates must have at least one disk defined. " +
                     "Instance template $ORIG_INSTANCE_TEMPLATE_NAME has $exceptionMsgSizeDescriptor."

    where:
      disks                                    | exceptionMsgSizeDescriptor
      null                                     | null
      []                                       | 0
  }
}
