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

import com.amazonaws.services.elasticloadbalancing.model.DescribeInstanceHealthRequest
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerNotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.oort.aws.data.Keys
import com.netflix.spinnaker.oort.aws.model.edda.InstanceLoadBalancers
import com.netflix.spinnaker.oort.aws.model.edda.LoadBalancerInstance
import com.netflix.spinnaker.oort.aws.model.edda.LoadBalancerInstanceState
import com.netflix.spinnaker.oort.aws.provider.AwsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext

import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.HEALTH
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.LOAD_BALANCERS

class AmazonLoadBalancerInstanceStateCachingAgent implements HealthProvidingCachingAgent {
  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper

  private Cache cacheView
  final String healthId = "aws-load-balancer-instance-health"

  @Autowired
  ApplicationContext ctx

  AmazonLoadBalancerInstanceStateCachingAgent(AmazonClientProvider amazonClientProvider,
                                              NetflixAmazonCredentials account, String region,
                                              ObjectMapper objectMapper) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  }

  @Override
  String getProviderName() {
    AwsProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${AmazonLoadBalancerInstanceStateCachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(account, region)
    def loadBalancerKeys = getCacheView().getIdentifiers(LOAD_BALANCERS.ns)

    Collection<CacheData> lbHealths = []
    Collection<CacheData> instances = []
    for (loadBalancerKey in loadBalancerKeys) {
      try {
        Map<String, String> idObj = Keys.parse(loadBalancerKey)
        def lbName = idObj.loadBalancer
        def result = loadBalancing.describeInstanceHealth(new DescribeInstanceHealthRequest(lbName))
        def loadBalancerInstances = []
        for (instanceState in result.instanceStates) {
          def loadBalancerInstance = new LoadBalancerInstance(instanceState.instanceId, instanceState.state, instanceState.reasonCode, instanceState.description)
          loadBalancerInstances << loadBalancerInstance
        }
        def loadBalancerInstanceState = new LoadBalancerInstanceState(lbName, loadBalancerInstances)
        def ilbs = InstanceLoadBalancers.fromLoadBalancerInstanceState([loadBalancerInstanceState])

        for (InstanceLoadBalancers ilb in ilbs) {
          String instanceId = Keys.getInstanceKey(ilb.instanceId, account.name, region)
          String healthId = Keys.getInstanceHealthKey(ilb.instanceId, account.name, region, healthId)
          Map<String, Object> attributes = objectMapper.convertValue(ilb, ATTRIBUTES)
          Map<String, Collection<String>> relationships = [(INSTANCES.ns): [instanceId]]
          lbHealths.add(new DefaultCacheData(healthId, attributes, relationships))
          instances.add(new DefaultCacheData(instanceId, [:], [(HEALTH.ns): [healthId]]))
        }
      } catch (LoadBalancerNotFoundException e) {
        // this is acceptable since we may be waiting for the caches the catch-up
      }
    }
    new DefaultCacheResult(
      (HEALTH.ns): lbHealths,
      (INSTANCES.ns): instances)
  }

  private Cache getCacheView() {
    if (!this.cacheView) {
      this.cacheView = ctx.getBean(Cache)
    }
    this.cacheView
  }
}
