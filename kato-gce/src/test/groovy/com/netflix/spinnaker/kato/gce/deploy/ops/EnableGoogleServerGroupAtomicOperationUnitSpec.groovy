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
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.ForwardingRuleList
import com.google.api.services.compute.model.InstanceProperties
import com.google.api.services.compute.model.InstanceTemplate
import com.google.api.services.compute.model.Zone
import com.google.api.services.replicapool.Replicapool
import com.google.api.services.replicapool.model.InstanceGroupManager
import com.google.api.services.resourceviews.Resourceviews
import com.google.api.services.resourceviews.model.ListResourceResponseItem
import com.google.api.services.resourceviews.model.ZoneViewsListResourcesResponse
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GCEResourceNotFoundException
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.description.EnableDisableGoogleServerGroupDescription
import spock.lang.Specification
import spock.lang.Subject

class EnableGoogleServerGroupAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final REPLICA_POOL_NAME = "mjdapp-dev-v009"
  private static final INSTANCE_TEMPLATE_NAME = "$REPLICA_POOL_NAME-${System.currentTimeMillis()}"
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

  def computeMock
  def replicaPoolBuilderMock
  def replicaPoolMock
  def zonesMock
  def zonesGetMock
  def instanceGroupManagersMock
  def instanceGroupManagersGetMock
  def resourceViewsBuilderMock
  def resourceViewsMock
  def zoneViewsMock
  def zoneViewsListResourcesMock
  def instanceTemplatesMock
  def instanceTemplatesGetMock
  def forwardingRulesMock
  def forwardingRulesListMock
  def targetPoolsMock
  def targetPoolsAddInstanceMock
  def instanceGroupManagersSetTargetPoolsMock

  def zone
  def instanceGroupManager
  def zoneViewsListResourcesResponse
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
    computeMock = Mock(Compute)
    credentials = new GoogleCredentials(PROJECT_NAME, computeMock)

    replicaPoolBuilderMock = Mock(ReplicaPoolBuilder)
    replicaPoolMock = Mock(Replicapool)

    zonesMock = Mock(Compute.Zones)
    zonesGetMock = Mock(Compute.Zones.Get)
    zone = new Zone(region: REGION)

    instanceGroupManagersMock = Mock(Replicapool.InstanceGroupManagers)
    instanceGroupManagersGetMock = Mock(Replicapool.InstanceGroupManagers.Get)
    instanceGroupManager = new InstanceGroupManager(instanceTemplate: INSTANCE_TEMPLATE_NAME, targetPools: TARGET_POOL_URLS)

    resourceViewsBuilderMock = Mock(ResourceViewsBuilder)
    resourceViewsMock = Mock(Resourceviews)

    zoneViewsMock = Mock(Resourceviews.ZoneViews)
    zoneViewsListResourcesMock = Mock(Resourceviews.ZoneViews.ListResources)
    items = [new ListResourceResponseItem(resource: INSTANCE_URL_1),
             new ListResourceResponseItem(resource: INSTANCE_URL_2)]
    zoneViewsListResourcesResponse = new ZoneViewsListResourcesResponse(items: items)

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

    instanceGroupManagersSetTargetPoolsMock = Mock(Replicapool.InstanceGroupManagers.SetTargetPools)

    description = new EnableDisableGoogleServerGroupDescription(replicaPoolName: REPLICA_POOL_NAME,
                                                                zone: ZONE,
                                                                accountName: ACCOUNT_NAME,
                                                                credentials: credentials)
  }

  void "should add instances and attach load balancers"() {
    setup:
      @Subject def operation =
              new EnableGoogleServerGroupAtomicOperation(description, replicaPoolBuilderMock, resourceViewsBuilderMock)

    when:
      operation.operate([])

    then:
      1 * computeMock.zones() >> zonesMock
      1 * zonesMock.get(PROJECT_NAME, ZONE) >> zonesGetMock
      1 * zonesGetMock.execute() >> zone

      2 * replicaPoolBuilderMock.buildReplicaPool(_, _) >> replicaPoolMock
      1 * replicaPoolMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, REPLICA_POOL_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManager

      1 * resourceViewsBuilderMock.buildResourceViews(_, _) >> resourceViewsMock
      1 * resourceViewsMock.zoneViews() >> zoneViewsMock
      1 * zoneViewsMock.listResources(PROJECT_NAME, ZONE, REPLICA_POOL_NAME) >> zoneViewsListResourcesMock
      1 * zoneViewsListResourcesMock.execute() >> zoneViewsListResourcesResponse

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

      1 * replicaPoolMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.setTargetPools(PROJECT_NAME, ZONE, REPLICA_POOL_NAME, _) >>
              instanceGroupManagersSetTargetPoolsMock
      1 * instanceGroupManagersSetTargetPoolsMock.execute()
  }

  void "should fail if load balancers cannot be resolved"() {
    setup:
      def forwardingRules2 = [new ForwardingRule(name: "${FORWARDING_RULE_1}_WRONG", target: TARGET_POOL_URL_1),
                              new ForwardingRule(name: "${FORWARDING_RULE_2}_WRONG", target: TARGET_POOL_URL_2)]
      def forwardingRulesList2 = new ForwardingRuleList(items: forwardingRules2)

      @Subject def operation =
              new EnableGoogleServerGroupAtomicOperation(description, replicaPoolBuilderMock, resourceViewsBuilderMock)

    when:
      operation.operate([])

    then:
      1 * computeMock.zones() >> zonesMock
      1 * zonesMock.get(PROJECT_NAME, ZONE) >> zonesGetMock
      1 * zonesGetMock.execute() >> zone

      2 * replicaPoolBuilderMock.buildReplicaPool(_, _) >> replicaPoolMock
      1 * replicaPoolMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, REPLICA_POOL_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManager

      1 * resourceViewsBuilderMock.buildResourceViews(_, _) >> resourceViewsMock
      1 * resourceViewsMock.zoneViews() >> zoneViewsMock
      1 * zoneViewsMock.listResources(PROJECT_NAME, ZONE, REPLICA_POOL_NAME) >> zoneViewsListResourcesMock
      1 * zoneViewsListResourcesMock.execute() >> zoneViewsListResourcesResponse

      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.get(PROJECT_NAME, INSTANCE_TEMPLATE_NAME) >> instanceTemplatesGetMock
      1 * instanceTemplatesGetMock.execute() >> instanceTemplate

      1 * computeMock.forwardingRules() >> forwardingRulesMock
      1 * forwardingRulesMock.list(PROJECT_NAME, REGION) >> forwardingRulesListMock
      1 * forwardingRulesListMock.execute() >> forwardingRulesList2

      def exc = thrown GCEResourceNotFoundException
      exc.message == "Network load balancers [$FORWARDING_RULE_1, $FORWARDING_RULE_2] not found."
  }
}
