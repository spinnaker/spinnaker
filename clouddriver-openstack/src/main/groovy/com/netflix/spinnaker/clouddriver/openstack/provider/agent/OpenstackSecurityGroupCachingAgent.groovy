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

package com.netflix.spinnaker.clouddriver.openstack.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.model.AddressableRange
import com.netflix.spinnaker.clouddriver.model.securitygroups.IpRangeRule
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import com.netflix.spinnaker.clouddriver.model.securitygroups.SecurityGroupRule
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackSecurityGroup
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import groovy.util.logging.Slf4j
import org.openstack4j.model.compute.SecGroupExtension

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SECURITY_GROUPS
import static com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider.ATTRIBUTES

@Slf4j
class OpenstackSecurityGroupCachingAgent extends AbstractOpenstackCachingAgent {

  final Set<AgentDataType> providedDataTypes = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(SECURITY_GROUPS.ns)
  ] as Set)
  final String agentType = "${account.name}/${region}/${OpenstackSecurityGroupCachingAgent.simpleName}"
  final ObjectMapper objectMapper

  OpenstackSecurityGroupCachingAgent(OpenstackNamedAccountCredentials account, String region, ObjectMapper objectMapper) {
    super(account, region)
    this.objectMapper = objectMapper
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    /*
     * Get security groups and map the names to the ids for a later lookup. Since there is a possibility
     * that there are duplicate security groups by name, the lookup is to a list of ids.
     */
    List<SecGroupExtension> securityGroups = clientProvider.getSecurityGroups(region)
    Map<String, List<String>> namesToIds = [:].withDefault {[]}
    securityGroups.each { namesToIds[it.name] << it.id }

    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder()
    securityGroups.each { securityGroup ->
      log.debug("Caching security group for account $accountName in region $region: $securityGroup")

      List<Rule> inboundRules = securityGroup.rules.collect { rule ->
        // The Openstack4J library doesn't put a type on the rule, instead, it includes a range object with a null cidr
        rule.range?.cidr ? buildIpRangeRule(rule) : buildSecurityGroupRule(rule, namesToIds.get(rule.group.name))
      }

      OpenstackSecurityGroup openstackSecurityGroup = new OpenstackSecurityGroup(id: securityGroup.id,
        accountName: accountName,
        region: region,
        name: securityGroup.name,
        description: securityGroup.description,
        inboundRules: inboundRules
      )

      String instanceKey = Keys.getSecurityGroupKey(securityGroup.name, securityGroup.id, accountName, region)
      cacheResultBuilder.namespace(SECURITY_GROUPS.ns).keep(instanceKey).with {
        attributes = objectMapper.convertValue(openstackSecurityGroup, ATTRIBUTES)
      }
    }

    log.info("Caching ${cacheResultBuilder.namespace(SECURITY_GROUPS.ns).keepSize()} items in ${agentType}")
    cacheResultBuilder.build()
  }

  /**
   * Build a security group rule the references another security group.
   *
   * This will Look up the referenced security group by name. This lookup may fail if multiple security groups
   * by name are found or no security groups with that name can be found.
   */
  private SecurityGroupRule buildSecurityGroupRule(SecGroupExtension.Rule rule, List<String> possibleSecurityGroupReferences) {

    String id = null
    if (possibleSecurityGroupReferences.isEmpty()) {
      log.warn("Could not find any security groups by name ${rule.group.name} in account ${accountName}")
    } else if (possibleSecurityGroupReferences.size() > 1) {
      log.warn("Found too many security groups by name ${rule.group.name} in account ${accountName}")
    } else {
      id = possibleSecurityGroupReferences[0]
    }

    def portRange = new Rule.PortRange(startPort: rule.fromPort, endPort: rule.toPort)
    def securityGroup = new OpenstackSecurityGroup(
      name: rule.group.name,
      type: OpenstackCloudProvider.ID,
      accountName: accountName,
      region: region,
      id: id
    )
    new SecurityGroupRule(protocol: rule.IPProtocol.value(),
      portRanges: [portRange] as SortedSet,
      securityGroup: securityGroup
    )
  }

  private IpRangeRule buildIpRangeRule(SecGroupExtension.Rule rule) {
    def portRange = new Rule.PortRange(startPort: rule.fromPort, endPort: rule.toPort)
    def addressableRange = buildAddressableRangeFromCidr(rule.range.cidr)
    new IpRangeRule(protocol: rule.IPProtocol.value(),
      portRanges: [portRange] as SortedSet,
      range: addressableRange
    )
  }

  private AddressableRange buildAddressableRangeFromCidr(String cidr) {
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
