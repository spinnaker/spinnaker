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
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.GoogleSecurityGroup
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.model.AddressableRange
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider
import com.netflix.spinnaker.clouddriver.model.securitygroups.IpRangeRule
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.SECURITY_GROUPS

@Slf4j
@Component
class GoogleSecurityGroupProvider implements SecurityGroupProvider<GoogleSecurityGroup> {

  private final AccountCredentialsProvider accountCredentialsProvider
  private final Cache cacheView
  private final ObjectMapper objectMapper

  final String cloudProvider = GoogleCloudProvider.ID

  @Autowired
  GoogleSecurityGroupProvider(AccountCredentialsProvider accountCredentialsProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.accountCredentialsProvider = accountCredentialsProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  Set<GoogleSecurityGroup> getAll(boolean includeRules) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', '*', '*', '*'), includeRules)
  }

  @Override
  Set<GoogleSecurityGroup> getAllByRegion(boolean includeRules, String region) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', '*', region, '*'), includeRules)
  }

  @Override
  Set<GoogleSecurityGroup> getAllByAccount(boolean includeRules, String account) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', '*', '*', account), includeRules)
  }

  @Override
  Set<GoogleSecurityGroup> getAllByAccountAndName(boolean includeRules, String account, String name) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(name, '*', '*', account), includeRules)
  }

  @Override
  Set<GoogleSecurityGroup> getAllByAccountAndRegion(boolean includeRules, String account, String region) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', '*', region, account), includeRules)
  }

  @Override
  GoogleSecurityGroup get(String account, String region, String name, String vpcId) {
    // We ignore vpcId here.
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(name, '*', region, account), true)[0]
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
    Map firewall = cacheData.attributes.firewall
    Map<String, String> parts = Keys.parse(cacheData.id)

    return convertToGoogleSecurityGroup(includeRules, firewall, parts.account, parts.region)
  }

  private GoogleSecurityGroup convertToGoogleSecurityGroup(boolean includeRules, Map firewall, String account, String region) {
    List<Rule> inboundRules = includeRules ? buildInboundIpRangeRules(firewall) : []

    new GoogleSecurityGroup(
      id: deriveResourceId(account, firewall.selfLink),
      name: firewall.name,
      description: firewall.description,
      accountName: account,
      region: region,
      network: deriveResourceId(account, firewall.network),
      selfLink: firewall.selfLink,
      sourceTags: firewall.sourceTags,
      targetTags: firewall.targetTags,
      sourceServiceAccounts: firewall.sourceServiceAccounts,
      targetServiceAccounts: firewall.targetServiceAccounts,
      inboundRules: inboundRules
    )
  }

  private List<Rule> buildInboundIpRangeRules(Map firewall) {
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
    def protocolToAllowedsMap = [:].withDefault { new HashSet<Map>() }

    firewall.allowed?.each { def allowed ->
      protocolToAllowedsMap[allowed.IPProtocol] << allowed
    }

    protocolToAllowedsMap.each { String ipProtocol, Set<Map> allowedSet ->
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
        rangeRules.add(new IpRangeRule(
          range: new AddressableRange(ip: "", cidr: ""), portRanges: portRanges, protocol: ipProtocol))
      }
    }

    return rangeRules.sort()
  }

  /**
   * Calculates security group names that match account, networkName, and tags
   * @param account - GCE account name.
   * @param securityGroups - Set of server groups to filter.
   * @param tags - GCE network tags to filter security groups by.
   * @param networkName - GCE network name.
   * @return Security group names that match account, networkName, and network tags.
   */
  static List<String> getMatchingSecurityGroupNames(String account,
                                                    Set<GoogleSecurityGroup> securityGroups,
                                                    Set<String> tags,
                                                    String networkName) {
    tags = tags ?: [] as Set
    securityGroups?.findResults { GoogleSecurityGroup securityGroup ->
      def accountAndNetworkMatch = securityGroup.accountName == account && securityGroup.network == networkName
      if (!accountAndNetworkMatch) {
        return null
      }

      boolean hasTargetTags = securityGroup.targetTags
      def targetTagsInCommon = []
      if (hasTargetTags) {
        targetTagsInCommon = (securityGroup.targetTags).intersect(tags)
      }

      // TODO(jacobkiefer): GCE firewall rules can target service accounts. This is a stop-gap solution to fix
      // a bug where security groups targeting service accounts are always returned. We should fully support those
      // security group configurations in the future.
      boolean hasTargetServiceAccounts = securityGroup.targetServiceAccounts
      // Firewall rules can apply to all instances, in which case neither tags nor service accounts are present.
      boolean isDefaultFirewallRule = !hasTargetTags && !hasTargetServiceAccounts

      (isDefaultFirewallRule || targetTagsInCommon) ? securityGroup.name : null
    } ?: []
  }

  private String deriveResourceId(String account, String resourceLink) {
    def accountCredentials = accountCredentialsProvider.getCredentials(account)

    if (!(accountCredentials instanceof GoogleNamedAccountCredentials)) {
      throw new IllegalArgumentException("Invalid credentials: $account")
    }

    def project = accountCredentials.project
    def firewallProject = GCEUtil.deriveProjectId(resourceLink)
    def firewallId = GCEUtil.getLocalName(resourceLink)

    if (firewallProject != project) {
      firewallId = "$firewallProject/$firewallId"
    }

    return firewallId
  }
}
