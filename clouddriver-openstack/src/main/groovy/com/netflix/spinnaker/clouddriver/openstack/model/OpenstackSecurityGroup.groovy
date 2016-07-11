/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.openstack.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.netflix.spinnaker.clouddriver.model.AddressableRange
import com.netflix.spinnaker.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.clouddriver.model.securitygroups.IpRangeRule
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import com.netflix.spinnaker.clouddriver.model.securitygroups.SecurityGroupRule
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import groovy.transform.Immutable
import org.openstack4j.model.compute.SecGroupExtension

@Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
class OpenstackSecurityGroup implements SecurityGroup {
  final String type = OpenstackCloudProvider.ID
  final String id
  final String name
  final String description
  final String application
  final String accountName
  final String region
  final Set<Rule> inboundRules
  final Set<Rule> outboundRules

  @JsonIgnore
  @Override
  SecurityGroupSummary getSummary() {
    new OpenstackSecurityGroupSummary(name: name, id: id)
  }

  static OpenstackSecurityGroup from(SecGroupExtension securityGroup, String accountName, String region) {
    new OpenstackSecurityGroup(id: securityGroup.id,
      accountName: accountName,
      region: region,
      name: securityGroup.name,
      description: securityGroup.description,
      inboundRules: buildInboundRules(securityGroup, accountName, region)
    )
  }

  private static List<Rule> buildInboundRules(SecGroupExtension securityGroup, String accountName, String region) {
    securityGroup.rules.collect { rule ->
      // The Openstack4J library doesn't put a type on the rule, instead, it includes a range object with a null cidr
      rule.range?.cidr ? buildIpRangeRule(rule) : buildSecurityGroupRule(rule, accountName, region)
    }
  }

  private static SecurityGroupRule buildSecurityGroupRule(SecGroupExtension.Rule rule, String accountName, String region) {
    def portRange = new Rule.PortRange(startPort: rule.fromPort, endPort: rule.toPort)
    def securityGroup = new OpenstackSecurityGroup(
      name: rule.group.name,
      type: OpenstackCloudProvider.ID,
      accountName: accountName,
      region: region
    )
    new SecurityGroupRule(protocol: rule.IPProtocol.value(),
      portRanges: [portRange] as SortedSet,
      securityGroup: securityGroup
    )
  }

  private static IpRangeRule buildIpRangeRule(SecGroupExtension.Rule rule) {
    def portRange = new Rule.PortRange(startPort: rule.fromPort, endPort: rule.toPort)
    def addressableRange = buildAddressableRangeFromCidr(rule.range.cidr)
    new IpRangeRule(protocol: rule.IPProtocol.value(),
      portRanges: [portRange] as SortedSet,
      range: addressableRange
    )
  }

  private static AddressableRange buildAddressableRangeFromCidr(String cidr) {
    if (!cidr) {
      return null
    }

    def rangeParts = cidr.split('/') as List

    // If the cidr just a single IP address, use 32 as the mask
    if (rangeParts.size() == 1) {
      rangeParts << "32"
    }

    new AddressableRange(ip: rangeParts[0], cidr: "/${rangeParts[1]}")
  }
}
