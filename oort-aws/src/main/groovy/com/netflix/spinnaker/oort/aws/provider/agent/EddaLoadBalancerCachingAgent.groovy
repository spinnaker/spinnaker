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

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.oort.aws.edda.EddaApi
import com.netflix.spinnaker.oort.aws.data.Keys
import com.netflix.spinnaker.oort.aws.model.edda.InstanceLoadBalancers
import com.netflix.spinnaker.oort.aws.model.edda.LoadBalancerInstanceState
import com.netflix.spinnaker.oort.aws.provider.AwsProvider

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.HEALTH
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.INSTANCES

class EddaLoadBalancerCachingAgent implements CachingAgent {
  public static final String PROVIDER_NAME = "edda-load-balancers"

  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  private static final Collection<AgentDataType> types = Collections.unmodifiableCollection([
    AUTHORITATIVE.forType(HEALTH.ns),
    INFORMATIVE.forType(INSTANCES.ns)
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

  EddaLoadBalancerCachingAgent(EddaApi eddaApi, NetflixAmazonCredentials account, String region, ObjectMapper objectMapper) {
    this.eddaApi = eddaApi
    this.account = account
    this.region = region
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  }

  @Override
  CacheResult loadData() {
    List<LoadBalancerInstanceState> balancerInstances = eddaApi.loadBalancerInstances()

    List<InstanceLoadBalancers> ilbs = InstanceLoadBalancers.fromLoadBalancerInstanceState(balancerInstances)
    Collection<CacheData> lbHealths = new ArrayList<CacheData>(ilbs.size())
    Collection<CacheData> instances = new ArrayList<CacheData>(ilbs.size())

    for (InstanceLoadBalancers ilb : ilbs) {
      String instanceId = Keys.getInstanceKey(ilb.instanceId, account.name, region)
      String healthId = Keys.getInstanceHealthKey(ilb.instanceId, account.name, region, PROVIDER_NAME)
      Map<String, Object> attributes = objectMapper.convertValue(ilb, ATTRIBUTES)
      Map<String, Collection<String>> relationships = [(INSTANCES.ns): [instanceId]]
      lbHealths.add(new DefaultCacheData(healthId, attributes, relationships))
      instances.add(new DefaultCacheData(instanceId, [:], [(HEALTH.ns): [healthId]]))
    }
    new DefaultCacheResult(
      (HEALTH.ns): lbHealths,
      (INSTANCES.ns): instances)
  }
}
