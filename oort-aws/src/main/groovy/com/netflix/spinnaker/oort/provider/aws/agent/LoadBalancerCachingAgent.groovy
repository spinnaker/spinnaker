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
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.oort.model.Instance
import com.netflix.spinnaker.oort.model.LoadBalancer
import com.netflix.spinnaker.oort.provider.aws.AwsProvider

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE

class LoadBalancerCachingAgent  implements CachingAgent {
  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  private static final Collection<AgentDataType> types = Collections.unmodifiableCollection([
    AUTHORITATIVE.forType(LoadBalancer.DATA_TYPE),
    INFORMATIVE.forType(Instance.DATA_TYPE)
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
  final AwsProvider.Identifiers identifiers

  LoadBalancerCachingAgent(AmazonClientProvider amazonClientProvider, NetflixAmazonCredentials account, String region, ObjectMapper objectMapper) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper
    this.identifiers = new AwsProvider.Identifiers(account.name, region)
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

    Closure<Map<String, CacheData>> cache = {
      [:].withDefault { String id -> new MutableCacheData(id) }
    }

    Map<String, CacheData> instances = cache()
    Map<String, CacheData> loadBalancers = cache()

    for (LoadBalancerDescription loadBalancer : allLoadBalancers) {
      Collection<String> instanceIds = loadBalancer.instances.collect { identifiers.instanceId(it.instanceId) }
      Map<String, Object> attributes = objectMapper.convertValue(loadBalancer, ATTRIBUTES)
      String loadBalancerId = identifiers.loadBalancerId(loadBalancer.loadBalancerName)
      loadBalancers[loadBalancerId].with {
        attributes.putAll(attributes)
        relationships[Instance.DATA_TYPE].addAll(instanceIds)
      }
      for (String instanceId : instanceIds) {
        instances[instanceId].with {
          relationships[LoadBalancer.DATA_TYPE].add(loadBalancerId)
        }
      }
    }

    new DefaultCacheResult(
      (Instance.DATA_TYPE): instances.values(),
      (LoadBalancer.DATA_TYPE):  loadBalancers.values())
  }
}
