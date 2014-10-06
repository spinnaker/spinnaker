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

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.oort.config.edda.EddaApi
import com.netflix.spinnaker.oort.model.Instance
import com.netflix.spinnaker.oort.model.edda.LoadBalancerInstance
import com.netflix.spinnaker.oort.model.edda.LoadBalancerInstanceState
import com.netflix.spinnaker.oort.provider.aws.AwsProvider
import com.netflix.spinnaker.oort.provider.aws.AwsProvider.Identifiers

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE

class EddaLoadBalancerCachingAgent implements CachingAgent {
  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  private static final Collection<AgentDataType> types = Collections.unmodifiableCollection([
    AUTHORITATIVE.forType(AwsProvider.LOAD_BALANCER_HEALTH_TYPE),
    INFORMATIVE.forType(Instance.DATA_TYPE)
  ])

  @Override
  String getProviderName() {
    AwsProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${EddaLoadBalancerCachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  private final EddaApi eddaApi
  private final NetflixAmazonCredentials account
  private final String region
  private final ObjectMapper objectMapper
  private final Identifiers identifiers

  EddaLoadBalancerCachingAgent(EddaApi eddaApi, NetflixAmazonCredentials account, String region, ObjectMapper objectMapper) {
    this.eddaApi = eddaApi
    this.account = account
    this.region = region
    this.objectMapper = objectMapper
    this.identifiers = new Identifiers(account.name, region)
  }

  @Override
  CacheResult loadData() {
    List<LoadBalancerInstanceState> balancerInstances = eddaApi.loadBalancerInstances()
    Collection<CacheData> lbHealths = new ArrayList<CacheData>(balancerInstances.size())
    Collection<CacheData> instances = new ArrayList<CacheData>(balancerInstances.size())

    for (LoadBalancerInstanceState balancerInstance : balancerInstances) {
      for (LoadBalancerInstance instance : balancerInstance.instances) {
        String instanceId = identifiers.instanceId(instance.instanceId)
        Map<String, Object> attributes = objectMapper.convertValue(instance, ATTRIBUTES)
        Map<String, Collection<String>> relationships = [(Instance.DATA_TYPE): [instanceId]]
        lbHealths.add(new DefaultCacheData(instanceId, attributes, relationships))
        instances.add(new DefaultCacheData(instanceId, [:], [(AwsProvider.LOAD_BALANCER_HEALTH_TYPE): [instanceId]]))
      }
    }
    new DefaultCacheResult(
      (AwsProvider.LOAD_BALANCER_HEALTH_TYPE): lbHealths,
      (Instance.DATA_TYPE): instances)
  }
}
