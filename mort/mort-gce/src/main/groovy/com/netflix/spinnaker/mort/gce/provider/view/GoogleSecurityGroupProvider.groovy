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

package com.netflix.spinnaker.mort.gce.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.model.Firewall
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.model.AddressableRange
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider
import com.netflix.spinnaker.clouddriver.model.securitygroups.IpRangeRule
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import com.netflix.spinnaker.mort.gce.cache.Keys
import com.netflix.spinnaker.mort.gce.model.GoogleSecurityGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.mort.gce.cache.Keys.Namespace.SECURITY_GROUPS

@Component
class GoogleSecurityGroupProvider implements SecurityGroupProvider<GoogleSecurityGroup> {

  final GoogleCloudProvider googleCloudProvider
  final Cache cacheView
  final ObjectMapper objectMapper

  @Autowired
  GoogleSecurityGroupProvider(GoogleCloudProvider googleCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.googleCloudProvider = googleCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  String getType() {
    return googleCloudProvider.id
  }

  @Override
  Set<GoogleSecurityGroup> getAll(boolean includeRules) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(googleCloudProvider, '*', '*', '*', '*'), includeRules)
  }

  @Override
  Set<GoogleSecurityGroup> getAllByRegion(boolean includeRules, String region) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(googleCloudProvider, '*', '*', region, '*'), includeRules)
  }

  @Override
  Set<GoogleSecurityGroup> getAllByAccount(boolean includeRules, String account) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(googleCloudProvider, '*', '*', '*', account), includeRules)
  }

  @Override
  Set<GoogleSecurityGroup> getAllByAccountAndName(boolean includeRules, String account, String name) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(googleCloudProvider, name, '*', '*', account), includeRules)
  }

  @Override
  Set<GoogleSecurityGroup> getAllByAccountAndRegion(boolean includeRules, String account, String region) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(googleCloudProvider, '*', '*', region, account), includeRules)
  }

  @Override
  GoogleSecurityGroup get(String account, String region, String name, String vpcId) {
    // We ignore vpcId here.
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(googleCloudProvider, name, '*', region, account), true)[0]
  }

  Set<GoogleSecurityGroup> getAllMatchingKeyPattern(String pattern, boolean includeRules) {
    loadResults(includeRules, cacheView.filterIdentifiers(SECURITY_GROUPS.ns, pattern))
  }

  Set<GoogleSecurityGroup> loadResults(boolean includeRules, Collection<String> identifiers) {
    def transform = this.&fromCacheData.curry(includeRules)
    def data = cacheView.getAll(SECURITY_GROUPS.ns, identifiers, RelationshipCacheFilter.none())
    def transformed = data.collect(transform)

    return transformed
  }

  GoogleSecurityGroup fromCacheData(boolean includeRules, CacheData cacheData) {
    if (!(cacheData.attributes.firewall.id instanceof BigInteger)) {
      cacheData.attributes.firewall.id = new BigInteger(cacheData.attributes.firewall.id)
    }

    Firewall firewall = objectMapper.convertValue(cacheData.attributes.firewall, Firewall)
    Map<String, String> parts = Keys.parse(googleCloudProvider, cacheData.id)

    return convertToGoogleSecurityGroup(includeRules, firewall, parts.account, parts.region)
  }

  private GoogleSecurityGroup convertToGoogleSecurityGroup(boolean includeRules, Firewall firewall, String account, String region) {
    List<Rule> inboundRules = includeRules ? buildInboundIpRangeRules(firewall) : []

    new GoogleSecurityGroup(
      type: googleCloudProvider.id,
      id: firewall.name,
      name: firewall.name,
      description: firewall.description,
      accountName: account,
      region: region,
      network: getLocalName(firewall.network),
      targetTags: firewall.targetTags,
      inboundRules: inboundRules
    )
  }

  private List<Rule> buildInboundIpRangeRules(Firewall firewall) {
    List<IpRangeRule> rangeRules = []
    List<AddressableRange> sourceRanges = firewall.sourceRanges?.collect { sourceRange ->
      def rangeParts = sourceRange.split("/") as List

      // A sourceRange may in fact be just a single ip address.
      if (rangeParts.size == 1) {
        rangeParts << "32"
      }

      new AddressableRange(ip: rangeParts[0], cidr: "/${rangeParts[1]}")
    }

    // Build a map from protocol to Allowed's so we can group all the ranges for a particular protocol.
    def protocolToAllowedsMap = [:].withDefault { new HashSet<Firewall.Allowed>() }

    firewall.allowed?.each { def allowed ->
      protocolToAllowedsMap[allowed.IPProtocol] << allowed
    }

    protocolToAllowedsMap.each { String ipProtocol, Set<Firewall.Allowed> allowedSet ->
      SortedSet<Rule.PortRange> portRanges = [] as SortedSet

      allowedSet.each { allowed ->
        // Each port must be either an integer or a range.
        allowed.ports?.each { port ->
          def portRangeParts = port.split("-")

          if (portRangeParts) {
            Rule.PortRange portRange = new Rule.PortRange(startPort: new Integer(portRangeParts[0]))

            if (portRangeParts.length == 2) {
              portRange.endPort = new Integer(portRangeParts[1])
            } else {
              portRange.endPort = portRange.startPort
            }

            portRanges << portRange
          }
        }
      }

      // If ports are not specified, connections through any port are allowed.
      if (!portRanges && ["tcp", "udp", "sctp"].contains(ipProtocol)) {
        portRanges << new Rule.PortRange(startPort: 1, endPort: 65535)
      }

      if (sourceRanges) {
        sourceRanges.each { sourceRange ->
          rangeRules.add(new IpRangeRule(range: sourceRange, portRanges: portRanges, protocol: ipProtocol))
        }
      } else {
        // TODO(duftler): Add support for sourceTags.
        rangeRules.add(new IpRangeRule(
          range: new AddressableRange(ip: "", cidr: ""), portRanges: portRanges, protocol: ipProtocol))
      }
    }

    rangeRules.sort()
  }

  private String getLocalName(String fullUrl) {
    if (!fullUrl) {
      return fullUrl
    }

    def urlParts = fullUrl.split("/")

    return urlParts[urlParts.length - 1]
  }
}
