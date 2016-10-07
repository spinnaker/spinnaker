/*
 * Copyright 2016 Target Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider
import groovy.transform.Canonical
import org.openstack4j.model.compute.Address
import org.openstack4j.model.compute.Server

@Canonical
class OpenstackInstance {
  String name
  String instanceId
  String instanceType
  Long launchTime
  String zone
  String region
  String keyName
  Map<String, String> metadata
  String account
  String ipv4
  String ipv6
  String floatingIp
  List<OpenstackLoadBalancerHealth> loadBalancerHealths = []
  OpenstackInstanceHealth instanceHealth
  List<String> securityGroups = []

  static OpenstackInstance from(Server server, String account, String region) {
    //find first fixed v4 address
    Address fixedIpAddressV4 = server?.addresses?.addresses?.collectMany { it.value }?.find { it.type == 'fixed' && it.version == 4 }
    //find first fixed v6 address
    Address fixedIpAddressV6 = server?.addresses?.addresses?.collectMany { it.value }?.find { it.type == 'fixed' && it.version == 6 }
    //floating ip
    Address floatingIpAddress = server?.addresses?.addresses?.collectMany { it.value }?.find { it.type == 'floating' && it.version == 4 }

    new OpenstackInstance(name: server.name
      , region: region
      , account: account
      , zone: server.availabilityZone
      , instanceId: server.id
      , instanceType: server.flavor?.name
      , launchTime: server.launchedAt?.time
      , metadata: server.metadata
      , instanceHealth: new OpenstackInstanceHealth(status: server.status)
      , keyName: server.keyName
      , ipv4: fixedIpAddressV4?.addr
      , ipv6: fixedIpAddressV6?.addr
      , floatingIp: floatingIpAddress?.addr
      , securityGroups: server.securityGroups?.collect { it.name })
  }

  @JsonIgnore
  View getView() {
    new View()
  }

  @Canonical
  class View implements Instance {

    final String providerType = OpenstackCloudProvider.ID //expected by deck

    String name = OpenstackInstance.this.instanceId //expected by deck
    String instanceId = OpenstackInstance.this.instanceId
    String instanceName = OpenstackInstance.this.name
    String instanceType = OpenstackInstance.this.instanceType //expected by deck
    Long launchTime = OpenstackInstance.this.launchTime
    String zone = OpenstackInstance.this.zone
    String region = OpenstackInstance.this.region
    Map placement = ["availabilityZone": OpenstackInstance.this.zone] //expected by deck
    String keyName = OpenstackInstance.this.keyName
    Map<String, String> metadata = OpenstackInstance.this.metadata
    String account = OpenstackInstance.this.account
    String ipv4 = OpenstackInstance.this.ipv4
    String ipv6 = OpenstackInstance.this.ipv6
    String floatingIp = OpenstackInstance.this.floatingIp

    List<Map<String, String>> getSecurityGroups() {
      OpenstackInstance.this.securityGroups.collect {
        ["groupName": it, "groupId": it]
      }
    }

    @Override
    List<Map<String, Object>> getHealth() {
      ObjectMapper mapper = new ObjectMapper()
      List<Map<String, Object>> healths = []

      // load balancer health
      loadBalancerHealths?.each {
        healths << mapper.convertValue(it.view, OpenstackInfrastructureProvider.ATTRIBUTES)
      }

      //instance health
      healths << mapper.convertValue(instanceHealth?.view, OpenstackInfrastructureProvider.ATTRIBUTES)

      //TODO derekolk - Add consul health

      healths
    }

    @JsonIgnore
    List<OpenstackHealth> allHealths() {
      def allHealths = []

      loadBalancerHealths?.each {
        allHealths << it.view
      }
      if (instanceHealth) {
        allHealths << instanceHealth.view
      }

      //TODO derekolk - Add consul health views

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

    private static boolean anyDown(List<OpenstackHealth> healthsList) {
      healthsList.any { it.state == HealthState.Down }
    }

    private static boolean someUpRemainingUnknown(List<OpenstackHealth> healthsList) {
      List<OpenstackHealth> knownHealthList = healthsList.findAll { it.state != HealthState.Unknown }
      knownHealthList ? knownHealthList.every { it.state == HealthState.Up } : false
    }

    private static boolean anyStarting(List<OpenstackHealth> healthsList) {
      healthsList.any { it.state == HealthState.Starting }
    }

    private static boolean anyOutOfService(List<OpenstackHealth> healthsList) {
      healthsList.any { it.state == HealthState.OutOfService }
    }
  }
}
