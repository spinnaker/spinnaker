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

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.Instance
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.frigga.Names
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.oort.data.aws.Keys

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.*
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.*
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.oort.provider.aws.AwsProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ClusterCachingAgent implements CachingAgent, OnDemandAgent {
  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  private static final Logger log = LoggerFactory.getLogger(ClusterCachingAgent)

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(SERVER_GROUPS.ns),
    INFORMATIVE.forType(APPLICATIONS.ns),
    INFORMATIVE.forType(CLUSTERS.ns),
    INFORMATIVE.forType(LOAD_BALANCERS.ns),
    INFORMATIVE.forType(LAUNCH_CONFIGS.ns),
    INFORMATIVE.forType(INSTANCES.ns)
  ] as Set)

  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper

  ClusterCachingAgent(AmazonClientProvider amazonClientProvider, NetflixAmazonCredentials account, String region, ObjectMapper objectMapper) {
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
    "${account.name}/${region}/${ClusterCachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
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
    type == "AmazonServerGroup"
  }

  @Override
  OnDemandAgent.OnDemandResult handle(Map<String, ? extends Object> data) {
    if (!data.containsKey("asgName")) {
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

    def autoScaling = amazonClientProvider.getAutoScaling(account, region, true)
    List<AutoScalingGroup> asg = autoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(data.asgName)).autoScalingGroups
    CacheResult result = buildCacheResult(asg)
    new OnDemandAgent.OnDemandResult(sourceAgentType: getAgentType(), cacheResult: result)
  }

  private Map<String, CacheData> cache() {
    [:].withDefault { String id -> new MutableCacheData(id) }
  }

  @Override
  CacheResult loadData() {
    def autoScaling = amazonClientProvider.getAutoScaling(account, region)
    def request = new DescribeAutoScalingGroupsRequest()
    List<AutoScalingGroup> asgs = []
    while (true) {
      def resp = autoScaling.describeAutoScalingGroups(request)
      asgs.addAll(resp.autoScalingGroups)
      if (resp.nextToken) {
        request.withNextToken(resp.nextToken)
      } else {
        break
      }
    }
    buildCacheResult(asgs)
  }

  private CacheResult buildCacheResult(Collection<AutoScalingGroup> asgs) {

    Map<String, CacheData> applications = cache()
    Map<String, CacheData> clusters = cache()
    Map<String, CacheData> serverGroups = cache()
    Map<String, CacheData> loadBalancers = cache()
    Map<String, CacheData> launchConfigs = cache()
    Map<String, CacheData> instances = cache()

    for (AutoScalingGroup asg : asgs) {
      AsgData data = new AsgData(asg, account.name, region)

      cacheApplication(data, applications)
      cacheCluster(data, clusters)
      cacheServerGroup(data, serverGroups)
      cacheLaunchConfig(data, launchConfigs)
      cacheInstances(data, instances)
      cacheLoadBalancers(data, loadBalancers)
    }

    new DefaultCacheResult(
      (APPLICATIONS.ns): applications.values(),
      (CLUSTERS.ns): clusters.values(),
      (SERVER_GROUPS.ns): serverGroups.values(),
      (LOAD_BALANCERS.ns): loadBalancers.values(),
      (LAUNCH_CONFIGS.ns): launchConfigs.values(),
      (INSTANCES.ns): instances.values())

  }

  private void cacheApplication(AsgData data, Map<String, CacheData> applications) {
    applications[data.appName].with {
      relationships[CLUSTERS.ns].add(data.cluster)
      relationships[SERVER_GROUPS.ns].add(data.serverGroup)
      relationships[LOAD_BALANCERS.ns].addAll(data.loadBalancerNames)
    }
  }

  private void cacheCluster(AsgData data, Map<String, CacheData> clusters) {
    clusters[data.cluster].with {
      relationships[APPLICATIONS.ns].add(data.appName)
      relationships[SERVER_GROUPS.ns].add(data.serverGroup)
      relationships[LOAD_BALANCERS.ns].addAll(data.loadBalancerNames)
    }
  }

  private void cacheServerGroup(AsgData data, Map<String, CacheData> serverGroups) {
    serverGroups[data.serverGroup].with {
      attributes.asg = objectMapper.convertValue(data.asg, ATTRIBUTES)
      attributes.region = region
      attributes.name = data.asg.autoScalingGroupName
      attributes.launchConfigName = data.asg.launchConfigurationName
      attributes.zones = data.asg.availabilityZones
      attributes.instances = data.asg.instances

      relationships[APPLICATIONS.ns].add(data.appName)
      relationships[CLUSTERS.ns].add(data.cluster)
      relationships[LOAD_BALANCERS.ns].addAll(data.loadBalancerNames)
      relationships[LAUNCH_CONFIGS.ns].add(data.launchConfig)
      relationships[INSTANCES.ns].addAll(data.instanceIds)
    }
  }

  private void cacheLaunchConfig(AsgData data, Map<String, CacheData> launchConfigs) {
    launchConfigs[data.launchConfig].with {
      relationships[SERVER_GROUPS.ns].add(data.serverGroup)
    }
  }

  private void cacheInstances(AsgData data, Map<String, CacheData> instances) {
    for (Instance instance : data.asg.instances) {
      instances[Keys.getInstanceKey(instance.instanceId, account.name, region)].with {
        Map<String, Object> instanceAttributes = objectMapper.convertValue(instance, ATTRIBUTES)
        attributes.putAll(instanceAttributes)
        relationships[SERVER_GROUPS.ns].add(data.serverGroup)
      }
    }
  }

  private void cacheLoadBalancers(AsgData data, Map<String, CacheData> loadBalancers) {
    for (String loadBalancerName : data.loadBalancerNames) {
      loadBalancers[loadBalancerName].with {
        relationships[APPLICATIONS.ns].add(data.appName)
        relationships[SERVER_GROUPS.ns].add(data.serverGroup)
      }
    }
  }

  private static class AsgData {
    final AutoScalingGroup asg
    final String appName
    final String cluster
    final String serverGroup
    final String launchConfig
    final Set<String> loadBalancerNames
    final Set<String> instanceIds

    public AsgData(AutoScalingGroup asg, String account, String region) {
      this.asg = asg

      Names name = Names.parseName(asg.autoScalingGroupName)

      appName = Keys.getApplicationKey(name.app)
      cluster = Keys.getClusterKey(name.cluster, name.app, account)
      serverGroup = Keys.getServerGroupKey(asg.autoScalingGroupName, account, region)
      launchConfig = Keys.getLaunchConfigKey(asg.launchConfigurationName, account, region)
      loadBalancerNames = (asg.loadBalancerNames.collect { Keys.getLoadBalancerKey(it, account, region) } as Set).asImmutable()
      instanceIds = (asg.instances.instanceId.collect { Keys.getInstanceKey(it, account, region) } as Set).asImmutable()
    }
  }
}
