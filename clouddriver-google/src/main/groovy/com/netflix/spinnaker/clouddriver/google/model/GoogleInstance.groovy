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
import com.google.api.services.compute.model.Disk
import com.google.api.services.compute.model.Metadata
import com.google.api.services.compute.model.NetworkInterface
import com.google.api.services.compute.model.ServiceAccount
import com.google.api.services.compute.model.Tags
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleHealth
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleInstanceHealth
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import groovy.transform.Canonical
import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode(includes = "name")
class GoogleInstance {

  String name
  String instanceType
  Long launchTime
  String zone
  String region
  GoogleInstanceHealth instanceHealth
  List<GoogleLoadBalancerHealth> loadBalancerHealths = []
  List<NetworkInterface> networkInterfaces
  Metadata metadata
  List<Disk> disks
  List<ServiceAccount> serviceAccounts
  String selfLink
  Tags tags

  // Non-serialized values built up by providers
  @JsonIgnore
  String serverGroup
  @JsonIgnore
  List<String> securityGroups = []

  @JsonIgnore
  String getNetworkName() {
    return Utils.getLocalName(networkInterfaces?.getAt(0)?.network)
  }

  @JsonIgnore
  View getView() {
    new View()
  }

  @Canonical
  class View implements Instance {

    final String providerType = GoogleCloudProvider.GCE

    String name = GoogleInstance.this.name
    String instanceId = GoogleInstance.this.name
    String instanceType = GoogleInstance.this.instanceType
    Long launchTime = GoogleInstance.this.launchTime
    String zone = GoogleInstance.this.zone
    String region = GoogleInstance.this.region
    Map placement = ["availabilityZone": GoogleInstance.this.zone]
    List<NetworkInterface> networkInterfaces = GoogleInstance.this.networkInterfaces
    Metadata metadata = GoogleInstance.this.metadata
    List<Disk> disks = GoogleInstance.this.disks
    List<ServiceAccount> serviceAccounts = GoogleInstance.this.serviceAccounts
    String selfLink = GoogleInstance.this.selfLink
    String serverGroup = GoogleInstance.this.serverGroup
    Tags tags = GoogleInstance.this.tags

    List<Map<String, String>> getSecurityGroups() {
      GoogleInstance.this.securityGroups.collect {
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
