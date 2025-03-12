/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.client.json.GenericJson
import com.google.api.services.compute.model.*
import com.netflix.spinnaker.clouddriver.consul.model.ConsulHealth
import com.netflix.spinnaker.clouddriver.consul.model.ConsulNode
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleHealth
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleInstanceHealth
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.moniker.Moniker
import groovy.transform.Canonical
import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode(includes = "name")
class GoogleInstance implements GoogleLabeledResource {

  String name
  String account
  String gceId
  String instanceType
  String cpuPlatform
  Long launchTime
  String zone
  String region
  GoogleInstanceHealth instanceHealth
  List<GoogleLoadBalancerHealth> loadBalancerHealths = []
  ConsulNode consulNode
  List<NetworkInterface> networkInterfaces
  String networkName
  Metadata metadata
  // This should be List<AttachedDisk> but objectMapper.convertValue doesn't work with
  // AttachedDisks. We deserialize the JSON to a Map first, which turns the diskSizeGb value into an
  // Integer. Then convertValue tries to assign it to the Long field and throws an exception. We
  // could solve this with a mixin (see AmazonObjectMapperConfigurer) but since no one actually
  // cares about the type, we just use GenericJson to pass through the data to deck without
  // interpreting it at all.
  List<? extends GenericJson> disks
  List<ServiceAccount> serviceAccounts
  String selfLink
  Tags tags
  Map<String, String> labels

  // Non-serialized values built up by providers
  @JsonIgnore
  String serverGroup
  @JsonIgnore
  List<String> securityGroups = []

  @JsonIgnore
  View getView() {
    new View(this)
  }

  @Canonical
  class View implements Instance {

    final String providerType = GoogleCloudProvider.ID
    final String cloudProvider = GoogleCloudProvider.ID

    String name
    String gceId
    String instanceId
    String instanceType
    String cpuPlatform
    Long launchTime
    String zone
    String region
    Map placement
    List<NetworkInterface> networkInterfaces
    Metadata metadata
    List<? extends GenericJson> disks
    List<ServiceAccount> serviceAccounts
    String selfLink
    String serverGroup
    Tags tags
    Map<String, String> labels
    ConsulNode consulNode
    List<String> securityGroups

    View(GoogleInstance googleInstance){
      name = googleInstance.name
      gceId = googleInstance.gceId
      instanceId = googleInstance.name
      instanceType = googleInstance.instanceType
      cpuPlatform = googleInstance.cpuPlatform
      launchTime = googleInstance.launchTime
      zone = googleInstance.zone
      region = googleInstance.region
      placement = ["availabilityZone": googleInstance.zone]
      networkInterfaces = googleInstance.networkInterfaces
      metadata = googleInstance.metadata
      disks = googleInstance.disks
      serviceAccounts = googleInstance.serviceAccounts
      selfLink = googleInstance.selfLink
      serverGroup = googleInstance.serverGroup
      tags = googleInstance.tags
      labels = googleInstance.labels
      consulNode = googleInstance.consulNode
      securityGroups = googleInstance.securityGroups
    }

    List<Map<String, String>> getSecurityGroups() {
      securityGroups.collect {
        ["groupName": it, "groupId": it]
      }
    }

    @Override
    List<Map<String, Object>> getHealth() {
      ObjectMapper mapper = new ObjectMapper()
      def healths = []
      loadBalancerHealths.each { GoogleLoadBalancerHealth h ->
        healths << mapper.convertValue(h.view, new TypeReference<Map<String, Object>>() {})
      }
      consulNode?.healths?.each { ConsulHealth h ->
        healths << mapper.convertValue(h, new TypeReference<Map<String, Object>>() {})
      }
      healths << mapper.convertValue(instanceHealth?.view, new TypeReference<Map<String, Object>>() {})
      healths
    }

    @JsonIgnore
    List<GoogleHealth> allHealths() {
      def allHealths = []
      loadBalancerHealths?.each{
        allHealths << it.view
      }
      if (instanceHealth) {
        allHealths << instanceHealth.view
      }
      consulNode?.healths?.each {
        allHealths << it
      }
      allHealths
    }

    @Override
    HealthState getHealthState() {
      def allHealths = allHealths()
      someUpRemainingUnknown(allHealths) ? HealthState.Up :
          anyStarting(allHealths) ? HealthState.Starting :
              anyDown(allHealths) ? HealthState.Down :
                  anyOutOfService(allHealths) ? HealthState.OutOfService :
                      HealthState.Unknown
    }

    Moniker getMoniker() {
      return NamerRegistry.lookup()
        .withProvider(GoogleCloudProvider.ID)
        .withAccount(account)
        .withResource(GoogleLabeledResource)
        .deriveMoniker(GoogleInstance.this)
    }

    private static boolean anyDown(List<GoogleHealth> healthsList) {
      healthsList.any { it.state == HealthState.Down }
    }

    private static boolean someUpRemainingUnknown(List<GoogleHealth> healthsList) {
      List<GoogleHealth> knownHealthList = healthsList.findAll { it.state != HealthState.Unknown }
      knownHealthList ? knownHealthList.every { it.state == HealthState.Up } : false
    }

    private static boolean anyStarting(List<GoogleHealth> healthsList) {
      healthsList.any { it.state == HealthState.Starting }
    }

    private static boolean anyOutOfService(List<GoogleHealth> healthsList) {
      healthsList.any { it.state == HealthState.OutOfService }
    }
  }
}
