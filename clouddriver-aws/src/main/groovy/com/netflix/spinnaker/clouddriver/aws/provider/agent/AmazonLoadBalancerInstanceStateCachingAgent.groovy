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
import com.amazonaws.services.elasticloadbalancing.model.DescribeInstanceHealthResult
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
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
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

  final static String healthId = "aws-load-balancer-instance-health"
  private final static String STILL_REGISTERING_DESCRIPTION = "Instance registration is still in progress."

  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper
  final ApplicationContext ctx

  private Cache cacheView

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
    def loadBalancerKeys = getCacheView()
      .filterIdentifiers(LOAD_BALANCERS.ns, allVpcsGlob) +
      getCacheView().filterIdentifiers(LOAD_BALANCERS.ns, nonVpcGlob)

    Map<String, CacheData> lbHealths = new HashMap<>()
    Collection<CacheData> instanceRels = new ArrayList<>()

    for (loadBalancerKey in loadBalancerKeys) {
      try {
        Map<String, String> idObj = Keys.parse(loadBalancerKey)
        String lbName = idObj.loadBalancer
        if (idObj.loadBalancerType && idObj.loadBalancerType != 'classic')
          continue

        List<LoadBalancerInstance> loadBalancerInstances = new ArrayList<>()
        DescribeInstanceHealthResult result = loadBalancing
          .describeInstanceHealth(new DescribeInstanceHealthRequest(lbName))

        for (instanceState in result.instanceStates) {
          LoadBalancerInstance loadBalancerInstance = new LoadBalancerInstance(
            instanceState.instanceId,
            instanceState.state,
            instanceState.reasonCode,
            instanceState.description)
          loadBalancerInstances << loadBalancerInstance

          // We want to track how long instances remain in a "still registering" state. Logging any time we
          // see an instance with this description is a poor man's way of getting the metrics we need, without
          // having to do expensive lookups - we can defer this to our logging platform to do the maths.
          // TODO(rz): This kind of metric may be easier to create if we had a method of emitting events when
          //  cache state changes.
          if (instanceState.description == STILL_REGISTERING_DESCRIPTION) {
            log.info("Instance '${instanceState.instanceId}' is still registering with load balancer '$lbName'")
          }
        }

        LoadBalancerInstanceState loadBalancerInstanceState = new LoadBalancerInstanceState(
          name: lbName,
          instances: loadBalancerInstances)
        List<InstanceLoadBalancers> ilbs = InstanceLoadBalancers
          .fromLoadBalancerInstanceState([loadBalancerInstanceState])
        Collection<String> instanceIds = ilbs.collect {
          Keys.getInstanceKey(it.instanceId, account.name, region)
        }
        Map<String, CacheData> instances = providerCache
          .getAll(INSTANCES.ns, instanceIds, RelationshipCacheFilter.none())
          .collectEntries { [(it.id): it] }

        for (InstanceLoadBalancers ilb in ilbs) {
          String instanceId = Keys.getInstanceKey(ilb.instanceId, account.name, region)
          String healthId = Keys.getInstanceHealthKey(ilb.instanceId, account.name, region, healthId)
          Map<String, Object> attributes = objectMapper.convertValue(ilb, ATTRIBUTES)
          Map<String, Collection<String>> relationships = [(INSTANCES.ns): [instanceId]]

          if (instances[instanceId] != null) {
            String application = instances[instanceId].attributes.get("application")
            if (application != null) {
              attributes.put("application", application)
            }
          }

          CacheData lbHealth = new DefaultCacheData(healthId, attributes, relationships);
          CacheData previousLbHealth = lbHealths.put(healthId, lbHealth);
          if (previousLbHealth != null) {
            // We already had health information about this instance from one
            // load balancer It would be nice to add this health information to
            // what we already had, and
            // com.netflix.spinnaker.clouddriver.aws.model.edda.InstanceLoadBalancers
            // does have a List<InstanceLoadBalancerState> that we could in
            // theory add to, but it's only got one HealthState and multiple
            // load balancers could have different opinions about that.
            //
            // So for now at least, drop the instance state information from
            // this previous load balancer on the floor.  Log it, but at debug
            // since this can happen frequently.
            //
            // This effectively retains instance health information from the
            // last load balancer that supports it, which is consistent with the
            // way the redis cache behaves when presented with multiple pieces
            // of information.
            log.debug("replaced instance health information for {}: was {}, is now {}",
                      instanceId, previousLbHealth.attributes, attributes)
            continue
          }
          instanceRels.add(new DefaultCacheData(instanceId, [:], [(HEALTH.ns): [healthId]]))
        }
      } catch (LoadBalancerNotFoundException e) {
        // this is acceptable since we may be waiting for the caches to catch up
      }
    }
    log.info("Caching ${lbHealths.size()} items in ${agentType}")
    new DefaultCacheResult(
      (HEALTH.ns): lbHealths.values(),
      (INSTANCES.ns): instanceRels)
  }

  private Cache getCacheView() {
    if (!this.cacheView) {
      this.cacheView = ctx.getBean(Cache)
    }
    this.cacheView
  }
}
