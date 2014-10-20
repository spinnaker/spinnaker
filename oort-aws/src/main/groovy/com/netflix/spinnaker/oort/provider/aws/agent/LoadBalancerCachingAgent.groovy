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

package com.netflix.spinnaker.oort.provider.aws.agent

import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
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
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.provider.aws.AwsProvider

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.LOAD_BALANCERS

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
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper

  LoadBalancerCachingAgent(AmazonClientProvider amazonClientProvider, NetflixAmazonCredentials account, String region, ObjectMapper objectMapper) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  }

  static class MutableCacheData implements CacheData {
    final String id
    final Map<String, Object> attributes = [:]
    final Map<String, Collection<String>> relationships = [:].withDefault { [] as Set }
    public MutableCacheData(String id) {
      this.id = id
    }
  }

  @Override
  boolean handles(String type) {
    type == "AmazonLoadBalancer"
  }

  @Override
  OnDemandAgent.OnDemandResult handle(Map<String, ? extends Object> data) {
    if (!data.containsKey("loadBalancerName")) {
      return
    }
    if (!data.containsKey("account")) {
      return
    }
    if (!data.containsKey("region")) {
      return
    }

    if (account.name != data.account) {
      return
    }

    if (region != data.region) {
      return
    }

    if (data.evict as boolean) {
      new OnDemandAgent.OnDemandResult(sourceAgentType: getAgentType(), evictions: [(LOAD_BALANCERS.ns): [Keys.getLoadBalancerKey(data.loadBalancerName as String, account.name, region, data.vpcId)]])
    } else {
      def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(account, region, true)
      def lb = loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest().withLoadBalancerNames(data.loadBalancerName as String)).loadBalancerDescriptions

      new OnDemandAgent.OnDemandResult(sourceAgentType: getAgentType(), cacheResult: buildCacheResult(lb))
    }
  }

  private Map<String, CacheData> cache() {
    [:].withDefault { String id -> new MutableCacheData(id) }
  }

  @Override
  CacheResult loadData() {
    def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(account, region)
    List<LoadBalancerDescription> allLoadBalancers = []
    def request = new DescribeLoadBalancersRequest()
    while (true) {
      def resp = loadBalancing.describeLoadBalancers(request)
      allLoadBalancers.addAll(resp.loadBalancerDescriptions)
      if (resp.nextMarker) {
        request.withMarker(resp.nextMarker)
      } else {
        break
      }
    }
    buildCacheResult(allLoadBalancers)
  }

  private CacheResult buildCacheResult(Collection<LoadBalancerDescription> allLoadBalancers) {

    Map<String, CacheData> instances = cache()
    Map<String, CacheData> loadBalancers = cache()

    for (LoadBalancerDescription loadBalancer : allLoadBalancers) {
      Collection<String> instanceIds = loadBalancer.instances.collect { Keys.getInstanceKey(it.instanceId, account.name, region) }
      Map<String, Object> lbAttributes = objectMapper.convertValue(loadBalancer, ATTRIBUTES)
      String loadBalancerId = Keys.getLoadBalancerKey(loadBalancer.loadBalancerName, account.name, region, loadBalancer.getVPCId())
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

    new DefaultCacheResult(
      (INSTANCES.ns): instances.values(),
      (LOAD_BALANCERS.ns):  loadBalancers.values())
  }
}
