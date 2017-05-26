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

import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupAttributesRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroupAttribute
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealthDescription
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.data.ArnUtils
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.model.InstanceTargetGroupState
import com.netflix.spinnaker.clouddriver.aws.model.InstanceTargetGroups
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.core.provider.agent.HealthProvidingCachingAgent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.*

class AmazonTargetGroupCachingAgent implements CachingAgent, HealthProvidingCachingAgent, AccountAware {
  final Logger log = LoggerFactory.getLogger(getClass())

  static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  static final Collection<AgentDataType> types = Collections.unmodifiableCollection([
    AUTHORITATIVE.forType(TARGET_GROUPS.ns),
    AUTHORITATIVE.forType(HEALTH.ns),
    INFORMATIVE.forType(LOAD_BALANCERS.ns),
    INFORMATIVE.forType(INSTANCES.ns)
  ] as Set)

  static final String healthId = "aws-load-balancer-v2-target-group-instance-health"

  final AmazonCloudProvider amazonCloudProvider
  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper
  final Registry registry

  AmazonTargetGroupCachingAgent(AmazonCloudProvider amazonCloudProvider,
                                AmazonClientProvider amazonClientProvider,
                                NetflixAmazonCredentials account,
                                String region,
                                ObjectMapper objectMapper,
                                Registry registry) {
    this.amazonCloudProvider = amazonCloudProvider
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    this.registry = registry
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
    "${account.name}/${region}/${AmazonTargetGroupCachingAgent.simpleName}"
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
    AmazonElasticLoadBalancing loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancingV2(account, region)

    // Get all the target groups
    List<TargetGroup> allTargetGroups = []
    DescribeTargetGroupsRequest describeTargetGroupsRequest = new DescribeTargetGroupsRequest()
    while (true) {
      def resp = loadBalancing.describeTargetGroups(describeTargetGroupsRequest)
      allTargetGroups.addAll(resp.targetGroups)
      if (resp.nextMarker) {
        describeTargetGroupsRequest.withMarker(resp.nextMarker)
      } else {
        break
      }
    }

    // Get all the target group health and attributes
    Map<String, List<TargetHealthDescription>> targetGroupArnToHealths = new HashMap<String, List<TargetHealthDescription>>()
    Map<String, List<TargetGroupAttribute>> targetGroupArnToAttributes = new HashMap<String, List<TargetGroupAttribute>>()
    for (TargetGroup targetGroup : allTargetGroups) {
      List<TargetHealthDescription> targetHealthDescriptions = loadBalancing.describeTargetHealth(new DescribeTargetHealthRequest().withTargetGroupArn(targetGroup.targetGroupArn)).targetHealthDescriptions
      targetGroupArnToHealths.put(targetGroup.targetGroupArn, targetHealthDescriptions)
      List<TargetGroupAttribute> targetGroupAttributes = loadBalancing.describeTargetGroupAttributes(new DescribeTargetGroupAttributesRequest().withTargetGroupArn(targetGroup.targetGroupArn)).attributes
      targetGroupArnToAttributes.put(targetGroup.targetGroupArn, targetGroupAttributes)
    }

    buildCacheResult(allTargetGroups, targetGroupArnToHealths, targetGroupArnToAttributes)
  }

  private CacheResult buildCacheResult(Collection<TargetGroup> allTargetGroups, Map<String, List<TargetHealthDescription>> targetGroupArnToHealths, Map<String, List<TargetGroupAttribute>> targetGroupArnToAttributes) {
    Map<String, CacheData> targetGroups = CacheHelpers.cache()
    Map<String, CacheData> loadBalancers = CacheHelpers.cache()

    List<InstanceTargetGroupState> itgStates = []
    for (TargetGroup tg : allTargetGroups) {
      // Collect health information for the target group and instance ids
      List<String> instanceIds = new ArrayList<String>()
      List<TargetHealthDescription> thds = targetGroupArnToHealths.get(tg.targetGroupArn)
      for (TargetHealthDescription thd : thds) {
        itgStates << new InstanceTargetGroupState(thd.target.id, ArnUtils.extractTargetGroupName(tg.targetGroupArn).get(), thd.targetHealth.state, thd.targetHealth.reason, thd.targetHealth.description)
        instanceIds << thd.target.id
      }

      // Get associated load balancer keys
      Collection<String> loadBalancerIds = tg.loadBalancerArns.collect {
        def lbName = ArnUtils.extractLoadBalancerName(it).get()
        Keys.getLoadBalancerKey(lbName, account.name, region, tg.vpcId, "application")
      }

      // Get target group attributes
      Map<String, String> tgAttributes = targetGroupArnToAttributes.get(tg.targetGroupArn).collectEntries {
        [(it.key): it.value]
      }

      Map<String, Object> tgCacheAttributes = objectMapper.convertValue(tg, ATTRIBUTES)
      tgCacheAttributes.put('instances', instanceIds)
      tgCacheAttributes.put('attributes', tgAttributes)
      tgCacheAttributes.loadBalancerNames = tgCacheAttributes.loadBalancerArns.collect { String lbArn ->
        ArnUtils.extractLoadBalancerName(lbArn).get()
      }
      tgCacheAttributes.remove('loadBalancerArns')

      // Cache target group
      String targetGroupId = Keys.getTargetGroupKey(tg.targetGroupName, account.name, region, tg.vpcId)
      targetGroups[targetGroupId].with {
        attributes.putAll(tgCacheAttributes)
        relationships[LOAD_BALANCERS.ns].addAll(loadBalancerIds)
      }
      for (String loadBalancerId : loadBalancerIds) {
        loadBalancers[loadBalancerId].with {
          relationships[TARGET_GROUPS.ns].add(targetGroupId)
        }
      }
    }

    // Build health cache
    // Have to do this separately because an instance can be in multiple target groups
    List<InstanceTargetGroups> itgs = InstanceTargetGroups.fromInstanceTargetGroupStates(itgStates)
    Collection<CacheData> tgHealths = []
    Collection<CacheData> instances = []

    for (InstanceTargetGroups itg in itgs) {
      String instanceId = Keys.getInstanceKey(itg.instanceId, account.name, region)
      String healthId = Keys.getInstanceHealthKey(itg.instanceId, account.name, region, healthId)
      Map<String, Object> attributes = objectMapper.convertValue(itg, ATTRIBUTES)
      Map<String, Collection<String>> relationships = [(INSTANCES.ns): [instanceId]]
      tgHealths.add(new DefaultCacheData(healthId, attributes, relationships))
      instances.add(new DefaultCacheData(instanceId, [:], [(HEALTH.ns): [healthId]]))
    }

    // Put it all into cache
    log.info("Caching ${loadBalancers.size()} load balancers in ${agentType}")
    log.info("Caching ${targetGroups.size()} target groups in ${agentType}")
    log.info("Caching ${tgHealths.size()} target group health states in ${agentType}")
    new DefaultCacheResult([
      (LOAD_BALANCERS.ns):  loadBalancers.values(),
      (TARGET_GROUPS.ns): targetGroups.values(),
      (HEALTH.ns): tgHealths,
      (INSTANCES.ns): instances
    ])
  }
}
