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
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.model.AddressableRange
import com.netflix.spinnaker.clouddriver.model.securitygroups.IpRangeRule
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import com.netflix.spinnaker.clouddriver.model.securitygroups.SecurityGroupRule
import com.netflix.spinnaker.clouddriver.openstack.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.cache.UnresolvableKeyException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackSecurityGroup
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import groovy.util.logging.Slf4j
import org.openstack4j.model.compute.SecGroupExtension

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SECURITY_GROUPS
import static com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider.ATTRIBUTES
import static com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider.ID
import static com.netflix.spinnaker.clouddriver.cache.OnDemandAgent.OnDemandType.SecurityGroup


@Slf4j
class OpenstackSecurityGroupCachingAgent extends AbstractOpenstackCachingAgent implements OnDemandAgent {

  final Set<AgentDataType> providedDataTypes = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(SECURITY_GROUPS.ns)
  ] as Set)

  final String agentType = "${account.name}/${region}/${OpenstackSecurityGroupCachingAgent.simpleName}"
  final String onDemandAgentType = "${agentType}-OnDemand"
  final OnDemandMetricsSupport metricsSupport
  final ObjectMapper objectMapper

  OpenstackSecurityGroupCachingAgent(final OpenstackNamedAccountCredentials account,
                                     final String region,
                                     final ObjectMapper objectMapper,
                                     final Registry registry) {
    super(account, region)
    this.objectMapper = objectMapper
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, "${ID}:${SecurityGroup}")
  }


  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    type == SecurityGroup && cloudProvider == ID
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    getAllOnDemandCacheByRegionAndAccount(providerCache, accountName, region)
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    /*
     * Get security groups and map the names to the ids for a later lookup. Since there is a possibility
     * that there are duplicate security groups by name, the lookup is to a list of ids.
     */
    List<SecGroupExtension> securityGroups = clientProvider.getSecurityGroups(region)
    List<String> keys = securityGroups.collect{ Keys.getSecurityGroupKey(it.name, it.id, accountName, region) }

    buildLoadDataCache(providerCache, keys) { CacheResultBuilder cacheResultBuilder ->
      buildCacheResult(cacheResultBuilder, securityGroups)
    }
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    log.debug("Handling on-demand cache update; account=${account}, region=${region}, data=${data}")

    if (data.account != accountName) {
      return null
    }

    if (data.region != region) {
      return null
    }

    if (!data.containsKey('securityGroupName')) {
      return null
    }

    String name = data.securityGroupName as String

    SecGroupExtension securityGroup = metricsSupport.readData {
      SecGroupExtension group = null
      try {
        /*
         * Since we only have a name, we need to get all groups and filter by name. Also, since name is unique,
         * ensure there is only security group by this name.
         */
        List<SecGroupExtension> groups = clientProvider.getSecurityGroups(region).findAll { it.name == name }
        if (groups.size() == 1) {
          group = groups.first()
        } else {
          log.warn("Failed to find unique security group with name ${name} in region ${region}")
        }
      } catch (OpenstackProviderException e) {
        //Do nothing ... Exception is thrown if a security group isn't found
        log.debug("Unable to find security group to add to OnDemand cache", e)
      }
      return group
    }

    List<SecGroupExtension> securityGroups = []
    String key = Keys.getSecurityGroupKey(name, '*', accountName, region)
    if (securityGroup) {
      securityGroups = [securityGroup]
      key = Keys.getSecurityGroupKey(name, securityGroup.id, accountName, region)
    }

    CacheResult cacheResult = metricsSupport.transformData {
      buildCacheResult(new CacheResultBuilder(startTime: Long.MAX_VALUE), securityGroups)
    }

    String namespace = SECURITY_GROUPS.ns
    String resolvedKey = null
    try {
      resolvedKey = resolveKey(providerCache, namespace, key)
      processOnDemandCache(cacheResult, objectMapper, metricsSupport, providerCache, resolvedKey)
    } catch(UnresolvableKeyException e) {
      log.info("Security group ${name} is not resolvable", e)
    }

    log.info("On demand cache refresh succeeded. Data: ${data}")

    buildOnDemandCache(securityGroup, onDemandAgentType, cacheResult, namespace, resolvedKey)
  }

  protected CacheResult buildCacheResult(CacheResultBuilder cacheResultBuilder, List<SecGroupExtension> securityGroups) {
    Map<String, List<String>> namesToIds = [:].withDefault {[]}
    securityGroups.each { namesToIds[it.name] << it.id }

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

      String key = Keys.getSecurityGroupKey(securityGroup.name, securityGroup.id, accountName, region)

      if (shouldUseOnDemandData(cacheResultBuilder, key)) {
        moveOnDemandDataToNamespace(objectMapper, typeReference, cacheResultBuilder, key)
      } else {
        cacheResultBuilder.namespace(SECURITY_GROUPS.ns).keep(key).with {
          attributes = objectMapper.convertValue(openstackSecurityGroup, ATTRIBUTES)
        }
      }
    }

    log.info("Caching ${cacheResultBuilder.namespace(SECURITY_GROUPS.ns).keepSize()} items in ${agentType}")
    log.info("Caching ${cacheResultBuilder.onDemand.toKeep.size()} onDemand entries in ${agentType}")
    log.info("Evicting ${cacheResultBuilder.onDemand.toEvict.size()} onDemand entries in ${agentType}")

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
      type: ID,
      accountName: accountName,
      region: region,
      id: id
    )
    new SecurityGroupRule(protocol: rule.IPProtocol.value(),
      portRanges: [portRange] as SortedSet,
      securityGroup: securityGroup
    )
  }

  /**
   * Build a security group based on a IP range (cidr)
   */
  private IpRangeRule buildIpRangeRule(SecGroupExtension.Rule rule) {
    def portRange = new Rule.PortRange(startPort: rule.fromPort, endPort: rule.toPort)
    def addressableRange = buildAddressableRangeFromCidr(rule.range.cidr)
    new IpRangeRule(protocol: rule.IPProtocol.value(),
      portRanges: [portRange] as SortedSet,
      range: addressableRange
    )
  }

  /**
   * Builds an {@link AddressableRange} from a CIDR string.
   */
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
