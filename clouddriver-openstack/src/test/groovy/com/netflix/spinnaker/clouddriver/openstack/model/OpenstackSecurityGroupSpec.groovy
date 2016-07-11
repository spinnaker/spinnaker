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

import com.netflix.spinnaker.clouddriver.model.securitygroups.IpRangeRule
import com.netflix.spinnaker.clouddriver.model.securitygroups.SecurityGroupRule
import org.openstack4j.model.compute.IPProtocol
import org.openstack4j.openstack.compute.domain.NovaSecGroupExtension
import spock.lang.Specification

class OpenstackSecurityGroupSpec extends Specification {

  def "converts sec group extension object to openstack security group"() {
    given:
    def id = UUID.randomUUID().toString()
    def name = 'security-group'
    def desc = 'description of security group'
    def accountName = 'os-account'
    def region = 'west'
    def novaSecurityGroup = new NovaSecGroupExtension(name: name, description: desc, id: id, rules: [
      new NovaSecGroupExtension.SecurityGroupRule(fromPort: 80,
        toPort: 80,
        ipProtocol: IPProtocol.TCP,
        ipRange: new NovaSecGroupExtension.SecurityGroupRule.RuleIpRange(cidr: '10.10.0.0/24')
      ),
      new NovaSecGroupExtension.SecurityGroupRule(fromPort: 22,
        toPort: 22,
        ipProtocol: IPProtocol.TCP,
        ipRange: new NovaSecGroupExtension.SecurityGroupRule.RuleIpRange(cidr: '10.10.0.0')
      ),
      new NovaSecGroupExtension.SecurityGroupRule(fromPort: 443,
        toPort: 443,
        ipProtocol: IPProtocol.TCP,
        ipRange: new NovaSecGroupExtension.SecurityGroupRule.RuleIpRange(cidr: null),
        group: new NovaSecGroupExtension.SecurityGroupRule.RuleGroup(name: 'default', tenantId: 'abc')
      )
    ])
    when:
    def securityGroup = OpenstackSecurityGroup.from(novaSecurityGroup, accountName, region)

    then:
    securityGroup.id == id
    securityGroup.accountName == accountName
    securityGroup.region == region
    securityGroup.name == name
    securityGroup.description == desc
    securityGroup.inboundRules.size() == 3

    and:
    def httpRule = securityGroup.inboundRules.find { it.portRanges.first().startPort == 80 }
    httpRule.protocol == IPProtocol.TCP.value()
    httpRule.portRanges.first().endPort == 80
    httpRule instanceof IpRangeRule
    ((IpRangeRule) httpRule).range.cidr == '/24'
    ((IpRangeRule) httpRule).range.ip == '10.10.0.0'

    and:
    def sshRule = securityGroup.inboundRules.find { it.portRanges.first().startPort == 22 }
    sshRule.protocol == IPProtocol.TCP.value()
    sshRule.portRanges.first().endPort == 22
    sshRule instanceof IpRangeRule
    ((IpRangeRule) sshRule).range.cidr == '/32'
    ((IpRangeRule) sshRule).range.ip == '10.10.0.0'

    and:
    def httpsRule = securityGroup.inboundRules.find { it.portRanges.first().startPort == 443 }
    httpsRule.protocol == IPProtocol.TCP.value()
    httpsRule.portRanges.first().endPort == 443
    httpsRule instanceof SecurityGroupRule
    ((SecurityGroupRule) httpsRule).securityGroup.name == 'default'
    ((SecurityGroupRule) httpsRule).securityGroup.accountName == accountName
    ((SecurityGroupRule) httpsRule).securityGroup.region == region
  }

  def "converts sec group extension object to openstack security group without rules"() {
    given:
    def id = UUID.randomUUID().toString()
    def name = 'security-group'
    def desc = 'description of security group'
    def accountName = 'os-account'
    def region = 'west'
    def novaSecurityGroup = new NovaSecGroupExtension(name: name, description: desc, id: id, rules: [])

    when:
    def securityGroup = OpenstackSecurityGroup.from(novaSecurityGroup, accountName, region)

    then:
    securityGroup.id == id
    securityGroup.accountName == accountName
    securityGroup.region == region
    securityGroup.name == name
    securityGroup.description == desc
    securityGroup.inboundRules.empty
  }
}
