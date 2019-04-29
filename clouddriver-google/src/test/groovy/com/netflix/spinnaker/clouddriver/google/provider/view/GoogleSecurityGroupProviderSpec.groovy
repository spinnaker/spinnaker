/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.model.Firewall
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.cache.WriteableCache
import com.netflix.spinnaker.cats.mem.InMemoryCache
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.model.GoogleSecurityGroup
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.model.AddressableRange
import com.netflix.spinnaker.clouddriver.model.securitygroups.IpRangeRule
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class GoogleSecurityGroupProviderSpec extends Specification {

  @Subject
  GoogleSecurityGroupProvider provider

  WriteableCache cache = new InMemoryCache()
  ObjectMapper mapper = new ObjectMapper()

  def setup() {
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    credentialsRepo.save("test",
                         new GoogleNamedAccountCredentials
                           .Builder()
                           .name("test")
                           .project("my-project")
                           .credentials(new FakeGoogleCredentials())
                           .build())
    credentialsRepo.save("prod",
                         new GoogleNamedAccountCredentials
                           .Builder()
                           .name("prod")
                           .project("my-project")
                           .credentials(new FakeGoogleCredentials())
                           .build())
    provider = new GoogleSecurityGroupProvider(credentialsProvider, cache, mapper)
    cache.mergeAll(Keys.Namespace.SECURITY_GROUPS.ns, getAllGroups())
  }

  void "getAll lists all"() {
    when:
      def result = provider.getAll(false)

    then:
      result.size() == 6
  }

  void "getAllByRegion lists only those in supplied region"() {
    when:
      def result = provider.getAllByRegion(false, region)

    then:
      result.size() == 6
      result.each {
        it.region == region
      }

    where:
      region = 'global'
  }

  @Unroll
  void "getAllByAccount lists only those in supplied account"() {
    when:
      def result = provider.getAllByAccount(false, account)

    then:
      result.size() == count
      result.each {
        it.accountName == account
      }

    where:
      account | count
      'prod'  | 3
      'test'  | 3
  }

  void "getAllByAccountAndRegion lists only those in supplied account and region"() {
    when:
      def result = provider.getAllByAccountAndRegion(false, account, region)

    then:
      result.size() == 3
      result.each {
        it.accountName == account
        it.region == region
      }

    where:
      account = 'prod'
      region = 'global'
  }

  void "getAllByAccountAndName lists only those in supplied account with supplied name"() {
    when:
      def result = provider.getAllByAccountAndName(false, account, name)

    then:
      result.size() == 1
      result.each {
        it.accountName == account
        it.name == name
      }

    where:
      account = 'test'
      name = 'a'
  }

  void "get returns match based on account, region, and name"() {
    when:
      def result = provider.get(account, region, name, null)

    then:
      result != null
      result.accountName == account
      result.region == region
      result.name == name

    where:
      account = 'prod'
      region = 'global'
      name = 'name-a'
  }

  void "security group id and network id should be decorated with xpn host project id"() {
    when:
      def result = provider.get(account, region, name, null)

    then:
      result != null
      result.accountName == account
      result.region == region
      result.name == name
      result.id == "some-xpn-host-project/$name"
      result.network == 'some-xpn-host-project/default'

    where:
      account = 'prod'
      region = 'global'
      name = 'name-c'
  }

  void "should add ipRangeRules with different protocols"() {
    given:
      String account = 'prod'
      String region = 'global'

    when:
      def sg = provider.get(account, region, 'name-a', null)

    then:
      sg == new GoogleSecurityGroup(
        id: 'name-a',
        name: 'name-a',
        network: 'default',
        selfLink: 'https://compute.googleapis.com/compute/v1/projects/my-project/global/firewalls/name-a',
        targetTags: ['tag-1', 'tag-2'],
        description: 'a',
        accountName: account,
        region: region,
        inboundRules: [
          new IpRangeRule(
            range: new AddressableRange(ip: '192.168.2.0', cidr: '/24'),
            portRanges: [
              new Rule.PortRange(startPort: 8080, endPort: 8080)
            ] as SortedSet,
            protocol: 'tcp'),
          new IpRangeRule(
            range: new AddressableRange(ip: '192.168.2.0', cidr: '/24'),
            portRanges: [
              new Rule.PortRange(startPort: 4040, endPort: 4042)
            ] as SortedSet,
            protocol: 'udp')
        ]
      )
      0 * _
  }

  void "should group ipRangeRules by addressable range + protocol"() {
    given:
      String account = 'prod'
      String region = 'global'

    when:
      def cachedValue = provider.get(account, region, 'name-a', null)

    then:
      cachedValue.inboundRules.size() == 2
      cachedValue.inboundRules.protocol == ['tcp', 'udp']
      cachedValue.inboundRules.range.ip == ['192.168.2.0', '192.168.2.0']
      cachedValue.inboundRules.range.cidr == ['/24', '/24']
      cachedValue.inboundRules[0].portRanges.startPort == [8080, 9090]
      cachedValue.inboundRules[0].portRanges.endPort == [8080, 9090]
      cachedValue.inboundRules[1].portRanges.startPort == [4040]
      cachedValue.inboundRules[1].portRanges.endPort == [4042]
  }

  void "should turn exact ip address as source range into cidr range"() {
    given:
      String account = 'test'
      String region = 'global'

    when:
      def cachedValue = provider.get(account, region, 'b', null)

    then:
      cachedValue.inboundRules.size() == 3
      cachedValue.inboundRules[1].protocol == 'tcp'
      cachedValue.inboundRules[1].range.ip == '192.168.3.100'
      cachedValue.inboundRules[1].range.cidr == '/32'
  }

  void "should turn exact port into port range"() {
    given:
      String account = 'test'
      String region = 'global'

    when:
      def cachedValue = provider.get(account, region, 'b', null)

    then:
      cachedValue.inboundRules.size() == 3
      cachedValue.inboundRules[1].protocol == 'tcp'
      cachedValue.inboundRules[1].portRanges.startPort == [1, 2, 3]
      cachedValue.inboundRules[1].portRanges.endPort == [1, 2, 100]
  }

  void "should return no port ranges for protocol lacking a port"() {
    given:
      String account = 'test'
      String region = 'global'

    when:
      def cachedValue = provider.get(account, region, 'b', null)

    then:
      cachedValue.inboundRules.size() == 3
      cachedValue.inboundRules[0].protocol == 'icmp'
      cachedValue.inboundRules[0].portRanges.size() == 0
  }

  void "should turn missing port for tcp or udp into full port range"() {
    given:
      String account = 'test'
      String region = 'global'

    when:
      def cachedValue = provider.get(account, region, 'c', null)

    then:
      cachedValue.inboundRules.size() == 2
      cachedValue.inboundRules[0].protocol == 'tcp'
      cachedValue.inboundRules[0].portRanges.startPort == [1]
      cachedValue.inboundRules[0].portRanges.endPort == [65535]
      cachedValue.inboundRules[1].protocol == 'udp'
      cachedValue.inboundRules[1].portRanges.startPort == [1]
      cachedValue.inboundRules[1].portRanges.endPort == [65535]
  }

  @Shared
  Map<String, Map<String, List<Firewall>>> firewallMap = [
    prod: [
      'global': [
        new Firewall(
          name: 'name-a',
          id: 6614377178691015951,
          network: 'https://compute.googleapis.com/compute/v1/projects/my-project/global/networks/default',
          targetTags: ['tag-1', 'tag-2'],
          description: 'a',
          sourceRanges: ['192.168.2.0/24'],
          allowed: [
            new Firewall.Allowed(IPProtocol: 'tcp', ports: ['8080']),
            new Firewall.Allowed(IPProtocol: 'udp', ports: ['4040-4042']),
            new Firewall.Allowed(IPProtocol: 'tcp', ports: ['9090']),
          ],
          selfLink: 'https://compute.googleapis.com/compute/v1/projects/my-project/global/firewalls/name-a'
        ),
        new Firewall(
          name: 'name-b',
          id: 6614377178691015952,
          network: 'https://compute.googleapis.com/compute/v1/projects/my-project/global/networks/default',
          selfLink: 'https://compute.googleapis.com/compute/v1/projects/my-project/global/firewalls/name-b'
        ),
        new Firewall(
          name: 'name-c',
          id: 6614377178691015954,
          network: 'https://compute.googleapis.com/compute/v1/projects/some-xpn-host-project/global/networks/default',
          selfLink: 'https://compute.googleapis.com/compute/v1/projects/some-xpn-host-project/global/firewalls/name-c'
        ),
      ]
    ],
    test: [
      'global': [
        new Firewall(
          name: 'a',
          id: 6614377178691015953,
          network: 'https://compute.googleapis.com/compute/v1/projects/my-project/global/networks/default',
          targetServiceAccounts: ['user@test.iam.gserviceaccount.com'],
          selfLink: 'https://compute.googleapis.com/compute/v1/projects/my-project/global/firewalls/a'
        ),
        new Firewall(
          name: 'b',
          id: 123,
          network: 'https://compute.googleapis.com/compute/v1/projects/my-project/global/networks/default',
          description: 'description of b',
          sourceRanges: ['192.168.3.100'],
          allowed: [
            new Firewall.Allowed(IPProtocol: 'icmp'),
            new Firewall.Allowed(IPProtocol: 'tcp', ports: ['1', '2', '3-100']),
            new Firewall.Allowed(IPProtocol: 'udp', ports: ['5050']),
          ],
          selfLink: 'https://compute.googleapis.com/compute/v1/projects/my-project/global/firewalls/b'
        ),
        new Firewall(
          name: 'c',
          id: 456,
          network: 'https://compute.googleapis.com/compute/v1/projects/my-project/global/networks/default',
          description: 'description of c',
          sourceRanges: ['192.168.4.100/32'],
          allowed: [
            new Firewall.Allowed(IPProtocol: 'tcp'),
            new Firewall.Allowed(IPProtocol: 'udp', ports: []),
          ],
          selfLink: 'https://compute.googleapis.com/compute/v1/projects/my-project/global/firewalls/c'
        ),
      ]
    ]
  ]

  private List<CacheData> getAllGroups() {
    firewallMap.collect { String account, Map<String, List<Firewall>> regions ->
      regions.collect { String region, List<Firewall> firewalls ->
        firewalls.collect { Firewall firewall ->
          Map<String, Object> attributes = [firewall: firewall]
          new DefaultCacheData(Keys.getSecurityGroupKey(firewall.getName(), firewall.getName(), "global", account), attributes, [:])
        }
      }.flatten()
    }.flatten()
  }
}
