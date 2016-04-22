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

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.ForwardingRuleList
import com.google.api.services.compute.model.InstanceGroupManager
import com.google.api.services.compute.model.InstanceGroupsListInstances
import com.google.api.services.compute.model.InstanceProperties
import com.google.api.services.compute.model.InstanceTemplate
import com.google.api.services.compute.model.InstanceWithNamedPorts
import com.google.api.services.compute.model.Zone
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.description.EnableDisableGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleResourceNotFoundException
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import spock.lang.Specification
import spock.lang.Subject

class EnableGoogleServerGroupAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final SERVER_GROUP_NAME = "mjdapp-dev-v009"
  private static final INSTANCE_TEMPLATE_NAME = "$SERVER_GROUP_NAME-${System.currentTimeMillis()}"
  private static final FORWARDING_RULE_1 = "testlb";
  private static final FORWARDING_RULE_2 = "testlb2";
  private static final INSTANCE_METADATA =
          ["startup-script": "apt-get update && apt-get install -y apache2 && hostname > /var/www/index.html",
           "testKey": "testValue",
           "load-balancer-names": "$FORWARDING_RULE_1,$FORWARDING_RULE_2"]
  private static final TARGET_POOL_NAME_1 = "testlb-target-pool-1417967954401";
  private static final TARGET_POOL_NAME_2 = "testlb2-target-pool-1417963107058";
  private static final TARGET_POOL_URL_1 =
          "https://www.googleapis.com/compute/v1/projects/shared-spinnaker/regions/us-central1/targetPools/$TARGET_POOL_NAME_1"
  private static final TARGET_POOL_URL_2 =
          "https://www.googleapis.com/compute/v1/projects/shared-spinnaker/regions/us-central1/targetPools/$TARGET_POOL_NAME_2"
  private static final TARGET_POOL_URLS = [TARGET_POOL_URL_1, TARGET_POOL_URL_2]
  private static final INSTANCE_URL_1 =
          "https://www.googleapis.com/compute/v1/projects/shared-spinnaker/zones/us-central1-a/instances/mjdapp-dev-v009-hnyp"
  private static final INSTANCE_URL_2 =
          "https://www.googleapis.com/compute/v1/projects/shared-spinnaker/zones/us-central1-a/instances/mjdapp-dev-v009-qtow"
  private static final ZONE = "us-central1-b"
  private static final REGION = "us-central1"

  def googleClusterProviderMock
  def serverGroup
  def computeMock
  def instanceGroupsMock
  def instanceGroupsListInstancesMock
  def instanceGroupManagersMock
  def instanceGroupManagersGetMock
  def instanceTemplatesMock
  def instanceTemplatesGetMock
  def forwardingRulesMock
  def forwardingRulesListMock
  def targetPoolsMock
  def targetPoolsAddInstanceMock
  def instanceGroupManagersSetTargetPoolsMock

  def instanceGroupManager
  def instanceGroupsListInstances
  def instanceMetadata
  def instanceProperties
  def instanceTemplate
  def items
  def forwardingRules
  def forwardingRulesList
  def credentials
  def description

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    googleClusterProviderMock = Mock(GoogleClusterProvider)
    serverGroup = new GoogleServerGroup(zone: ZONE).view
    computeMock = Mock(Compute)
    credentials = new GoogleCredentials(PROJECT_NAME, computeMock)

    instanceGroupManagersMock = Mock(Compute.InstanceGroupManagers)
    instanceGroupManagersGetMock = Mock(Compute.InstanceGroupManagers.Get)
    instanceGroupManager = new InstanceGroupManager(instanceTemplate: INSTANCE_TEMPLATE_NAME, targetPools: TARGET_POOL_URLS)

    instanceGroupsMock = Mock(Compute.InstanceGroups)
    instanceGroupsListInstancesMock = Mock(Compute.InstanceGroups.ListInstances)
    items = [new InstanceWithNamedPorts(instance: INSTANCE_URL_1),
             new InstanceWithNamedPorts(instance: INSTANCE_URL_2)]
    instanceGroupsListInstances = new InstanceGroupsListInstances(items: items)

    instanceTemplatesMock = Mock(Compute.InstanceTemplates)
    instanceTemplatesGetMock = Mock(Compute.InstanceTemplates.Get)
    instanceMetadata = GCEUtil.buildMetadataFromMap(INSTANCE_METADATA)
    instanceProperties = new InstanceProperties(metadata: instanceMetadata)
    instanceTemplate = new InstanceTemplate(name: INSTANCE_TEMPLATE_NAME, properties: instanceProperties)

    forwardingRulesMock = Mock(Compute.ForwardingRules)
    forwardingRulesListMock = Mock(Compute.ForwardingRules.List)
    forwardingRules = [new ForwardingRule(name: FORWARDING_RULE_1, target: TARGET_POOL_URL_1),
                       new ForwardingRule(name: FORWARDING_RULE_2, target: TARGET_POOL_URL_2)]
    forwardingRulesList = new ForwardingRuleList(items: forwardingRules)

    targetPoolsMock = Mock(Compute.TargetPools)
    targetPoolsAddInstanceMock = Mock(Compute.TargetPools.AddInstance)

    instanceGroupManagersSetTargetPoolsMock = Mock(Compute.InstanceGroupManagers.SetTargetPools)

    description = new EnableDisableGoogleServerGroupDescription(serverGroupName: SERVER_GROUP_NAME,
                                                                region: REGION,
                                                                accountName: ACCOUNT_NAME,
                                                                credentials: credentials)
  }

  void "should add instances and attach load balancers"() {
    setup:
      @Subject def operation = new EnableGoogleServerGroupAtomicOperation(description)
      operation.googleClusterProvider = googleClusterProviderMock

    when:
      operation.operate([])

    then:
      1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, SERVER_GROUP_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManager

      1 * computeMock.instanceGroups() >> instanceGroupsMock
      1 * instanceGroupsMock.listInstances(PROJECT_NAME, ZONE, SERVER_GROUP_NAME, _) >> instanceGroupsListInstancesMock
      1 * instanceGroupsListInstancesMock.execute() >> instanceGroupsListInstances

      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.get(PROJECT_NAME, INSTANCE_TEMPLATE_NAME) >> instanceTemplatesGetMock
      1 * instanceTemplatesGetMock.execute() >> instanceTemplate

      1 * computeMock.forwardingRules() >> forwardingRulesMock
      1 * forwardingRulesMock.list(PROJECT_NAME, REGION) >> forwardingRulesListMock
      1 * forwardingRulesListMock.execute() >> forwardingRulesList

      [TARGET_POOL_NAME_1, TARGET_POOL_NAME_2].each { targetPoolLocalName ->
        1 * computeMock.targetPools() >> targetPoolsMock
        1 * targetPoolsMock.addInstance(PROJECT_NAME, REGION, targetPoolLocalName, _) >> targetPoolsAddInstanceMock
        1 * targetPoolsAddInstanceMock.execute()
      }

      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.setTargetPools(PROJECT_NAME, ZONE, SERVER_GROUP_NAME, _) >>
              instanceGroupManagersSetTargetPoolsMock
      1 * instanceGroupManagersSetTargetPoolsMock.execute()
  }

  void "should fail if load balancers cannot be resolved"() {
    setup:
      def forwardingRules2 = [new ForwardingRule(name: "${FORWARDING_RULE_1}_WRONG", target: TARGET_POOL_URL_1),
                              new ForwardingRule(name: "${FORWARDING_RULE_2}_WRONG", target: TARGET_POOL_URL_2)]
      def forwardingRulesList2 = new ForwardingRuleList(items: forwardingRules2)

      @Subject def operation = new EnableGoogleServerGroupAtomicOperation(description)
      operation.googleClusterProvider = googleClusterProviderMock

    when:
      operation.operate([])

    then:
      1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, SERVER_GROUP_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManager

      1 * computeMock.instanceGroups() >> instanceGroupsMock
      1 * instanceGroupsMock.listInstances(PROJECT_NAME, ZONE, SERVER_GROUP_NAME, _) >> instanceGroupsListInstancesMock
      1 * instanceGroupsListInstancesMock.execute() >> instanceGroupsListInstances

      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.get(PROJECT_NAME, INSTANCE_TEMPLATE_NAME) >> instanceTemplatesGetMock
      1 * instanceTemplatesGetMock.execute() >> instanceTemplate

      1 * computeMock.forwardingRules() >> forwardingRulesMock
      1 * forwardingRulesMock.list(PROJECT_NAME, REGION) >> forwardingRulesListMock
      1 * forwardingRulesListMock.execute() >> forwardingRulesList2

      def exc = thrown GoogleResourceNotFoundException
      exc.message == "Network load balancers [$FORWARDING_RULE_1, $FORWARDING_RULE_2] not found."
  }
}
