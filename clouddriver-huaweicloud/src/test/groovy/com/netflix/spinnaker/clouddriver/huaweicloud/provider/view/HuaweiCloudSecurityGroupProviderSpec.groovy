/*
 * Copyright 2020 Huawei Technologies Co.,Ltd.
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

package com.netflix.spinnaker.clouddriver.huaweicloud.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.huawei.openstack4j.openstack.vpc.v1.domain.SecurityGroup
import com.huawei.openstack4j.openstack.vpc.v1.domain.SecurityGroupRule
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.cache.WriteableCache
import com.netflix.spinnaker.cats.mem.InMemoryCache
import com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys
import com.netflix.spinnaker.clouddriver.huaweicloud.model.HuaweiCloudSecurityGroup
import com.netflix.spinnaker.clouddriver.huaweicloud.security.HuaweiCloudNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.model.AddressableRange
import com.netflix.spinnaker.clouddriver.model.securitygroups.IpRangeRule
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class HuaweiCloudSecurityGroupProviderSpec extends Specification {

  @Subject
  HuaweiCloudSecurityGroupProvider provider

  WriteableCache cache = new InMemoryCache()
  ObjectMapper mapper = new ObjectMapper()

  def setup() {
    provider = new HuaweiCloudSecurityGroupProvider(cache, mapper)
    cache.mergeAll(Keys.Namespace.SECURITY_GROUPS.ns, getAllGroups())
  }

  void "getAll lists all"() {
    when:
      def result = provider.getAll(false)

    then:
      result.size() == 2
  }

  void "getAllByRegion lists only those in supplied region"() {
    setup:
      def region = 'cn-north-1'

    when:
      def result = provider.getAllByRegion(false, region)

    then:
      result.size() == 1
      result.count { it.region == region } == 1
  }

  @Unroll
  void "getAllByAccount lists only those in supplied #account"() {
    when:
      def result = provider.getAllByAccount(false, account)

    then:
      result.size() == count
      result.count { it.accountName == account } == count

    where:
      account | count
      'prod'  | 1
      'test'  | 1
  }

  void "getAllByAccountAndRegion lists only those in supplied account and region"() {
    setup:
      def account = 'prod'
      def region = 'cn-north-1'

    when:
      def result = provider.getAllByAccountAndRegion(false, account, region)

    then:
      result.size() == 1
      result.count {
        it.accountName == account
        it.region == region
      } == 1
  }

  void "getAllByAccountAndName lists only those in supplied account with supplied name"() {
    setup:
      def account = 'test'
      def name = 'name-b'

    when:
      def result = provider.getAllByAccountAndName(false, account, name)

    then:
      result.size() == 1
      result.count {
        it.accountName == account
        it.name == name
      } == 1
  }

  void "get returns match based on account, region, and name"() {
    setup:
      def account = 'test'
      def region = 'cn-north-2'
      def name = 'name-b'

    when:
      def result = provider.get(account, region, name, null)

    then:
      result != null
      result.accountName == account
      result.region == region
      result.name == name
  }

  void "should add ipRangeRules with different protocols"() {
    setup:
      def account = 'prod'
      def region = 'cn-north-1'
      def name = 'name-a'
      def vpcId = 'default'

    when:
      def sg = provider.get(account, region, name, vpcId)

    then:
      sg != null
      sg.accountName == account
      sg.region == region
      sg.name == name

      def rule = sg.inboundRules.find { it.protocol }

      def rule1 = new IpRangeRule(
        range: new AddressableRange(ip: '', cidr: '0.0.0.0/0'),
        portRanges: [
          new Rule.PortRange(startPort: 80, endPort: 80)
        ] as SortedSet,
        protocol: 'tcp'
      )
      rule == rule1

      def rule2 = sg.inboundRules.find { !it.protocol }

      def rule3 = new com.netflix.spinnaker.clouddriver.model.securitygroups.SecurityGroupRule(
        portRanges: [
          new Rule.PortRange(startPort: 1, endPort: 65535)
        ] as SortedSet,
        securityGroup: new HuaweiCloudSecurityGroup(
          sg.id,
          name,
          region,
          account,
          'name',
          '',
          null,
          null)
      )
      rule2.portRanges == rule3.portRanges
      rule2.securityGroup.id == rule3.securityGroup.id
      rule2.securityGroup.name == rule3.securityGroup.name
      rule2.securityGroup.region == rule3.securityGroup.region
      rule2.securityGroup.accountName == rule3.securityGroup.accountName
      rule2.securityGroup.application == rule3.securityGroup.application
      rule2.securityGroup.vpcId == rule3.securityGroup.vpcId
      rule2.securityGroup.inboundRules == rule3.securityGroup.inboundRules
      rule2.securityGroup.outboundRules == rule3.securityGroup.outboundRules
  }

  @Shared
  Map<String, Map<String, List<SecurityGroup>>> firewallMap = [
    'prod': [
      'cn-north-1': [
        SecurityGroup.builder()
          .name('name-a')
          .id('3b5ceb06-3b8d-43ee-866a-dc0443b85def')
          .vpcId('default')
          .securityGroupRules([
            SecurityGroupRule.builder()
             .direction('ingress')
             .ethertype('IPv6')
             .id('976d4696-865f-4fb4-ac1d-385e3b06fd74')
             .remoteGroupId('3b5ceb06-3b8d-43ee-866a-dc0443b85def')
             .build(),

            SecurityGroupRule.builder()
              .direction('ingress')
              .ethertype('IPv4')
              .id('16c10a5d-572a-47bf-bf52-be3aacf15845')
              .portRangeMax(80)
              .portRangeMin(80)
              .protocol('tcp')
              .remoteIpPrefix('0.0.0.0/0')
              .build(),
          ])
         .build()
      ]
    ],
    test: [
      'cn-north-2': [
        SecurityGroup.builder()
          .name('name-b')
          .id('3b5ceb06-3b8d-43ee-866a-dc0443b85deg')
          .securityGroupRules([
            SecurityGroupRule.builder()
             .direction('ingress')
             .ethertype('IPv6')
             .id('976d4696-865f-4fb4-ac1d-385e3b06fd73')
             .remoteGroupId('3b5ceb06-3b8d-43ee-866a-dc0443b85deg')
             .build(),

            SecurityGroupRule.builder()
              .direction('ingress')
              .ethertype('IPv4')
              .id('16c10a5d-572a-47bf-bf52-be3aacf15844')
              .portRangeMax(80)
              .portRangeMin(80)
              .protocol('tcp')
              .remoteIpPrefix('0.0.0.0/0')
              .build(),
          ])
         .build()
      ]
    ]
  ]

  private List<CacheData> getAllGroups() {
    firewallMap.collect { String account, Map<String, List<SecurityGroup>> regions ->
      regions.collect { String region, List<SecurityGroup> firewalls ->
        firewalls.collect { SecurityGroup firewall ->
          String cacheId = Keys.getSecurityGroupKey(firewall.getName(), firewall.getId(), account, region)
          Map<String, Object> attributes = [
            'securityGroup': firewall,
            'relevantSecurityGroups': [(firewall.getId()): cacheId]
          ]
          return new DefaultCacheData(cacheId, attributes, [:])
        }
      }.flatten()
    }.flatten()
  }
}
