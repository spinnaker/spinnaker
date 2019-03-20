/*
 * Copyright 2015 Netflix, Inc.
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

import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.InstanceState
import com.amazonaws.services.ec2.model.InstanceStateName
import com.amazonaws.services.ec2.model.StateReason
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.common.collect.Lists
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS

class InstanceCachingAgent implements CachingAgent, AccountAware, DriftMetric {
  final Logger log = LoggerFactory.getLogger(getClass())
  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(INSTANCES.ns),
    INFORMATIVE.forType(SERVER_GROUPS.ns),
    INFORMATIVE.forType(IMAGES.ns)
  ] as Set)

  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper
  final Registry registry

  InstanceCachingAgent(AmazonClientProvider amazonClientProvider, NetflixAmazonCredentials account, String region, ObjectMapper objectMapper, Registry registry) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    this.registry = registry
  }

  @Override
  String getProviderName() {
    AwsProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${InstanceCachingAgent.simpleName}"
  }

  @Override
  String getAccountName() {
    account.name
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  static class MutableCacheData implements CacheData {
    final String id
    int ttlSeconds = -1
    final Map<String, Object> attributes = [:]
    final Map<String, Collection<String>> relationships = [:].withDefault { [] as Set }
    public MutableCacheData(String id) {
      this.id = id
    }
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")

    def amazonEC2 = amazonClientProvider.getAmazonEC2(account, region)

    Long start = null
    def request = new DescribeInstancesRequest().withMaxResults(500)
    List<Instance> awsInstances = []
    while (true) {
      def resp = amazonEC2.describeInstances(request)
      if (account.eddaEnabled) {
        start = amazonClientProvider.lastModified ?: 0
      }
      awsInstances.addAll(resp.reservations.collectMany { it.instances })
      if (resp.nextToken) {
        request.withNextToken(resp.nextToken)
      } else {
        break
      }
    }

    Closure<Map<String, CacheData>> cache = {
      [:].withDefault { String id -> new MutableCacheData(id) }
    }

    Map<String, CacheData> serverGroups = cache()
    Map<String, CacheData> instances = cache()
    Map<String, CacheData> images = cache()

    List<String> skipIds =  []

    Lists.partition(awsInstances, 1000).each { List<Instance> partition ->
      Map<String, Map<String, Object>> convertedInstancesById = ((List<Map>) objectMapper.convertValue(
        partition,
        new TypeReference<List<Map<String, Object>>>() {}
      )).collectEntries {
        [it.instanceId, it]
      }

      partition.each { Instance instance ->
        def data = new InstanceData(instance, account.name, region)
        if (instances.containsKey(data.instanceId)) {
          log.warn("Duplicate instance for ${data.instanceId}")
        }
        if (data.cache) {
          cacheImage(data, images)
          cacheServerGroup(data, serverGroups)
          cacheInstance(data, convertedInstancesById.get(data.instance.instanceId), instances)
        } else {
          skipIds.add(data.instance.instanceId)
        }
      }
    }

    recordDrift(start)
    log.info("Caching ${instances.size()} instances in ${agentType}")
    log.info("Caching ${serverGroups.size()} server groups in ${agentType}")
    log.info("Caching ${images.size()} images in ${agentType}")

    log.info("Skipping ${skipIds.size()} non-running instances in ${agentType}")
    log.debug("Skipped instanceIds in ${agentType}: ${skipIds}")

    new DefaultCacheResult(
      (SERVER_GROUPS.ns): serverGroups.values(),
      (INSTANCES.ns): instances.values(),
      (IMAGES.ns): images.values())
  }

  private void cacheImage(InstanceData data, Map<String, CacheData> images) {
    images[data.imageId].with {
      relationships[INSTANCES.ns].add(data.instanceId)
      if (data.serverGroup) {
        relationships[SERVER_GROUPS.ns].add(data.serverGroup)
      }
    }
  }

  private void cacheServerGroup(InstanceData data, Map<String, CacheData> serverGroups) {
    if (data.serverGroup) {
      serverGroups[data.serverGroup].with {
        relationships[INSTANCES.ns].add(data.instanceId)
        relationships[IMAGES.ns].add(data.imageId)
      }
    }
  }

  private void cacheInstance(InstanceData data, Map<String, Object> instanceAttributes, Map<String, CacheData> instances) {
    instances[data.instanceId].with {
      attributes.putAll(instanceAttributes)
      attributes.put(HEALTH.ns, [getAmazonHealth(data.instance)])
      relationships[IMAGES.ns].add(data.imageId)
      if (data.serverGroup) {
        relationships[SERVER_GROUPS.ns].add(data.serverGroup)

        def application = Keys.parse(data.serverGroup).get("application")
        if (application != null) {
          attributes.put("application", application)
        }
      } else {
        relationships[SERVER_GROUPS.ns].clear()
      }
    }
  }

  private Map<String, String>  getAmazonHealth(Instance instance) {
    InstanceState state = instance.state
    StateReason stateReason = instance.stateReason
    HealthState amazonState = state?.name == InstanceStateName.Running.toString() ? HealthState.Unknown : HealthState.Down
    Map<String, String> awsInstanceHealth = [
      type: 'Amazon',
      healthClass: 'platform',
      state: amazonState.toString()
    ]
    if (stateReason) {
      awsInstanceHealth.description = stateReason.message
    }
    awsInstanceHealth
  }

  private static class InstanceData {
    static final String ASG_TAG_NAME = "aws:autoscaling:groupName"
    static final String SHUTTING_DOWN = InstanceStateName.ShuttingDown.toString()
    static final String TERMINATED = InstanceStateName.Terminated.toString()

    final Instance instance
    final String instanceId
    final String serverGroup
    final String imageId
    final boolean cache

    public InstanceData(Instance instance, String account, String region) {
      this.instance = instance
      cache = !(instance.state.name == SHUTTING_DOWN || instance.state.name == TERMINATED)
      this.instanceId = Keys.getInstanceKey(instance.instanceId, account, region)
      String sgTag = instance.tags?.find { it.key == ASG_TAG_NAME }?.value
      this.serverGroup = sgTag ? Keys.getServerGroupKey(sgTag, account, region) : null
      this.imageId = Keys.getImageKey(instance.imageId, account, region)
    }

  }
}
