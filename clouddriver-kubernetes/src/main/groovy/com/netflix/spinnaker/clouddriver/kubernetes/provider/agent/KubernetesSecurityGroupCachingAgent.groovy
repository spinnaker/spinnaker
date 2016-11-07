/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.kubernetes.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.cache.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.provider.KubernetesProvider
import com.netflix.spinnaker.clouddriver.kubernetes.provider.view.MutableCacheData
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import groovy.util.logging.Slf4j
import io.fabric8.kubernetes.api.model.extensions.Ingress

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE

@Slf4j
class KubernetesSecurityGroupCachingAgent extends KubernetesCachingAgent implements OnDemandAgent {

  private static final OnDemandAgent.OnDemandType ON_DEMAND_TYPE = OnDemandAgent.OnDemandType.SecurityGroup

  final OnDemandMetricsSupport metricsSupport

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
      INFORMATIVE.forType(Keys.Namespace.LOAD_BALANCERS.ns),
      AUTHORITATIVE.forType(Keys.Namespace.SECURITY_GROUPS.ns),
  ] as Set)

  KubernetesSecurityGroupCachingAgent(String accountName,
                                      KubernetesCredentials credentials,
                                      ObjectMapper objectMapper,
                                      int agentIndex,
                                      int agentCount,
                                      Registry registry) {
    super(accountName, objectMapper, credentials, agentIndex, agentCount)
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, "$kubernetesCloudProvider.id:$ON_DEMAND_TYPE")
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  String getOnDemandAgentType() {
    "${getAgentType()}-OnDemand"
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (!data.containsKey("securityGroupName")) {
      return null
    }

    if (data.account != accountName) {
      return null
    }

    reloadNamespaces()
    String namespace = data.region
    if (namespaces.contains(namespace)) {
      return null
    }

    def securityGroupName = data.securityGroupName.toString()

    Ingress ingress = metricsSupport.readData {
      credentials.apiAdaptor.getIngress(namespace, securityGroupName)
    }

    CacheResult result = metricsSupport.transformData {
      buildCacheResult([ingress], [:], [], Long.MAX_VALUE)
    }

    def jsonResult = objectMapper.writeValueAsString(result.cacheResults)

    if (result.cacheResults.values().flatten().isEmpty()) {
      // Avoid writing an empty onDemand cache record (instead delete any that may have previously existed).
      providerCache.evictDeletedItems(Keys.Namespace.ON_DEMAND.ns, [Keys.getSecurityGroupKey(accountName, namespace, securityGroupName)])
    } else {
      metricsSupport.onDemandStore {
        def cacheData = new DefaultCacheData(
            Keys.getSecurityGroupKey(accountName, namespace, securityGroupName),
            10 * 60, // ttl is 10 minutes
            [
                cacheTime: System.currentTimeMillis(),
                cacheResults: jsonResult,
                processedCount: 0,
                processedTime: null
            ],
            [:]
        )

        providerCache.putCacheData(Keys.Namespace.ON_DEMAND.ns, cacheData)
      }
    }

    // Evict this security group if it no longer exists.
    Map<String, Collection<String>> evictions = ingress ? [:] : [
        (Keys.Namespace.SECURITY_GROUPS.ns): [
            Keys.getSecurityGroupKey(accountName, namespace, securityGroupName)
        ]
    ]

    log.info("On demand cache refresh (data: ${data}) succeeded.")

    return new OnDemandAgent.OnDemandResult(
        sourceAgentType: getOnDemandAgentType(),
        cacheResult: result,
        evictions: evictions
    )
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    def keys = providerCache.getIdentifiers(Keys.Namespace.ON_DEMAND.ns)
    keys = keys.findResults {
      def parse = Keys.parse(it)
      if (parse && namespaces.contains(parse.namespace) && parse.account == accountName) {
        return it
      } else {
        return null
      }
    }

    providerCache.getAll(Keys.Namespace.ON_DEMAND.ns, keys).collect {
      [
          details  : Keys.parse(it.id),
          cacheTime: it.attributes.cacheTime,
          processedCount: it.attributes.processedCount,
          processedTime: it.attributes.processedTime
      ]
    }
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    ON_DEMAND_TYPE == type && cloudProvider == kubernetesCloudProvider.id
  }

  List<Ingress> loadIngresses() {
    namespaces.collect { String namespace ->
      credentials.apiAdaptor.getIngress(namespace)
    }.flatten() - null
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    Long start = System.currentTimeMillis()
    List<Ingress> ingresses = loadIngresses()

    def evictFromOnDemand = []
    def keepInOnDemand = []

    providerCache.getAll(Keys.Namespace.ON_DEMAND.ns,
        ingresses.collect { Keys.getSecurityGroupKey(accountName, it.metadata.namespace, it.metadata.name) }).each {
      // Ensure that we don't overwrite data that was inserted by the `handle` method while we retrieved the
      // replication controllers. Furthermore, cache data that hasn't been processed needs to be updated in the ON_DEMAND
      // cache, so don't evict data without a processedCount > 0.
      if (it.attributes.cacheTime < start && it.attributes.processedCount > 0) {
        evictFromOnDemand << it
      } else {
        keepInOnDemand << it
      }
    }

    def result = buildCacheResult(ingresses, keepInOnDemand.collectEntries { [(it.id): it] }, evictFromOnDemand*.id, start)

    result.cacheResults[Keys.Namespace.ON_DEMAND.ns].each {
      it.attributes.processedTime = System.currentTimeMillis()
      it.attributes.processedCount = (it.attributes.processedCount ?: 0) + 1
    }

    return result
  }

  private static void cache(Map<String, List<CacheData>> cacheResults, String namespace, Map<String, CacheData> cacheDataById) {
    cacheResults[namespace].each {
      def existingCacheData = cacheDataById[it.id]
      if (!existingCacheData) {
        cacheDataById[it.id] = it
      } else {
        existingCacheData.attributes.putAll(it.attributes)
        it.relationships.each { String relationshipName, Collection<String> relationships ->
          existingCacheData.relationships[relationshipName].addAll(relationships)
        }
      }
    }
  }

  private CacheResult buildCacheResult(List<Ingress> ingresses, Map<String, CacheData> onDemandKeep, List<String> onDemandEvict, Long start) {
    log.info("Describing items in ${agentType}")

    Map<String, MutableCacheData> cachedSecurityGroups = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedLoadBalancers = MutableCacheData.mutableCacheMap()

    for (Ingress ingress : ingresses) {
      if (!ingress) {
        continue
      }

      def namespace = ingress.metadata.namespace

      def onDemandData = onDemandKeep ? onDemandKeep[Keys.getSecurityGroupKey(accountName, namespace, ingress.metadata.name)] : null

      if (onDemandData && onDemandData.attributes.cachetime >= start) {
        Map<String, List<CacheData>> cacheResults = objectMapper.readValue(onDemandData.attributes.cacheResults as String,
            new TypeReference<Map<String, List<MutableCacheData>>>() { })
        cache(cacheResults, Keys.Namespace.SECURITY_GROUPS.ns, cachedSecurityGroups)
      } else {
        def ingressName = ingress.metadata.name
        def securityGroupKey = Keys.getSecurityGroupKey(accountName, namespace, ingressName)

        List<String> loadBalancerKeys = ingress.spec.backend?.serviceName ?
            [Keys.getLoadBalancerKey(accountName, namespace, ingress.spec.backend.serviceName)] : []

        loadBalancerKeys.addAll(ingress.spec.rules?.findResults { rule ->
          rule.http?.paths?.findResults { path ->
            path?.backend?.serviceName ? Keys.getLoadBalancerKey(accountName, namespace, path.backend.serviceName) : null
          }
        }?.flatten() ?: [])

        cachedSecurityGroups[securityGroupKey].with {
          attributes.name = ingressName
          attributes.ingress = ingress
          relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
        }

        loadBalancerKeys.each {
          cachedLoadBalancers[it].with {
            relationships[Keys.Namespace.SECURITY_GROUPS.ns].add(securityGroupKey)
          }
        }
      }
    }

    log.info("Caching ${cachedSecurityGroups.size()} security groups in ${agentType}")

    new DefaultCacheResult([
        (Keys.Namespace.SECURITY_GROUPS.ns): cachedSecurityGroups.values(),
        (Keys.Namespace.LOAD_BALANCERS.ns): cachedLoadBalancers.values(),
        (Keys.Namespace.ON_DEMAND.ns): onDemandKeep.values()
    ],[
        (Keys.Namespace.ON_DEMAND.ns): onDemandEvict,
    ])

  }

  @Override
  String getSimpleName() {
    KubernetesSecurityGroupCachingAgent.simpleName
  }
}

