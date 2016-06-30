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

import com.netflix.spinnaker.clouddriver.model.Subnet
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import groovy.transform.Canonical
import org.openstack4j.model.network.Subnet as DomainSubnet

@Canonical
class OpenstackSubnet implements Subnet {
  String type
  String name
  String id
  String cidrBlock
  List<Range> allocationPools
  List<String> dnsNameservers
  boolean enableDhcp
  String gatewayIp
  Integer ipVersion
  String networkId
  String account
  String region
  String purpose = 'n/a'

  static class Range {
    String start
    String end
  }

  static OpenstackSubnet from(DomainSubnet subnet, String account, String region) {
    new OpenstackSubnet(
      type: OpenstackCloudProvider.ID,
      account: account,
      region: region,
      id: subnet.id,
      name: subnet.name,
      cidrBlock: subnet.cidr,
      allocationPools: subnet.allocationPools?.collect { it -> new Range(start: it.start, end: it.end) },
      dnsNameservers: subnet.dnsNames,
      enableDhcp: subnet.DHCPEnabled,
      gatewayIp: subnet.gateway,
      ipVersion: subnet.ipVersion?.version,
      networkId: subnet.networkId)
  }
}
