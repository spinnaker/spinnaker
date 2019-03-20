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

import com.amazonaws.services.elasticloadbalancing.model.DescribeInstanceHealthRequest
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerNotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.model.edda.InstanceLoadBalancers
import com.netflix.spinnaker.clouddriver.aws.model.edda.LoadBalancerInstance
import com.netflix.spinnaker.clouddriver.aws.model.edda.LoadBalancerInstanceState
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.core.provider.agent.HealthProvidingCachingAgent
import groovy.util.logging.Slf4j
import org.springframework.context.ApplicationContext

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LOAD_BALANCERS

@Slf4j
class AmazonLoadBalancerInstanceStateCachingAgent implements CachingAgent, HealthProvidingCachingAgent, AccountAware {
  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper
  final ApplicationContext ctx

  private Cache cacheView
  final static String healthId = "aws-load-balancer-instance-health"


  AmazonLoadBalancerInstanceStateCachingAgent(AmazonClientProvider amazonClientProvider,
                                              NetflixAmazonCredentials account, String region,
                                              ObjectMapper objectMapper,
                                              ApplicationContext ctx) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    this.ctx = ctx
  }

  @Override
  String getHealthId() {
    healthId
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
  String getAccountName() {
    account.name
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")
    def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(account, region)
    def allVpcsGlob = Keys.getLoadBalancerKey('*', account.name, region, '*', null)
    def nonVpcGlob = Keys.getLoadBalancerKey('*', account.name, region, null, null)
    def loadBalancerKeys =
      getCacheView().filterIdentifiers(LOAD_BALANCERS.ns, allVpcsGlob) + getCacheView().filterIdentifiers(LOAD_BALANCERS.ns, nonVpcGlob)

    Collection<CacheData> lbHealths = []
    Collection<CacheData> instances = []
    for (loadBalancerKey in loadBalancerKeys) {
      try {
        Map<String, String> idObj = Keys.parse(loadBalancerKey)
        def lbName = idObj.loadBalancer
        if (idObj.loadBalancerType && idObj.loadBalancerType != 'classic')
          continue

        def result = loadBalancing.describeInstanceHealth(new DescribeInstanceHealthRequest(lbName))
        def loadBalancerInstances = []
        for (instanceState in result.instanceStates) {
          def loadBalancerInstance = new LoadBalancerInstance(instanceState.instanceId, instanceState.state, instanceState.reasonCode, instanceState.description)
          loadBalancerInstances << loadBalancerInstance
        }
        def loadBalancerInstanceState = new LoadBalancerInstanceState(name: lbName, instances: loadBalancerInstances)
        def ilbs = InstanceLoadBalancers.fromLoadBalancerInstanceState([loadBalancerInstanceState])

        for (InstanceLoadBalancers ilb in ilbs) {
          String instanceId = Keys.getInstanceKey(ilb.instanceId, account.name, region)
          String healthId = Keys.getInstanceHealthKey(ilb.instanceId, account.name, region, healthId)

          Map<String, Object> attributes = objectMapper.convertValue(ilb, ATTRIBUTES)
          if (idObj.containsKey("application")) {
            attributes.put("application", idObj.get("application"))
          }

          Map<String, Collection<String>> relationships = [(INSTANCES.ns): [instanceId]]
          lbHealths.add(new DefaultCacheData(healthId, attributes, relationships))
          instances.add(new DefaultCacheData(instanceId, [:], [(HEALTH.ns): [healthId]]))
        }
      } catch (LoadBalancerNotFoundException e) {
        // this is acceptable since we may be waiting for the caches to catch up
      }
    }
    log.info("Caching ${lbHealths.size()} items in ${agentType}")
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
