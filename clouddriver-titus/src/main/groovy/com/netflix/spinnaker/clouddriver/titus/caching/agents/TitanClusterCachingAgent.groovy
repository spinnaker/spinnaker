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
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.titus.TitanClientProvider
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitanCredentials
import com.netflix.spinnaker.clouddriver.titus.caching.Keys
import com.netflix.spinnaker.clouddriver.titus.caching.TitanCachingProvider
import com.netflix.titanclient.TitanClient
import com.netflix.titanclient.model.Job
import com.netflix.titanclient.model.Task
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static Keys.Namespace.APPLICATIONS
import static Keys.Namespace.CLUSTERS
import static Keys.Namespace.INSTANCES
import static Keys.Namespace.SERVER_GROUPS
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE

class TitanClusterCachingAgent implements CachingAgent { //, OnDemandAgent {

  private static final Logger log = LoggerFactory.getLogger(TitanClusterCachingAgent)

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(SERVER_GROUPS.ns),
    AUTHORITATIVE.forType(APPLICATIONS.ns),
    INFORMATIVE.forType(CLUSTERS.ns),
    INFORMATIVE.forType(INSTANCES.ns)
  ] as Set)

  private final TitanClient titanClient
  private final NetflixTitanCredentials account
  private final String region
  private final ObjectMapper objectMapper

  TitanClusterCachingAgent(TitanClientProvider titanClientProvider,
                           NetflixTitanCredentials account,
                           String region,
                           ObjectMapper objectMapper) {
    this.account = account
    this.region = region
    this.objectMapper = objectMapper
    this.titanClient = titanClientProvider.getTitanClient(account, region)
  }

  @Override
  String getProviderName() {
    TitanCachingProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${TitanClusterCachingAgent.simpleName}"
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
    List<Job> jobs = titanClient.getAllJobs()
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
      attributes.name = data.job.name
      attributes.tasks = data.job.tasks
      relationships[APPLICATIONS.ns].add(data.appName)
      relationships[CLUSTERS.ns].add(data.cluster)
      relationships[INSTANCES.ns].addAll(data.instanceIds)
    }
  }

  private void cacheInstances(ServerGroupData data, Map<String, CacheData> instances) {
    for (Task task : data.job.tasks) {
      instances[Keys.getInstanceKey(task.id, account.name, region)].with {
        relationships[SERVER_GROUPS.ns].add(data.serverGroup)
      }
    }
  }

  private static class ServerGroupData {
    final Job job
    final Names name
    final String appName
    final String cluster
    final String serverGroup
    final Set<String> instanceIds

    public ServerGroupData(Job job, String account, String region) {
      this.job = job
      name = Names.parseName(job.name)
      appName = Keys.getApplicationKey(name.app)
      cluster = Keys.getClusterKey(name.cluster, name.app, account)
      serverGroup = Keys.getServerGroupKey(job.name, account, region)
      instanceIds = (job.tasks.id.collect { Keys.getInstanceKey(it, account, region) } as Set).asImmutable()
    }
  }

}
