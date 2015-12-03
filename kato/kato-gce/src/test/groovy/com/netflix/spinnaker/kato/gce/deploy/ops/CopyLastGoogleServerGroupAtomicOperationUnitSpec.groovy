/*
 * Copyright 2014 Google, Inc.
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
import com.google.api.services.compute.model.Image
import com.google.api.services.compute.model.InstanceGroupManager
import com.google.api.services.compute.model.InstanceProperties
import com.google.api.services.compute.model.InstanceTemplate
import com.google.api.services.compute.model.Network
import com.google.api.services.compute.model.Scheduling
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import com.netflix.spinnaker.kato.config.GceConfig
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.DeploymentResult
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.description.BaseGoogleInstanceDescription
import com.netflix.spinnaker.kato.gce.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.kato.gce.deploy.handlers.BasicGoogleDeployHandler
import com.netflix.spinnaker.kato.gce.model.GoogleDisk
import com.netflix.spinnaker.mort.gce.model.GoogleSecurityGroup
import com.netflix.spinnaker.mort.gce.provider.view.GoogleSecurityGroupProvider
import spock.lang.Specification
import spock.lang.Subject

class CopyLastGoogleServerGroupAtomicOperationUnitSpec extends Specification {
  private static final String ACCOUNT_NAME = "auto"
  private static final String PROJECT_NAME = "my_project"
  private static final String APPLICATION_NAME = "myapp"
  private static final String STACK_NAME = "dev"
  private static final String ANCESTOR_SERVER_GROUP_NAME = "$APPLICATION_NAME-$STACK_NAME-v000"
  private static final String NEW_SERVER_GROUP_NAME = "$APPLICATION_NAME-$STACK_NAME-v001"
  private static final String IMAGE = "debian-7-wheezy-v20141108"
  private static final String INSTANCE_TYPE = "f1-micro"
  private static final String INSTANCE_TEMPLATE_NAME = "myapp-dev-v000-${System.currentTimeMillis()}"
  private static final String REGION = "us-central1"
  private static final Map<String, String> INSTANCE_METADATA =
          ["startup-script": "apt-get update && apt-get install -y apache2 && hostname > /var/www/index.html",
           "testKey": "testValue"]
  private static final String HTTP_SERVER_TAG = "http-server"
  private static final String HTTPS_SERVER_TAG = "https-server"
  private static final List<String> TAGS = ["orig-tag-1", "orig-tag-2", HTTP_SERVER_TAG, HTTPS_SERVER_TAG]
  private static final List<String> AUTH_SCOPES = ["compute", "logging.write"]
  private static final List<String> LOAD_BALANCERS = ["testlb-east-1", "testlb-east-2"]
  private static final String SECURITY_GROUP_1 = "sg-1"
  private static final String SECURITY_GROUP_2 = "sg-2"
  private static final Set<String> SECURITY_GROUPS = [SECURITY_GROUP_1, SECURITY_GROUP_2]
  private static final String ZONE = "us-central1-b"

  private static final long DISK_SIZE_GB = 100
  private static final String DISK_TYPE = "pd-standard"
  private static final GoogleDisk DISK_PD_STANDARD = new GoogleDisk(type: DISK_TYPE, sizeGb: DISK_SIZE_GB)
  private static final String DEFAULT_NETWORK_NAME = "default"
  private static final String ACCESS_CONFIG_NAME = "External NAT"
  private static final String ACCESS_CONFIG_TYPE = "ONE_TO_ONE_NAT"

  private def computeMock
  private def credentials
  private def instanceGroupManagersMock
  private def instanceGroupManagersGetMock
  private def instanceGroupManagersDeleteMock
  private def instanceTemplatesMock
  private def instanceTemplatesGetMock

  private def sourceImage
  private def network
  private def attachedDisks
  private def networkInterface
  private def instanceMetadata
  private def tags
  private def scheduling
  private def serviceAccount
  private def instanceProperties
  private def instanceTemplate
  private def instanceGroupManager

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    computeMock = Mock(Compute)
    credentials = new GoogleCredentials(PROJECT_NAME, computeMock)

    instanceGroupManagersMock = Mock(Compute.InstanceGroupManagers)
    instanceGroupManagersGetMock = Mock(Compute.InstanceGroupManagers.Get)
    instanceGroupManagersDeleteMock = Mock(Compute.InstanceGroupManagers.Delete)
    instanceTemplatesMock = Mock(Compute.InstanceTemplates)
    instanceTemplatesGetMock = Mock(Compute.InstanceTemplates.Get)

    sourceImage = new Image(selfLink: IMAGE)
    network = new Network(selfLink: DEFAULT_NETWORK_NAME)
    attachedDisks = GCEUtil.buildAttachedDisks(PROJECT_NAME,
                                               ZONE,
                                               sourceImage,
                                               [DISK_PD_STANDARD],
                                               false,
                                               INSTANCE_TYPE,
                                               new GceConfig.DeployDefaults())
    networkInterface = GCEUtil.buildNetworkInterface(network, ACCESS_CONFIG_NAME, ACCESS_CONFIG_TYPE)
    instanceMetadata = GCEUtil.buildMetadataFromMap(INSTANCE_METADATA)
    tags = GCEUtil.buildTagsFromList(TAGS)
    scheduling = new Scheduling(preemptible: false,
                                automaticRestart: true,
                                onHostMaintenance: "MIGRATE")
    serviceAccount = GCEUtil.buildServiceAccount(AUTH_SCOPES)
    instanceProperties = new InstanceProperties(machineType: INSTANCE_TYPE,
                                                disks: attachedDisks,
                                                networkInterfaces: [networkInterface],
                                                metadata: instanceMetadata,
                                                tags: tags,
                                                scheduling: scheduling,
                                                serviceAccounts: [serviceAccount])
    instanceTemplate = new InstanceTemplate(name: INSTANCE_TEMPLATE_NAME,
                                            properties: instanceProperties)
    instanceGroupManager = new InstanceGroupManager(name: ANCESTOR_SERVER_GROUP_NAME,
                                                    instanceTemplate: INSTANCE_TEMPLATE_NAME,
                                                    targetSize: 2,
                                                    targetPools: LOAD_BALANCERS)
  }

  void "operation builds description based on ancestor server group; overrides everything"() {
    setup:
      def description = new BasicGoogleDeployDescription(application: APPLICATION_NAME,
                                                         stack: STACK_NAME,
                                                         targetSize: 4,
                                                         image: "backports-$IMAGE",
                                                         instanceType: "n1-standard-8",
                                                         disks: [new GoogleDisk(type: "pd-ssd", sizeGb: 250)],
                                                         zone: ZONE,
                                                         instanceMetadata: ["differentKey": "differentValue"],
                                                         tags: ["new-tag-1", "new-tag-2"],
                                                         preemptible: true,
                                                         automaticRestart: false,
                                                         onHostMaintenance: BaseGoogleInstanceDescription.OnHostMaintenance.TERMINATE,
                                                         authScopes: ["some-scope", "some-other-scope"],
                                                         network: "other-network",
                                                         loadBalancers: ["testlb-west-1", "testlb-west-2"],
                                                         securityGroups: ["sg-3", "sg-4"] as Set,
                                                         source: [zone: ZONE,
                                                                  serverGroupName: ANCESTOR_SERVER_GROUP_NAME],
                                                         accountName: ACCOUNT_NAME,
                                                         credentials: credentials)
      def googleSecurityGroupProviderMock = Mock(GoogleSecurityGroupProvider)
      def basicGoogleDeployHandlerMock = Mock(BasicGoogleDeployHandler)
      def newDescription = description.clone()
      def deploymentResult = new DeploymentResult(serverGroupNames: ["$REGION:$NEW_SERVER_GROUP_NAME"])
      @Subject def operation = new CopyLastGoogleServerGroupAtomicOperation(description)
      operation.googleSecurityGroupProvider = googleSecurityGroupProviderMock
      operation.basicGoogleDeployHandler = basicGoogleDeployHandlerMock

    when:
      operation.operate([])

    then:
      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, ANCESTOR_SERVER_GROUP_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManager

      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.get(PROJECT_NAME, INSTANCE_TEMPLATE_NAME) >> instanceTemplatesGetMock
      1 * instanceTemplatesGetMock.execute() >> instanceTemplate
      1 * googleSecurityGroupProviderMock.getAllByAccount(false, ACCOUNT_NAME) >> []
      1 * basicGoogleDeployHandlerMock.handle(newDescription, _) >> deploymentResult
  }

  void "operation builds description based on ancestor server group; overrides nothing"() {
    setup:
      def description = new BasicGoogleDeployDescription(source: [zone: ZONE,
                                                                  serverGroupName: ANCESTOR_SERVER_GROUP_NAME],
                                                         accountName: ACCOUNT_NAME,
                                                         credentials: credentials)
      def googleSecurityGroupProviderMock = Mock(GoogleSecurityGroupProvider)
      def basicGoogleDeployHandlerMock = Mock(BasicGoogleDeployHandler)
      def newDescription = description.clone()
      newDescription.application = APPLICATION_NAME
      newDescription.stack = STACK_NAME
      newDescription.targetSize = 2
      newDescription.image = IMAGE
      newDescription.instanceType = INSTANCE_TYPE
      newDescription.disks = [DISK_PD_STANDARD]
      newDescription.zone = ZONE
      newDescription.instanceMetadata = INSTANCE_METADATA
      newDescription.tags = TAGS
      newDescription.preemptible = false
      newDescription.automaticRestart = true
      newDescription.onHostMaintenance = BaseGoogleInstanceDescription.OnHostMaintenance.MIGRATE
      newDescription.authScopes = AUTH_SCOPES
      newDescription.network = DEFAULT_NETWORK_NAME
      newDescription.loadBalancers = LOAD_BALANCERS
      newDescription.securityGroups = SECURITY_GROUPS
      def deploymentResult = new DeploymentResult(serverGroupNames: ["$REGION:$NEW_SERVER_GROUP_NAME"])
      @Subject def operation = new CopyLastGoogleServerGroupAtomicOperation(description)
      operation.googleSecurityGroupProvider = googleSecurityGroupProviderMock
      operation.basicGoogleDeployHandler = basicGoogleDeployHandlerMock

    when:
      operation.operate([])

    then:
      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, ANCESTOR_SERVER_GROUP_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManager

      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.get(PROJECT_NAME, INSTANCE_TEMPLATE_NAME) >> instanceTemplatesGetMock
      1 * instanceTemplatesGetMock.execute() >> instanceTemplate
      1 * googleSecurityGroupProviderMock.getAllByAccount(false, ACCOUNT_NAME) >> [
        new GoogleSecurityGroup(name: SECURITY_GROUP_1, targetTags: [HTTP_SERVER_TAG]),
        new GoogleSecurityGroup(name: SECURITY_GROUP_2, targetTags: [HTTPS_SERVER_TAG])
      ]
      1 * basicGoogleDeployHandlerMock.handle(newDescription, _) >> deploymentResult
  }

  void "description specifies subset of security groups, and subset of tags is properly calculated"() {
    setup:
      def description = new BasicGoogleDeployDescription(source: [zone: ZONE,
                                                                  serverGroupName: ANCESTOR_SERVER_GROUP_NAME],
                                                         securityGroups: [SECURITY_GROUP_2],
                                                         accountName: ACCOUNT_NAME,
                                                         credentials: credentials)
      def googleSecurityGroupProviderMock = Mock(GoogleSecurityGroupProvider)
      def basicGoogleDeployHandlerMock = Mock(BasicGoogleDeployHandler)
      def deploymentResult = new DeploymentResult(serverGroupNames: ["$REGION:$NEW_SERVER_GROUP_NAME"])
      @Subject def operation = new CopyLastGoogleServerGroupAtomicOperation(description)
      operation.googleSecurityGroupProvider = googleSecurityGroupProviderMock
      operation.basicGoogleDeployHandler = basicGoogleDeployHandlerMock

    when:
      operation.operate([])

    then:
      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, ANCESTOR_SERVER_GROUP_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManager

      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.get(PROJECT_NAME, INSTANCE_TEMPLATE_NAME) >> instanceTemplatesGetMock
      1 * instanceTemplatesGetMock.execute() >> instanceTemplate
      2 * googleSecurityGroupProviderMock.getAllByAccount(false, ACCOUNT_NAME) >> [
        new GoogleSecurityGroup(name: SECURITY_GROUP_1, targetTags: [HTTP_SERVER_TAG]),
        new GoogleSecurityGroup(name: SECURITY_GROUP_2, targetTags: [HTTPS_SERVER_TAG])
      ]
      1 * basicGoogleDeployHandlerMock.handle(_, _) >> {
        it[0].tags == TAGS - HTTP_SERVER_TAG

        deploymentResult
      }
  }

  void "description specifies empty list of security groups, and subset of tags is properly calculated"() {
    setup:
      def description = new BasicGoogleDeployDescription(source: [zone: ZONE,
                                                                  serverGroupName: ANCESTOR_SERVER_GROUP_NAME],
                                                         securityGroups: [],
                                                         accountName: ACCOUNT_NAME,
                                                         credentials: credentials)
      def googleSecurityGroupProviderMock = Mock(GoogleSecurityGroupProvider)
      def basicGoogleDeployHandlerMock = Mock(BasicGoogleDeployHandler)
      def deploymentResult = new DeploymentResult(serverGroupNames: ["$REGION:$NEW_SERVER_GROUP_NAME"])
      @Subject def operation = new CopyLastGoogleServerGroupAtomicOperation(description)
      operation.googleSecurityGroupProvider = googleSecurityGroupProviderMock
      operation.basicGoogleDeployHandler = basicGoogleDeployHandlerMock

    when:
      operation.operate([])

    then:
      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, ANCESTOR_SERVER_GROUP_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManager

      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.get(PROJECT_NAME, INSTANCE_TEMPLATE_NAME) >> instanceTemplatesGetMock
      1 * instanceTemplatesGetMock.execute() >> instanceTemplate
      2 * googleSecurityGroupProviderMock.getAllByAccount(false, ACCOUNT_NAME) >> [
        new GoogleSecurityGroup(name: SECURITY_GROUP_1, targetTags: [HTTP_SERVER_TAG]),
        new GoogleSecurityGroup(name: SECURITY_GROUP_2, targetTags: [HTTPS_SERVER_TAG])
      ]
      1 * basicGoogleDeployHandlerMock.handle(_, _) >> {
        it[0].tags == TAGS - HTTP_SERVER_TAG - HTTPS_SERVER_TAG

        deploymentResult
      }
  }
}
