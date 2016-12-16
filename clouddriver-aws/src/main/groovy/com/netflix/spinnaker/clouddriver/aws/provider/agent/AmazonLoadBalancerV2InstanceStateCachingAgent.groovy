/*
 * Copyright 2016 Netflix, Inc.
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

import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealthDescription
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.util.concurrent.RateLimiter
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.model.edda.InstanceLoadBalancerState
import com.netflix.spinnaker.clouddriver.aws.model.edda.InstanceLoadBalancers
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.core.provider.agent.HealthProvidingCachingAgent
import groovy.util.logging.Slf4j

import java.util.regex.Pattern

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES


@Slf4j
class AmazonLoadBalancerV2InstanceStateCachingAgent implements CachingAgent, HealthProvidingCachingAgent {
  private static final Pattern ALB_ARN_PATTERN = Pattern.compile(/^arn:aws:elasticloadbalancing:[^:]+:[^:]+:loadbalancer\/app\/([^\/]+)\/.+$/)
  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper
  final static String healthId = "aws-load-balancer-v2-instance-health"

  AmazonLoadBalancerV2InstanceStateCachingAgent(AmazonClientProvider amazonClientProvider, NetflixAmazonCredentials account, String region, ObjectMapper objectMapper) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper
  }

  @Override
  String getHealthId() {
    healthId
  }

  @Override
  String getAgentType() {
    return "${account.name}/${region}/${AmazonLoadBalancerV2InstanceStateCachingAgent.simpleName}"
  }

  @Override
  String getProviderName() {
    AwsProvider.PROVIDER_NAME
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  RateLimiter rateLimiter() {
    return RateLimiter.create(2)
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    def limiter = rateLimiter()
    def lbv2 = amazonClientProvider.getAmazonElasticLoadBalancingV2(account, region)
    List<TargetGroup> targetGroups = []
    def req = new DescribeTargetGroupsRequest()
    while (true) {
      limiter.acquire()
      def resp = lbv2.describeTargetGroups(req)
      if (resp.targetGroups) {
        targetGroups.addAll(resp.targetGroups)
      }
      if (!resp.nextMarker) {
        break
      }
      req.setMarker(resp.nextMarker)
    }

    List<InstanceLoadBalancerState> ilbStates = []
    for (TargetGroup tg : targetGroups) {
      Optional.ofNullable(tg.loadBalancerArns?.getAt(0))
        .flatMap({ extractLoadBalancerName(it) })
        .ifPresent({ String loadBalancerName ->
          limiter.acquire()
          def healthDesc = lbv2.describeTargetHealth(new DescribeTargetHealthRequest().withTargetGroupArn(tg.targetGroupArn)).targetHealthDescriptions
          for (TargetHealthDescription hd : healthDesc) {
            ilbStates << new InstanceLoadBalancerState(hd.target.id, 'application', loadBalancerName, hd.targetHealth.state, hd.targetHealth.reason, hd.targetHealth.description)
          }
        })
    }

    List<InstanceLoadBalancers> ilbs = InstanceLoadBalancers.fromInstanceLoadBalancerStates(ilbStates)
    Collection<CacheData> lbHealths = []
    Collection<CacheData> instances = []
    for (InstanceLoadBalancers ilb in ilbs) {
      String instanceId = Keys.getInstanceKey(ilb.instanceId, account.name, region)
      String healthId = Keys.getInstanceHealthKey(ilb.instanceId, account.name, region, healthId)
      Map<String, Object> attributes = objectMapper.convertValue(ilb, ATTRIBUTES)
      Map<String, Collection<String>> relationships = [(INSTANCES.ns): [instanceId]]
      lbHealths.add(new DefaultCacheData(healthId, attributes, relationships))
      instances.add(new DefaultCacheData(instanceId, [:], [(HEALTH.ns): [healthId]]))
    }

    log.info("Caching ${lbHealths.size()} items in ${agentType}")

    return new DefaultCacheResult(
      (HEALTH.ns): lbHealths,
      (INSTANCES.ns): instances)
  }

  static Optional<String> extractLoadBalancerName(String loadBalancerArn) {
    def m = ALB_ARN_PATTERN.matcher(loadBalancerArn)
    if (m.matches()) {
      return Optional.of(m.group(1))
    }
    return Optional.empty()
  }
}
