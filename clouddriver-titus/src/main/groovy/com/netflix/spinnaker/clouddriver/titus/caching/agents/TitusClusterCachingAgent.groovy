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

package com.netflix.spinnaker.clouddriver.titus.caching.agents

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.frigga.autoscaling.AutoScalingGroupNameBuilder
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.caching.Keys
import com.netflix.spinnaker.clouddriver.titus.caching.TitusCachingProvider
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.CLUSTERS
import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.SERVER_GROUPS
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE

class TitusClusterCachingAgent implements CachingAgent, OnDemandAgent {

  private static final Logger log = LoggerFactory.getLogger(TitusClusterCachingAgent)

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(SERVER_GROUPS.ns),
    AUTHORITATIVE.forType(APPLICATIONS.ns),
    INFORMATIVE.forType(CLUSTERS.ns),
    INFORMATIVE.forType(INSTANCES.ns)
  ] as Set)

  private final TitusCloudProvider titusCloudProvider
  private final TitusClient titusClient
  private final NetflixTitusCredentials account
  private final String region
  private final ObjectMapper objectMapper
  private final Registry registry
  private final OnDemandMetricsSupport metricsSupport

  TitusClusterCachingAgent(TitusCloudProvider titusCloudProvider,
                           TitusClientProvider titusClientProvider,
                           NetflixTitusCredentials account,
                           String region,
                           ObjectMapper objectMapper,
                           Registry registry) {
    this.titusCloudProvider = titusCloudProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper
    this.titusClient = titusClientProvider.getTitusClient(account, region)
    this.registry = registry
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, "${titusCloudProvider.id}:${OnDemandAgent.OnDemandType.ServerGroup}")
  }

  @Override
  String getProviderName() {
    TitusCachingProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${TitusClusterCachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  @Override
  String getOnDemandAgentType() {
    "${getAgentType()}-OnDemand"
  }

  @Override
  OnDemandMetricsSupport getMetricsSupport() {
    return metricsSupport
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    return type == OnDemandAgent.OnDemandType.ServerGroup && cloudProvider == titusCloudProvider.id
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (!data.containsKey("serverGroupName")) {
      return null
    }
    if (!data.containsKey("account")) {
      return null
    }
    if (!data.containsKey("region")) {
      return null
    }

    if (account.name != data.account) {
      return null
    }

    if (region != data.region) {
      return null
    }

    //TODO(cfieber) - this should just load a single server group and follow the same on-demand caching behaviour as the AWS provider
    List<Job> jobs = metricsSupport.readData {
      titusClient.getAllJobs()
    }

    CacheResult result = metricsSupport.transformData { buildCacheResult(jobs) }
    def withOnDemand = new DefaultCacheResult(result.cacheResults + [onDemand: [new DefaultCacheData(
      Keys.getServerGroupKey(data.serverGroupName, data.account, data.region),
      10 * 60, // ttl is 10 minutes
      [
        cacheTime     : System.currentTimeMillis(),
        cacheResults  : "[{}]",
        processedCount: 1,
        processedTime : System.currentTimeMillis()
      ],
      [:]
    )]], result.evictions)
    new OnDemandAgent.OnDemandResult(sourceAgentType: getAgentType(), authoritativeTypes: [SERVER_GROUPS.ns], cacheResult: withOnDemand)
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    def keys = providerCache.getIdentifiers('onDemand')
    keys = keys.findResults {
      def key = Keys.parse(it)
      if (key && key.type == SERVER_GROUPS.ns && key.account == account.name && key.region == region) {
        return it
      }
      return null
    }
    return providerCache.getAll('onDemand', keys, RelationshipCacheFilter.none()).collect {
      [
        id            : it.id,
        details       : Keys.parse(it.id),
        cacheTime     : it.attributes.cacheTime,
        processedCount: it.attributes.processedCount,
        processedTime : it.attributes.processedTime
      ]
    }
  }

  static class MutableCacheData implements CacheData {
    final String id
    int ttlSeconds = -1
    final Map<String, Object> attributes = [:]
    final Map<String, Collection<String>> relationships = [:].withDefault { [] as Set }

    public MutableCacheData(String id) {
      this.id = id
    }

    @JsonCreator
    public MutableCacheData(@JsonProperty("id") String id,
                            @JsonProperty("attributes") Map<String, Object> attributes,
                            @JsonProperty("relationships") Map<String, Collection<String>> relationships) {
      this(id)
      this.attributes.putAll(attributes)
      this.relationships.putAll(relationships)
    }
  }

  private Map<String, CacheData> cache() {
    [:].withDefault { String id -> new MutableCacheData(id) }
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<Job> jobs = titusClient.getAllJobs()
    CacheResult result = buildCacheResult(jobs)
    result
  }

  private CacheResult buildCacheResult(List<Job> jobs) {
    Map<String, CacheData> applications = cache()
    Map<String, CacheData> clusters = cache()
    Map<String, CacheData> serverGroups = cache()
    Map<String, CacheData> instances = cache()

    for (Job job : jobs) {
      try {
        ServerGroupData data = new ServerGroupData(job, account.name, region)
        cacheApplication(data, applications)
        cacheCluster(data, clusters)
        cacheServerGroup(data, serverGroups)
        cacheInstances(data, instances)
      } catch (Exception ex) {
        log.error("Failed to cache ${job.name} in ${account.name}", ex)
      }
    }

    new DefaultCacheResult(
      (APPLICATIONS.ns): applications.values(),
      (CLUSTERS.ns): clusters.values(),
      (SERVER_GROUPS.ns): serverGroups.values(),
      (INSTANCES.ns): instances.values()
    )
  }

  private void cacheApplication(ServerGroupData data, Map<String, CacheData> applications) {
    applications[data.appName].with {
      attributes.name = data.name.app
      relationships[CLUSTERS.ns].add(data.cluster)
      relationships[SERVER_GROUPS.ns].add(data.serverGroup)
    }
  }

  private void cacheCluster(ServerGroupData data, Map<String, CacheData> clusters) {
    clusters[data.cluster].with {
      attributes.name = data.name.cluster
      relationships[APPLICATIONS.ns].add(data.appName)
      relationships[SERVER_GROUPS.ns].add(data.serverGroup)
    }
  }

  private void cacheServerGroup(ServerGroupData data, Map<String, CacheData> serverGroups) {
    serverGroups[data.serverGroup].with {
      attributes.job = objectMapper.convertValue(data.job, Job.class)
      attributes.tasks = data.job.tasks
      attributes.region = region
      attributes.account = account.name
      relationships[APPLICATIONS.ns].add(data.appName)
      relationships[CLUSTERS.ns].add(data.cluster)
      relationships[INSTANCES.ns].addAll(data.instanceIds)
    }
  }

  private void cacheInstances(ServerGroupData data, Map<String, CacheData> instances) {
    for (Job.TaskSummary task : data.job.tasks) {
      instances[Keys.getInstanceKey(task.id)].with {
        relationships[SERVER_GROUPS.ns].add(data.serverGroup)
      }
    }
  }

  private static class ServerGroupData {
    private final AutoScalingGroupNameBuilder asgNameBuilder;

    final Job job
    final Names name
    final String appName
    final String cluster
    final String serverGroup
    final Set<String> instanceIds
    final String region
    final String account

    public ServerGroupData(Job job, String account, String region) {
      this.job = job

      String asgName = job.name
      if (job.labels && job.labels['name']) {
        asgName = job.labels['name']
      } else {
        if (job.appName) {
          if (!asgNameBuilder) {
            asgNameBuilder = new AutoScalingGroupNameBuilder()
          }
          asgNameBuilder.setAppName(job.appName)
          asgNameBuilder.setDetail(job.jobGroupDetail)
          asgNameBuilder.setStack(job.jobGroupStack)
          String version = job.jobGroupSequence
          asgName = asgNameBuilder.buildGroupName() + ( version ? "-${version}" : '' )
        }
      }

      name = Names.parseName(asgName)
      appName = Keys.getApplicationKey(name.app)
      cluster = Keys.getClusterKey(name.cluster, name.app, account)
      region = region
      account = account
      serverGroup = Keys.getServerGroupKey(job.name, account, region)
      instanceIds = (job.tasks.id.collect { Keys.getInstanceKey(it) } as Set).asImmutable()
    }
  }

}
