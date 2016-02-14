/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerNotFoundException
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import groovy.util.logging.Slf4j
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.aws.data.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.aws.data.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.aws.data.Keys.Namespace.ON_DEMAND

class LoadBalancerCachingAgent implements CachingAgent, OnDemandAgent, AccountAware, DriftMetric {
  final Logger log = LoggerFactory.getLogger(getClass())
  @Deprecated
  private static final String LEGACY_ON_DEMAND_TYPE = 'AmazonLoadBalancer'

  private static final String ON_DEMAND_TYPE = 'LoadBalancer'

  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  private static final Collection<AgentDataType> types = Collections.unmodifiableCollection([
    AUTHORITATIVE.forType(LOAD_BALANCERS.ns),
    INFORMATIVE.forType(INSTANCES.ns)
  ])

  @Override
  String getProviderName() {
    AwsProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${LoadBalancerCachingAgent.simpleName}"
  }

  @Override
  String getAccountName() {
    account.name
  }

  @Override
  String getOnDemandAgentType() {
    "${getAgentType()}-OnDemand"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  final AmazonCloudProvider amazonCloudProvider
  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper
  final Registry registry
  final OnDemandMetricsSupport metricsSupport

  LoadBalancerCachingAgent(AmazonCloudProvider amazonCloudProvider,
                           AmazonClientProvider amazonClientProvider,
                           NetflixAmazonCredentials account,
                           String region,
                           ObjectMapper objectMapper,
                           Registry registry) {
    this.amazonCloudProvider = amazonCloudProvider
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    this.registry = registry
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, amazonCloudProvider.id + ":" + ON_DEMAND_TYPE)
  }

  static class MutableCacheData implements CacheData {
    final String id
    int ttlSeconds = -1
    final Map<String, Object> attributes = [:]
    final Map<String, Collection<String>> relationships = [:].withDefault { [] as Set }
    public MutableCacheData(String id) {
      this.id = id
    }

    @JsonCreator
    public MutableCacheData(@JsonProperty("id") String id,
                            @JsonProperty("attributes") Map<String, Object> attributes,
                            @JsonProperty("relationships") Map<String, Collection<String>> relationships) {
      this(id);
      this.attributes.putAll(attributes);
      this.relationships.putAll(relationships);
    }
  }

  @Override
  boolean handles(String type) {
    type == LEGACY_ON_DEMAND_TYPE
  }

  @Override
  boolean handles(String type, String cloudProvider) {
    type == ON_DEMAND_TYPE && cloudProvider == amazonCloudProvider.id
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (!data.containsKey("loadBalancerName")) {
      return null
    }
    if (!data.containsKey("account")) {
      return null
    }
    if (!data.containsKey("region")) {
      return null
    }

    if (account.name != data.account) {
      return null
    }

    if (region != data.region) {
      return null
    }

    List<LoadBalancerDescription> loadBalancers = metricsSupport.readData {
      def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(account, region, true)
      try {
        return loadBalancing.describeLoadBalancers(
          new DescribeLoadBalancersRequest().withLoadBalancerNames(data.loadBalancerName as String)
        ).loadBalancerDescriptions
      } catch (LoadBalancerNotFoundException ignored) {
        return []
      }
    }

    def cacheResult = metricsSupport.transformData { buildCacheResult(loadBalancers, [:], System.currentTimeMillis(), []) }
    metricsSupport.onDemandStore {
      def cacheData = new DefaultCacheData(
        Keys.getLoadBalancerKey(data.loadBalancerName as String, account.name, region, loadBalancers ? loadBalancers[0].getVPCId() : null),
        10 * 60,
        [
          cacheTime   : new Date(),
          cacheResults: objectMapper.writeValueAsString(cacheResult.cacheResults)
        ],
        [:]
      )
      providerCache.putCacheData(ON_DEMAND.ns, cacheData)
    }
    Map<String, Collection<String>> evictions = loadBalancers ? [:] : [
      (LOAD_BALANCERS.ns): [
        Keys.getLoadBalancerKey(data.loadBalancerName as String, account.name, region, data.vpcId as String)
      ]
    ]

    log.info("onDemand cache refresh (data: ${data}, evictions: ${evictions})")
    return new OnDemandAgent.OnDemandResult(
      sourceAgentType: getAgentType(), cacheResult: cacheResult, evictions: evictions
    )
  }

  private Map<String, CacheData> cache() {
    [:].withDefault { String id -> new MutableCacheData(id) }
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")
    def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(account, region)
    List<LoadBalancerDescription> allLoadBalancers = []
    def request = new DescribeLoadBalancersRequest()
    Long start = account.eddaEnabled ? null : System.currentTimeMillis()

    while (true) {
      def resp = loadBalancing.describeLoadBalancers(request)
      if (account.eddaEnabled) {
        start = amazonClientProvider.lastModified ?: 0
      }

      allLoadBalancers.addAll(resp.loadBalancerDescriptions)
      if (resp.nextMarker) {
        request.withMarker(resp.nextMarker)
      } else {
        break
      }
    }

    if (!start) {
      if (account.eddaEnabled) {
        log.warn("${agentType} did not receive lastModified value in response metadata")
      }
      start = System.currentTimeMillis()
    }

    def evictableOnDemandCacheDatas = []
    def usableOnDemandCacheDatas = []
    providerCache.getAll(ON_DEMAND.ns, allLoadBalancers.collect { Keys.getLoadBalancerKey(it.loadBalancerName, account.name, region, it.getVPCId()) }).each {
      if (it.attributes.cacheTime < start) {
        evictableOnDemandCacheDatas << it
      } else {
        usableOnDemandCacheDatas << it
      }
    }

    buildCacheResult(allLoadBalancers, usableOnDemandCacheDatas.collectEntries { [it.id, it] }, start, evictableOnDemandCacheDatas)
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    return []
  }

  private CacheResult buildCacheResult(Collection<LoadBalancerDescription> allLoadBalancers, Map<String, CacheData> onDemandCacheDataByLb, long start, Collection<CacheData> evictableOnDemandCacheDatas) {

    Map<String, CacheData> instances = cache()
    Map<String, CacheData> loadBalancers = cache()

    for (LoadBalancerDescription lb : allLoadBalancers) {
      def onDemandCacheData = onDemandCacheDataByLb ? onDemandCacheDataByLb[Keys.getLoadBalancerKey(lb.loadBalancerName, account.name, region, lb.getVPCId())] : null
      if (onDemandCacheData) {
        log.info("Using onDemand cache value (${onDemandCacheData.id})")

        Map<String, List<CacheData>> cacheResults = objectMapper.readValue(onDemandCacheData.attributes.cacheResults as String, new TypeReference<Map<String, List<MutableCacheData>>>() {})
        cache(cacheResults["instances"], instances)
        cache(cacheResults["loadBalancers"], loadBalancers)
      } else {
        Collection<String> instanceIds = lb.instances.collect { Keys.getInstanceKey(it.instanceId, account.name, region) }
        Map<String, Object> lbAttributes = objectMapper.convertValue(lb, ATTRIBUTES)
        String loadBalancerId = Keys.getLoadBalancerKey(lb.loadBalancerName, account.name, region, lb.getVPCId())
        loadBalancers[loadBalancerId].with {
          attributes.putAll(lbAttributes)
          relationships[INSTANCES.ns].addAll(instanceIds)
        }
        for (String instanceId : instanceIds) {
          instances[instanceId].with {
            relationships[LOAD_BALANCERS.ns].add(loadBalancerId)
          }
        }
      }
    }
    recordDrift(start)
    log.info("Caching ${instances.size()} instances in ${agentType}")
    log.info("Caching ${loadBalancers.size()} load balancers in ${agentType}")
    if (evictableOnDemandCacheDatas) {
      log.info("Evicting onDemand cache keys (${evictableOnDemandCacheDatas.collect { "${it.id}/${start - it.attributes.cacheTime}ms"}.join(", ")})")
    }
    new DefaultCacheResult([
      (INSTANCES.ns): instances.values(),
      (LOAD_BALANCERS.ns):  loadBalancers.values()
      ],[
      (ON_DEMAND.ns): evictableOnDemandCacheDatas*.id])
  }

  private void cache(List<CacheData> data, Map<String, CacheData> cacheDataById) {
    data.each {
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
}
