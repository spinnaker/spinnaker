/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.appengine.v1.model.Service
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.appengine.AppengineCloudProvider
import com.netflix.spinnaker.clouddriver.appengine.cache.Keys
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineLoadBalancer
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent.OnDemandResult
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.view.MutableCacheData
import groovy.util.logging.Slf4j

import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.appengine.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.appengine.cache.Keys.Namespace.ON_DEMAND

@Slf4j
class AppengineLoadBalancerCachingAgent extends AbstractAppengineCachingAgent implements OnDemandAgent {
  final String category = "loadBalancer"

  final OnDemandMetricsSupport metricsSupport

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(LOAD_BALANCERS.ns),
  ] as Set)

  String agentType = "${accountName}/${AppengineLoadBalancerCachingAgent.simpleName}"

  @Override
  String getSimpleName() {
    AppengineLoadBalancerCachingAgent.simpleName
  }

  @Override
  String getOnDemandAgentType() {
    "${agentType}-OnDemand"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  AppengineLoadBalancerCachingAgent(String accountName,
                                    AppengineNamedAccountCredentials credentials,
                                    ObjectMapper objectMapper,
                                    Registry registry) {
    super(accountName, objectMapper, credentials)
    metricsSupport = new OnDemandMetricsSupport(
      registry,
      this,
      "$AppengineCloudProvider.ID:$OnDemandAgent.OnDemandType.LoadBalancer")
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    type == OnDemandAgent.OnDemandType.LoadBalancer && cloudProvider == AppengineCloudProvider.ID
  }

  @Override
  OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (!data.containsKey("loadBalancerName") || data.account != accountName) {
      return null
    }

    def loadBalancerName = data.loadBalancerName.toString()

    if (shouldIgnoreLoadBalancer(loadBalancerName)) {
      return null
    }

    Service service = metricsSupport.readData {
      loadService(loadBalancerName)
    }

    CacheResult result = metricsSupport.transformData {
      buildCacheResult([service], [:], [], Long.MAX_VALUE)
    }

    def jsonResult = objectMapper.writeValueAsString(result.cacheResults)
    def loadBalancerKey = Keys.getLoadBalancerKey(accountName, loadBalancerName)
    if (result.cacheResults.values().flatten().isEmpty()) {
      providerCache.evictDeletedItems(ON_DEMAND.ns, [loadBalancerKey])
    } else {
      metricsSupport.onDemandStore {
        def cacheData = new DefaultCacheData(
          loadBalancerKey,
          TimeUnit.MINUTES.toSeconds(10) as int,
          [
            cacheTime: System.currentTimeMillis(),
            cacheResults: jsonResult,
            processedCount: 0,
            processedTime: 0
          ],
          [:]
        )

        providerCache.putCacheData(ON_DEMAND.ns, cacheData)
      }
    }

    Map<String, Collection<String>> evictions = service ? [:] : [(LOAD_BALANCERS.ns): [loadBalancerKey]]

    log.info "On demand cache refresh (data: ${data}) succeeded."

    return new OnDemandAgent.OnDemandResult(
      sourceAgentType: getOnDemandAgentType(),
      cacheResult: result,
      evictions: evictions
    )
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    Long start = System.currentTimeMillis()
    List<Service> services = loadServices().stream().filter { !shouldIgnoreLoadBalancer(it.getId()) }.collect(Collectors.toList())
    List<CacheData> evictFromOnDemand = []
    List<CacheData> keepInOnDemand = []

    def loadBalancerKeys = services.collect {
      Keys.getLoadBalancerKey(credentials.name, it.getId())
    }

    providerCache.getAll(ON_DEMAND.ns, loadBalancerKeys).each { CacheData onDemandEntry ->
      if (onDemandEntry.attributes.cacheTime < start && onDemandEntry.attributes.processedCount > 0) {
        evictFromOnDemand << onDemandEntry
      } else {
        keepInOnDemand << onDemandEntry
      }
    }

    def onDemandMap = keepInOnDemand.collectEntries { CacheData onDemandEntry ->
      [(onDemandEntry.id): onDemandEntry]
    }
    def result = buildCacheResult(services, onDemandMap, evictFromOnDemand*.id, start)
    result.cacheResults[ON_DEMAND.ns].each { CacheData onDemandEntry ->
      onDemandEntry.attributes.processedTime = System.currentTimeMillis()
      onDemandEntry.attributes.processedCount = (onDemandEntry.attributes.processedCount ?: 0) + 1
    }

    return result
  }

  CacheResult buildCacheResult(List<Service> services,
                               Map<String, CacheData> onDemandKeep,
                               List<String> onDemandEvict,
                               Long start) {
    log.info "Describing items in $agentType"

    Map<String, MutableCacheData> cachedLoadBalancers = MutableCacheData.mutableCacheMap()

    services.each { Service service ->
      def loadBalancerKey = Keys.getLoadBalancerKey(accountName, service.getId())
      def loadBalancerName = service.getId()
      def onDemandData = onDemandKeep ? onDemandKeep[loadBalancerKey] : null

      if (onDemandData && onDemandData.attributes.cacheTime >= start) {
        Map<String, List<CacheData>> cacheResults = objectMapper
          .readValue(onDemandData.attributes.cacheResults as String,
                     new TypeReference<Map<String, List<MutableCacheData>>>() {})

        cache(cacheResults, LOAD_BALANCERS.ns, cachedLoadBalancers)
      } else {
        cachedLoadBalancers[loadBalancerKey].with {
          attributes.name = loadBalancerName
          attributes.loadBalancer = new AppengineLoadBalancer(service,
                                                              accountName,
                                                              credentials.region)
        }
      }
    }

    log.info("Caching ${cachedLoadBalancers.size()} load balancers in ${agentType}")

    return new DefaultCacheResult([
      (LOAD_BALANCERS.ns): cachedLoadBalancers.values(),
      (ON_DEMAND.ns): onDemandKeep.values()
    ], [
      (ON_DEMAND.ns): onDemandEvict
    ])
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    def keys = providerCache.getIdentifiers(ON_DEMAND.ns)
    keys = keys.findResults {
      def parse = Keys.parse(it)
      if (parse && parse.account == accountName) {
        return it
      } else {
        return null
      }
    }

    return providerCache.getAll(ON_DEMAND.ns, keys).collect {
      def details = Keys.parse(it.id)
      return [
          details       : details,
          moniker       : convertOnDemandDetails(details),
          cacheTime     : it.attributes.cacheTime,
          processedCount: it.attributes.processedCount,
          processedTime : it.attributes.processedTime
      ]
    }
  }

  Service loadService(String loadBalancerName) {
    def project = credentials.project
    return credentials.appengine.apps().services().get(project, loadBalancerName).execute()
  }

  List<Service> loadServices() {
    def project = credentials.project
    return credentials.appengine.apps().services().list(project).execute().getServices()
  }
}
