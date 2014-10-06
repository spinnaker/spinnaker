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
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.frigga.Names
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.oort.model.Instance
import com.netflix.spinnaker.oort.provider.aws.AwsProvider.Identifiers

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.*
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.oort.model.Application
import com.netflix.spinnaker.oort.model.Cluster
import com.netflix.spinnaker.oort.model.LoadBalancer
import com.netflix.spinnaker.oort.model.ServerGroup
import com.netflix.spinnaker.oort.provider.aws.AwsProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ClusterCachingAgent implements CachingAgent {

  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  private static final Logger log = LoggerFactory.getLogger(ClusterCachingAgent)

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(ServerGroup.DATA_TYPE),
    INFORMATIVE.forType(Application.DATA_TYPE),
    INFORMATIVE.forType(Cluster.DATA_TYPE),
    INFORMATIVE.forType(LoadBalancer.DATA_TYPE),
    INFORMATIVE.forType(AwsProvider.LAUNCH_CONFIG_TYPE),
    INFORMATIVE.forType(Instance.DATA_TYPE)
  ] as Set)

  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper
  final Identifiers identifiers

  ClusterCachingAgent(AmazonClientProvider amazonClientProvider, NetflixAmazonCredentials account, String region, ObjectMapper objectMapper) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper
    identifiers = new Identifiers(account.name, region)
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

    Closure<Map<String, CacheData>> cache = {
      [:].withDefault { String id -> new MutableCacheData(id) }
    }

    Map<String, CacheData> applications = cache()
    Map<String, CacheData> clusters = cache()
    Map<String, CacheData> serverGroups = cache()
    Map<String, CacheData> loadBalancers = cache()
    Map<String, CacheData> launchConfigs = cache()
    Map<String, CacheData> instances = cache()

    for (AutoScalingGroup asg : asgs) {
      AsgData data = new AsgData(asg, identifiers)

      cacheApplication(data, applications)
      cacheCluster(data, clusters)
      cacheServerGroup(data, serverGroups)
      cacheLaunchConfig(data, launchConfigs)
      cacheInstances(data, instances)
      cacheLoadBalancers(data, loadBalancers)
    }

    new DefaultCacheResult(
      (Application.DATA_TYPE): applications.values(),
      (Cluster.DATA_TYPE): clusters.values(),
      (ServerGroup.DATA_TYPE): serverGroups.values(),
      (LoadBalancer.DATA_TYPE): loadBalancers.values(),
      (AwsProvider.LAUNCH_CONFIG_TYPE): launchConfigs.values(),
      (Instance.DATA_TYPE): instances.values())

  }

  private void cacheApplication(AsgData data, Map<String, CacheData> applications) {
    applications[data.appName].with {
      relationships[Cluster.DATA_TYPE].add(data.cluster)
      relationships[ServerGroup.DATA_TYPE].add(data.serverGroup)
      relationships[LoadBalancer.DATA_TYPE].addAll(data.loadBalancerNames)
    }
  }

  private void cacheCluster(AsgData data, Map<String, CacheData> clusters) {
    clusters[data.cluster].with {
      relationships[Application.DATA_TYPE].add(data.appName)
      relationships[ServerGroup.DATA_TYPE].add(data.serverGroup)
      relationships[LoadBalancer.DATA_TYPE].addAll(data.loadBalancerNames)
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

      relationships[Application.DATA_TYPE].add(data.appName)
      relationships[Cluster.DATA_TYPE].add(data.cluster)
      relationships[LoadBalancer.DATA_TYPE].addAll(data.loadBalancerNames)
      relationships[AwsProvider.LAUNCH_CONFIG_TYPE].add(data.launchConfig)
      relationships[Instance.DATA_TYPE].addAll(data.instanceIds)
    }
  }

  private void cacheLaunchConfig(AsgData data, Map<String, CacheData> launchConfigs) {
    launchConfigs[data.launchConfig].with {
      relationships[ServerGroup.DATA_TYPE].add(data.serverGroup)
    }
  }

  private void cacheInstances(AsgData data, Map<String, CacheData> instances) {
    for (com.amazonaws.services.autoscaling.model.Instance instance : data.asg.instances) {
      instances[identifiers.instanceId(instance.instanceId)].with {
        attributes.putAll(objectMapper.convertValue(instance, ATTRIBUTES))
        relationships[ServerGroup.DATA_TYPE].add(data.serverGroup)
      }
    }
  }

  private void cacheLoadBalancers(AsgData data, Map<String, CacheData> loadBalancers) {
    for (String loadBalancerName : data.loadBalancerNames) {
      loadBalancers[loadBalancerName].with {
        relationships[Application.DATA_TYPE].add(data.appName)
        relationships[ServerGroup.DATA_TYPE].add(data.serverGroup)
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

    public AsgData(AutoScalingGroup asg, Identifiers identifiers) {
      this.asg = asg

      Names name = Names.parseName(asg.autoScalingGroupName)

      appName = name.app.toLowerCase()
      cluster = identifiers.clusterId(name.cluster)
      serverGroup = identifiers.serverGroupId(asg.autoScalingGroupName)
      launchConfig = identifiers.launchConfigId(asg.launchConfigurationName)
      loadBalancerNames = (asg.loadBalancerNames.collect(identifiers.&loadBalancerId) as Set).asImmutable()
      instanceIds = (asg.instances.instanceId.collect(identifiers.&instanceId) as Set).asImmutable()
    }
  }
}
