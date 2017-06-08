/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.amazonaws.services.cloudwatchevents.model.DescribeRuleRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeListenersRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeListenersResult
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeRulesRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeRulesResult
import com.amazonaws.services.elasticloadbalancingv2.model.Listener
import com.amazonaws.services.elasticloadbalancingv2.model.ListenerNotFoundException
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancerNotFoundException
import com.amazonaws.services.elasticloadbalancingv2.model.Rule
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.data.ArnUtils
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.edda.EddaApi
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import retrofit.RetrofitError

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.ON_DEMAND
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.TARGET_GROUPS

class AmazonApplicationLoadBalancerCachingAgent extends AbstractAmazonLoadBalancerCachingAgent {
  final EddaApi eddaApi
  final EddaTimeoutConfig eddaTimeoutConfig

  AmazonApplicationLoadBalancerCachingAgent(AmazonCloudProvider amazonCloudProvider,
                                            AmazonClientProvider amazonClientProvider,
                                            NetflixAmazonCredentials account,
                                            String region,
                                            EddaApi eddaApi,
                                            ObjectMapper objectMapper,
                                            Registry registry,
                                            EddaTimeoutConfig eddaTimeoutConfig) {
    super(amazonCloudProvider, amazonClientProvider, account, region, objectMapper, registry)
    this.eddaApi = eddaApi
    this.eddaTimeoutConfig = eddaTimeoutConfig
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    // TODO: Support OnDemand support for ALBs
    return null
  }

  @Override
  CacheResult loadDataInternal(ProviderCache providerCache) {
    // TODO: Support caching of ALBs
    def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancingV2(account, region)
    List<LoadBalancer> allLoadBalancers = []
    def describeLoadBalancerRequest = new DescribeLoadBalancersRequest().withNames()
    Long start = account.eddaEnabled ? null : System.currentTimeMillis()

    while (true) {
      def resp = loadBalancing.describeLoadBalancers(describeLoadBalancerRequest)
      if (account.eddaEnabled) {
        start = amazonClientProvider.lastModified ?: 0
      }

      allLoadBalancers.addAll(resp.loadBalancers)
      if (resp.nextMarker) {
        describeLoadBalancerRequest.withMarker(resp.nextMarker)
      } else {
        break
      }
    }

    Map<LoadBalancer, List<Listener>> loadBalancerToListeners = new HashMap<LoadBalancer, ArrayList<Listener>>()
    for (loadBalancer in allLoadBalancers) {
      loadBalancerToListeners[loadBalancer] = []
      if (account.eddaEnabled && eddaTimeoutConfig.albEnabled) {
        try {
          List<Listener> listeners = eddaApi.listeners(loadBalancer.loadBalancerName)
          loadBalancerToListeners.get(loadBalancer).addAll(listeners)
        } catch (RetrofitError e) {
          // this is acceptable since we may be waiting for the caches to catch up
        }
      } else {
        try {
          DescribeListenersRequest describeListenersRequest = new DescribeListenersRequest(loadBalancerArn: loadBalancer.loadBalancerArn)
          while (true) {
            DescribeListenersResult result = loadBalancing.describeListeners(describeListenersRequest)
            loadBalancerToListeners.get(loadBalancer).addAll(result.listeners)
            if (result.nextMarker) {
              describeListenersRequest.withMarker(result.nextMarker)
            } else {
              break
            }
          }
        } catch (LoadBalancerNotFoundException e) {
          // this is acceptable since we may be waiting for the caches to catch up
        }
      }
    }

    Map<Listener, List<Rule>> listenerToRules = new HashMap<>()
    for (loadBalancer in allLoadBalancers) {
      for (listener in loadBalancerToListeners.get(loadBalancer)) {
        listenerToRules[listener] = []
        try {
          DescribeRulesRequest describeRulesRequest = new DescribeRulesRequest(listenerArn: listener.listenerArn)
          DescribeRulesResult result = loadBalancing.describeRules(describeRulesRequest)
          listenerToRules.get(listener).addAll(result.rules)
        } catch (ListenerNotFoundException e) {
          // should be fine
        }
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
    // TODO: Supprt ALBs in ON_DEMAND

    return buildCacheResult(allLoadBalancers, loadBalancerToListeners, listenerToRules, usableOnDemandCacheDatas.collectEntries { [it.id, it] }, start, evictableOnDemandCacheDatas)
  }

  private CacheResult buildCacheResult(Collection<LoadBalancer> allLoadBalancers, Map<LoadBalancer, List<Listener>> loadBalancerToListeners, Map<Listener, List<Rule>> listenerToRules, Map<String, CacheData> onDemandCacheDataByLb, long start, Collection<CacheData> evictableOnDemandCacheDatas) {
    Map<String, CacheData> loadBalancers = CacheHelpers.cache()

    for (LoadBalancer lb : allLoadBalancers) {
      String loadBalancerKey = Keys.getLoadBalancerKey(lb.loadBalancerName, account.name, region, lb.vpcId, lb.type)

      def onDemandCacheData = onDemandCacheDataByLb ? onDemandCacheDataByLb[Keys.getLoadBalancerKey(lb.loadBalancerName, account.name, region, lb.vpcId, "application")] : null
      if (onDemandCacheData) {
        log.info("Using onDemand cache value (${onDemandCacheData.id})")

        Map<String, List<CacheData>> cacheResults = objectMapper.readValue(onDemandCacheData.attributes.cacheResults as String, new TypeReference<Map<String, List<MutableCacheData>>>() {})
        CacheHelpers.cache(cacheResults["loadBalancers"], loadBalancers)
      } else {
        Map<String, Object> lbAttributes = objectMapper.convertValue(lb, ATTRIBUTES)

        // Type is already used for provider name, so rename the field
        lbAttributes.loadBalancerType = lbAttributes.type
        lbAttributes.remove("type")

        // Translate availabilityZones to the format we expect
        List<String> availabilityZones = new ArrayList<String>()
        List<String> subnets = new ArrayList<String>()
        ((List<Map<String, String>>)lbAttributes.availabilityZones).each { az ->
          availabilityZones.push(az.zoneName)
          subnets.push(az.subnetId)
        }
        lbAttributes.subnets = subnets
        lbAttributes.availabilityZones = availabilityZones


        // Add the listeners
        def listeners = []
        Set<String> allTargetGroupKeys = []
        String vpcId = Keys.parse(loadBalancerKey).vpcId
        for (Listener listener : loadBalancerToListeners.get(lb)) {

          Map<String, Object> listenerAttributes = objectMapper.convertValue(listener, ATTRIBUTES)
          listenerAttributes.loadBalancerName = ArnUtils.extractLoadBalancerName((String)listenerAttributes.loadBalancerArn).get()
          listenerAttributes.remove('loadBalancerArn')
          for (Map<String, String> action : (List<Map<String, String>>)listenerAttributes.defaultActions) {
            String targetGroupName = ArnUtils.extractTargetGroupName(action.targetGroupArn).get()
            action.targetGroupName = targetGroupName
            action.remove("targetGroupArn")

            allTargetGroupKeys.add(Keys.getTargetGroupKey(targetGroupName, account.name, region, vpcId))
          }

          // add the rules to the listener
          List<Object> rules = new ArrayList<>()
          for (Rule rule : listenerToRules.get(listener)) {
            Map<String, Object> ruleAttributes = objectMapper.convertValue(rule, ATTRIBUTES)
            for (Map<String, String> action : (List<Map<String, String>>)ruleAttributes.actions) {
              String targetGroupName = ArnUtils.extractTargetGroupName(action.targetGroupArn).get()
              action.targetGroupName = targetGroupName
              action.remove("targetGroupArn")

              allTargetGroupKeys.add(Keys.getTargetGroupKey(targetGroupName, account.name, region, vpcId))
            }

            rules.push(ruleAttributes)
          }
          listenerAttributes.rules = rules

          listeners.push(listenerAttributes)
        }

        lbAttributes.listeners = listeners

        loadBalancers[loadBalancerKey].with {
          attributes.putAll(lbAttributes)
          relationships[TARGET_GROUPS.ns].addAll(allTargetGroupKeys)
        }
      }
    }
    log.info("Caching ${loadBalancers.size()} load balancers in ${agentType}")
    if (evictableOnDemandCacheDatas) {
      log.info("Evicting onDemand cache keys (${evictableOnDemandCacheDatas.collect { "${it.id}/${start - (long)it.attributes.cacheTime}ms"}.join(", ")})")
    }
    new DefaultCacheResult([
      (LOAD_BALANCERS.ns):  loadBalancers.values()
    ],[
      (ON_DEMAND.ns): evictableOnDemandCacheDatas*.id])
  }
}
