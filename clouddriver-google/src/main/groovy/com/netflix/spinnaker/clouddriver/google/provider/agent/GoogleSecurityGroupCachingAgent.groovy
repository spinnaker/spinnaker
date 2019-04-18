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

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.model.Firewall
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import groovy.transform.Canonical
import groovy.util.logging.Slf4j

import java.util.concurrent.TimeUnit

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.ON_DEMAND
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.SECURITY_GROUPS

@Slf4j
class GoogleSecurityGroupCachingAgent extends AbstractGoogleCachingAgent implements OnDemandAgent {

  String agentType = "${accountName}/global/${GoogleSecurityGroupCachingAgent.simpleName}"
  String onDemandAgentType = "${agentType}-OnDemand"

  final OnDemandMetricsSupport metricsSupport

  final Set<AgentDataType> providedDataTypes = [
    AUTHORITATIVE.forType(SECURITY_GROUPS.ns)
  ] as Set

  GoogleSecurityGroupCachingAgent(String clouddriverUserAgentApplicationName,
                                  GoogleNamedAccountCredentials credentials,
                                  ObjectMapper objectMapper,
                                  Registry registry) {
    super(clouddriverUserAgentApplicationName,
          credentials,
          objectMapper,
          registry)
    this.metricsSupport = new OnDemandMetricsSupport(
      registry,
      this,
      "${GoogleCloudProvider.ID}:${OnDemandAgent.OnDemandType.SecurityGroup}")
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    type == OnDemandAgent.OnDemandType.SecurityGroup && cloudProvider == GoogleCloudProvider.ID
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (!data.containsKey("securityGroupName") || data.account != accountName || data.region != "global") {
      return null
    }

    Firewall firewall = metricsSupport.readData {
      getFirewall(data.securityGroupName as String)
    }

    def securityGroupKey
    Collection<String> identifiers = []

    if (firewall) {
      securityGroupKey = Keys.getSecurityGroupKey(
        firewall.name,
        deriveFirewallId(firewall),
        "global",
        accountName)
    } else {
      securityGroupKey = Keys.getSecurityGroupKey(
        data.securityGroupName,
        data.securityGroupName,
        "global",
        accountName)

      // TODO(duftler): Is this right? Seems like this should use a wildcard.
      // No firewall was found, so need to find identifiers for all firewalls in the region.
      identifiers = providerCache.filterIdentifiers(SECURITY_GROUPS.ns, securityGroupKey)
    }

    def cacheResultBuilder = new CacheResultBuilder(startTime: Long.MAX_VALUE)
    CacheResult result = metricsSupport.transformData {
      buildCacheResult(cacheResultBuilder, firewall ? [firewall] : [])
    }

    if (result.cacheResults.values().flatten().empty) {
      // Avoid writing an empty onDemand cache record (instead delete any that may have previously existed).
      providerCache.evictDeletedItems(ON_DEMAND.ns, identifiers)
    } else {
      metricsSupport.onDemandStore {
        def cacheData = new DefaultCacheData(
          securityGroupKey,
          TimeUnit.MINUTES.toSeconds(10) as Integer, // ttl
          [
            cacheTime     : System.currentTimeMillis(),
            cacheResults  : objectMapper.writeValueAsString(result.cacheResults),
            processedCount: 0,
            processedTime : null
          ],
          [:]
        )

        providerCache.putCacheData(ON_DEMAND.ns, cacheData)
      }
    }

    Map<String, Collection<String>> evictions = [:].withDefault {_ -> []}
    if (!firewall) {
      evictions[SECURITY_GROUPS.ns].addAll(identifiers)
    }

    log.debug("On demand cache refresh succeeded. Data: ${data}. Added ${firewall ? 1 : 0} items to the cache.")

    return new OnDemandAgent.OnDemandResult(
      sourceAgentType: getOnDemandAgentType(),
      cacheResult: result,
      evictions: evictions,
      // Do not include "authoritativeTypes" here, as it will result in all other cache entries getting deleted!
    )
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    def keyOwnedByThisAgent = { Map<String, String> parsedKey ->
      parsedKey && parsedKey.account == accountName && parsedKey.region == "global"
    }

    def keys = providerCache.getIdentifiers(ON_DEMAND.ns).findAll { String key ->
      keyOwnedByThisAgent(Keys.parse(key))
    }

    return providerCache.getAll(ON_DEMAND.ns, keys).collect { CacheData cacheData ->
      def details = Keys.parse(cacheData.id)

      return [
          details       : details,
          moniker       : convertOnDemandDetails(details),
          cacheTime     : cacheData.attributes.cacheTime,
          processedCount: cacheData.attributes.processedCount,
          processedTime : cacheData.attributes.processedTime
      ]
    }
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    def cacheResultBuilder = new CacheResultBuilder(startTime: System.currentTimeMillis())

    List<Firewall> firewalls = getFirewalls()
    def firewallKeys = firewalls.collect { Keys.getSecurityGroupKey(it.getName(), deriveFirewallId(it), "global", accountName) }

    providerCache.getAll(ON_DEMAND.ns, firewallKeys).each { CacheData cacheData ->
      // Ensure that we don't overwrite data that was inserted by the `handle` method while we retrieved the
      // firewalls. Furthermore, cache data that hasn't been moved to the proper namespace needs to be
      // updated in the ON_DEMAND cache, so don't evict data without a processedCount > 0.
      if (cacheData.attributes.cacheTime < cacheResultBuilder.startTime && cacheData.attributes.processedCount > 0) {
        cacheResultBuilder.onDemand.toEvict << cacheData.id
      } else {
        cacheResultBuilder.onDemand.toKeep[cacheData.id] = cacheData
      }
    }

    CacheResult cacheResults = buildCacheResult(cacheResultBuilder, firewalls)

    cacheResults.cacheResults[ON_DEMAND.ns].each { CacheData cacheData ->
      cacheData.attributes.processedTime = System.currentTimeMillis()
      cacheData.attributes.processedCount = (cacheData.attributes.processedCount ?: 0) + 1
    }

    return cacheResults
  }

  List<Firewall> getFirewalls(String onDemandSecurityGroupName = null) {
    if (onDemandSecurityGroupName) {
      return [timeExecute(compute.firewalls().get(project, onDemandSecurityGroupName),
                          "compute.firewalls.get", TAG_SCOPE, SCOPE_GLOBAL
      )] as List<Firewall>
    } else {
      List<Firewall> firewalls = timeExecute(compute.firewalls().list(project),
                                             "compute.firewalls.list", TAG_SCOPE, SCOPE_GLOBAL).items as List

      if (xpnHostProject) {
        List<Firewall> hostFirewalls = timeExecute(compute.firewalls().list(xpnHostProject),
                                                   "compute.firewalls.list", TAG_SCOPE, SCOPE_GLOBAL).items as List

        firewalls = (firewalls ?: []) + (hostFirewalls ?: [])
      }

      return firewalls
    }
  }

  Firewall getFirewall(String onDemandSecurityGroupName) {
    def firewalls = getFirewalls(onDemandSecurityGroupName)

    return firewalls ? firewalls.first() : null
  }

  private CacheResult buildCacheResult(CacheResultBuilder cacheResultBuilder, List<Firewall> firewalls) {
    log.debug("Describing items in ${agentType}")

    firewalls.each { Firewall firewall ->
      def securityGroupKey = Keys.getSecurityGroupKey(firewall.getName(),
                                                      deriveFirewallId(firewall),
                                                      "global",
                                                      accountName)

      if (shouldUseOnDemandData(cacheResultBuilder, securityGroupKey)) {
        moveOnDemandDataToNamespace(cacheResultBuilder, firewall)
      } else {
        cacheResultBuilder.namespace(SECURITY_GROUPS.ns).keep(securityGroupKey).with {
          attributes = [firewall: firewall]
        }
      }
    }

    log.debug("Caching ${cacheResultBuilder.namespace(SECURITY_GROUPS.ns).keepSize()} security groups in ${agentType}")
    log.debug "Caching ${cacheResultBuilder.onDemand.toKeep.size()} onDemand entries in ${agentType}"
    log.debug "Evicting ${cacheResultBuilder.onDemand.toEvict.size()} onDemand entries in ${agentType}"

    return cacheResultBuilder.build()
  }

  static boolean shouldUseOnDemandData(CacheResultBuilder cacheResultBuilder, String securityGroupKey) {
    CacheData cacheData = cacheResultBuilder.onDemand.toKeep[securityGroupKey]

    return cacheData ? cacheData.attributes.cacheTime >= cacheResultBuilder.startTime : false
  }

  void moveOnDemandDataToNamespace(CacheResultBuilder cacheResultBuilder,
                                   Firewall firewall) {
    def securityGroupKey = Keys.getSecurityGroupKey(
      firewall.getName(),
      deriveFirewallId(firewall),
      "global",
      accountName)
    Map<String, List<MutableCacheData>> onDemandData = objectMapper.readValue(
      cacheResultBuilder.onDemand.toKeep[securityGroupKey].attributes.cacheResults as String,
      new TypeReference<Map<String, List<MutableCacheData>>>() {})
    onDemandData.each { String namespace, List<MutableCacheData> cacheDatas ->
      if (namespace != 'onDemand') {
        cacheDatas.each { MutableCacheData cacheData ->
          cacheResultBuilder.namespace(namespace).keep(cacheData.id).with { it ->
            it.attributes = cacheData.attributes
            it.relationships = Utils.mergeOnDemandCacheRelationships(cacheData.relationships, it.relationships)
          }
          cacheResultBuilder.onDemand.toKeep.remove(cacheData.id)
        }
      }
    }
  }

  private String deriveFirewallId(Firewall firewall) {
    def firewallProject = GCEUtil.deriveProjectId(firewall.selfLink)
    def firewallId = GCEUtil.getLocalName(firewall.selfLink)

    if (firewallProject != project) {
      firewallId = "$firewallProject/$firewallId"
    }

    return firewallId
  }

  // TODO(lwander) this was taken from the netflix cluster caching, and should probably be shared between all providers.
  @Canonical
  static class MutableCacheData implements CacheData {
    String id
    int ttlSeconds = -1
    Map<String, Object> attributes = [:]
    Map<String, Collection<String>> relationships = [:].withDefault { [] as Set }
  }
}
