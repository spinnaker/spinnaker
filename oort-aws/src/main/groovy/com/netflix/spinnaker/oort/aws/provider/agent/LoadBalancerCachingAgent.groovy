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

package com.netflix.spinnaker.oort.aws.provider.agent

import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerNotFoundException
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.oort.aws.data.Keys
import com.netflix.spinnaker.oort.aws.provider.AwsProvider
import groovy.util.logging.Slf4j
import org.codehaus.jackson.annotate.JsonCreator

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.ON_DEMAND

@Slf4j
class LoadBalancerCachingAgent  implements CachingAgent, OnDemandAgent {
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
  String getOnDemandAgentType() {
    "${getAgentType()}-OnDemand"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper

  LoadBalancerCachingAgent(AmazonClientProvider amazonClientProvider,
                           NetflixAmazonCredentials account,
                           String region,
                           ObjectMapper objectMapper) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
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
    type == "AmazonLoadBalancer"
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

    def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(account, region, true)
    def loadBalancers = []
    try {
      loadBalancers = loadBalancing.describeLoadBalancers(
        new DescribeLoadBalancersRequest().withLoadBalancerNames(data.loadBalancerName as String)
      ).loadBalancerDescriptions
    } catch (LoadBalancerNotFoundException ignored) {}

    def cacheResult = buildCacheResult(loadBalancers, [:])
    def cacheData = new DefaultCacheData(
      Keys.getLoadBalancerKey(data.loadBalancerName as String, account.name, region, loadBalancers ? loadBalancers[0].getVPCId() : null),
      60 * 60,
      [
        cacheTime   : new Date(),
        cacheResults: objectMapper.writeValueAsString(cacheResult.cacheResults)
      ],
      [:]
    )
    providerCache.putCacheData(ON_DEMAND.ns, cacheData)

    cacheResult.cacheResults.values().each { Collection<CacheData> cacheDatas ->
      cacheDatas.each {
        ((MutableCacheData) it).ttlSeconds = 60
      }
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
    def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(account, region)
    List<LoadBalancerDescription> allLoadBalancers = []
    def request = new DescribeLoadBalancersRequest()
    Long start = null

    while (true) {
      def resp = loadBalancing.describeLoadBalancers(request)
      if (!start) {
        start = EddaSupport.parseLastModified(amazonClientProvider.lastResponseHeaders?.get("last-modified")?.get(0))
      }

      allLoadBalancers.addAll(resp.loadBalancerDescriptions)
      if (resp.nextMarker) {
        request.withMarker(resp.nextMarker)
      } else {
        break
      }
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

    if (evictableOnDemandCacheDatas) {
      log.info("Evicting onDemand cache keys (${evictableOnDemandCacheDatas.collect { "${it.id}/${start - it.attributes.cacheTime}ms"}.join(", ")})")
      providerCache.evictDeletedItems(ON_DEMAND.ns, evictableOnDemandCacheDatas*.id)
    }

    buildCacheResult(allLoadBalancers, usableOnDemandCacheDatas.collectEntries { [it.id, it] })
  }

  private CacheResult buildCacheResult(Collection<LoadBalancerDescription> allLoadBalancers, Map<String, CacheData> onDemandCacheDataByLb) {

    Map<String, CacheData> instances = cache()
    Map<String, CacheData> loadBalancers = cache()

    for (LoadBalancerDescription lb : allLoadBalancers) {
      def onDemandCacheData = onDemandCacheDataByLb ? onDemandCacheDataByLb[Keys.getLoadBalancerKey(lb.loadBalancerName, account.name, region, lb.getVPCId())] : null
      if (onDemandCacheData) {
        log.info("Using onDemand cache value (${onDemandCacheData.id})")

        Map<String, List<CacheData>> cacheResults = objectMapper.readValue(onDemandCacheData.attributes.cacheResults as String, new TypeReference<Map<String, List<MutableCacheData>>>() {})
        instances.putAll(cacheResults.instances.collectEntries { [it.id, it] })
        loadBalancers.putAll(cacheResults.loadBalancers.collectEntries { [it.id, it] })
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

    new DefaultCacheResult(
      (INSTANCES.ns): instances.values(),
      (LOAD_BALANCERS.ns):  loadBalancers.values())
  }
}
