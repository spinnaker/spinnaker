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
import com.fasterxml.jackson.core.type.TypeReference
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
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import com.netflix.spinnaker.clouddriver.titus.caching.utils.AwsLookupUtil
import com.netflix.spinnaker.clouddriver.titus.client.model.TaskState
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.caching.Keys
import com.netflix.spinnaker.clouddriver.titus.caching.TitusCachingProvider
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.model.TitusSecurityGroup
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.inject.Provider

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH
import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.CLUSTERS
import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.IMAGES
import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.ON_DEMAND
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
    AUTHORITATIVE.forType(INSTANCES.ns)
  ] as Set)

  private final TitusCloudProvider titusCloudProvider
  private final TitusClient titusClient
  private final NetflixTitusCredentials account
  private final String region
  private final ObjectMapper objectMapper
  private final Registry registry
  private final OnDemandMetricsSupport metricsSupport
  private final Provider<AwsLookupUtil> awsLookupUtil

  TitusClusterCachingAgent(TitusCloudProvider titusCloudProvider,
                           TitusClientProvider titusClientProvider,
                           NetflixTitusCredentials account,
                           String region,
                           ObjectMapper objectMapper,
                           Registry registry,
                           Provider<AwsLookupUtil> awsLookupUtil
  ) {
    this.titusCloudProvider = titusCloudProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper
    this.titusClient = titusClientProvider.getTitusClient(account, region)
    this.registry = registry
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, "${titusCloudProvider.id}:${OnDemandAgent.OnDemandType.ServerGroup}")
    this.awsLookupUtil = awsLookupUtil
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

    Job job = metricsSupport.readData {
      titusClient.findJobByName(data.serverGroupName)
    }

    CacheResult result = metricsSupport.transformData { buildCacheResult([job], [:], [], System.currentTimeMillis()) }

    def jsonResult = objectMapper.writeValueAsString(result.cacheResults)
    def serverGroupKey = Keys.getServerGroupKey(job.name, account.name, region)

    if (result.cacheResults.values().flatten().isEmpty()) {
      providerCache.evictDeletedItems(ON_DEMAND.ns, [serverGroupKey])
    } else {
      def cacheData = metricsSupport.onDemandStore {
        new DefaultCacheData(
          serverGroupKey,
          10 * 60, // ttl is 10 minutes,
          [
            cacheTime     : System.currentTimeMillis(),
            cacheResults  : jsonResult,
            processedCount: 0,
            processedTime : System.currentTimeMillis()
          ],
          [:])
      }
      providerCache.putCacheData(ON_DEMAND.ns, cacheData)
    }

    Map<String, Collection<String>> evictions = job ? [:] : [(SERVER_GROUPS.ns): [serverGroupKey]]

    log.info "On demand cache refresh (data: ${data}) succeeded."

    new OnDemandAgent.OnDemandResult(
      sourceAgentType: getOnDemandAgentType(),
      cacheResult: result,
      evictions: evictions
    )
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

  private Map<String, CacheData> createCache() {
    [:].withDefault { String id -> new MutableCacheData(id) }
  }

  static void cache(Map<String, List<CacheData>> cacheResults,
                    String cacheNamespace,
                    Map<String, CacheData> cacheDataById) {
    cacheResults[cacheNamespace].each {
      def existingCacheData = cacheDataById[it.id]
      if (existingCacheData) {
        existingCacheData.attributes.putAll(it.attributes)
        it.relationships.each { String relationshipName, Collection<String> relationships ->
          existingCacheData.relationships[relationshipName].addAll(relationships)
        }
      } else {
        cacheDataById[it.id] = it
      }
    }
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    Long start = System.currentTimeMillis()
    List<Job> jobs = titusClient.getAllJobs()
    List<CacheData> evictFromOnDemand = []
    List<CacheData> keepInOnDemand = []

    def serverGroupKeys = jobs.collect { job -> Keys.getServerGroupKey(job.name, account.name, region) }

    providerCache.getAll(ON_DEMAND.ns, serverGroupKeys).each { CacheData onDemandEntry ->
      if (onDemandEntry.attributes.cacheTime < start && onDemandEntry.attributes.processedCount > 0) {
        evictFromOnDemand << onDemandEntry
      } else {
        keepInOnDemand << onDemandEntry
      }
    }

    def onDemandMap = keepInOnDemand.collectEntries { CacheData onDemandEntry -> [(onDemandEntry.id): onDemandEntry] }

    CacheResult result = buildCacheResult(jobs, onDemandMap, evictFromOnDemand*.id, start)

    result.cacheResults[ON_DEMAND.ns].each { CacheData onDemandEntry ->
      onDemandEntry.attributes.processedTime = System.currentTimeMillis()
      onDemandEntry.attributes.processedCount = (onDemandEntry.attributes.processedCount ?: 0) + 1
    }
    result
  }

  private CacheResult buildCacheResult(List<Job> jobs, Map<String, CacheData> onDemandKeep, List<String> onDemandEvict, Long start) {
    Map<String, CacheData> applications = createCache()
    Map<String, CacheData> clusters = createCache()
    Map<String, CacheData> serverGroups = createCache()
    Map<String, CacheData> instances = createCache()

    Map<String, TitusSecurityGroup> titusSecurityGroupCache = [:]

    for (Job job : jobs) {
      def onDemandData = onDemandKeep ? onDemandKeep[Keys.getServerGroupKey(job.name, account.name, region)] : null
      if (onDemandData && onDemandData.attributes.cacheTime >= start) {
        Map<String, List<CacheData>> cacheResults = objectMapper.readValue(onDemandData.attributes.cacheResults as String,
          new TypeReference<Map<String, List<MutableCacheData>>>() {})
        cache(cacheResults, APPLICATIONS.ns, applications)
        cache(cacheResults, CLUSTERS.ns, clusters)
        cache(cacheResults, SERVER_GROUPS.ns, serverGroups)
        cache(cacheResults, INSTANCES.ns, instances)
      } else {
        try {
          ServerGroupData data = new ServerGroupData(job, account.name, region)
          cacheApplication(data, applications)
          cacheCluster(data, clusters)
          cacheServerGroup(data, serverGroups, instances, titusSecurityGroupCache)
        } catch (Exception ex) {
          log.error("Failed to cache ${job.name} in ${account.name}", ex)
        }
      }
    }

    new DefaultCacheResult(
      [(APPLICATIONS.ns) : applications.values(),
       (CLUSTERS.ns)     : clusters.values(),
       (SERVER_GROUPS.ns): serverGroups.values(),
       (INSTANCES.ns)    : instances.values(),
       (ON_DEMAND.ns)    : onDemandKeep.values()
      ],
      [(ON_DEMAND.ns): onDemandEvict]
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

  private void cacheServerGroup(ServerGroupData data, Map<String, CacheData> serverGroups, Map<String, CacheData> instances, Map titusSecurityGroupCache) {
    serverGroups[data.serverGroup].with {
      Job job = objectMapper.convertValue(data.job, Job.class)
      resolveAwsDetails(titusSecurityGroupCache, job)
      attributes.job = job
      attributes.tasks = data.job.tasks
      attributes.region = region
      attributes.account = account.name
      relationships[APPLICATIONS.ns].add(data.appName)
      relationships[CLUSTERS.ns].add(data.cluster)
      relationships[INSTANCES.ns].addAll(data.instanceIds)
      for (Job.TaskSummary task : job.tasks) {
        def instanceData = new InstanceData(job, task, account.name, region)
        cacheInstance(instanceData, instances)
      }
    }
  }

  private void cacheInstance(InstanceData data, Map<String, CacheData> instances) {
    instances[data.instanceId].with {
      Job.TaskSummary task = objectMapper.convertValue(data.task, Job.TaskSummary)
      attributes.task = task
      Map<String, Object> job = objectMapper.convertValue(data.job, Map)
      job.remove('tasks')
      attributes.job = job
      attributes.put(HEALTH.ns, [getTitusHealth(task)])
      relationships[IMAGES.ns].add(data.imageId)
      if (data.serverGroup) {
        relationships[SERVER_GROUPS.ns].add(data.serverGroup)
      } else {
        relationships[SERVER_GROUPS.ns].clear()
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
          asgName = asgNameBuilder.buildGroupName() + (version ? "-${version}" : '')
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

  private void resolveAwsDetails(Map<String, TitusSecurityGroup> titusSecurityGroupCache,
                                 Job job) {
    Set<TitusSecurityGroup> securityGroups = awsLookupUtil.get().lookupSecurityGroupNames(
      titusSecurityGroupCache, account.name, region, job.securityGroups
    )
    job.securityGroupDetails = securityGroups
  }

  private static class InstanceData {
    private final Job job
    private final Job.TaskSummary task
    private final String instanceId
    private final String serverGroup
    private final String imageId

    public InstanceData(Job job, Job.TaskSummary task, String account, String region) {
      this.job = job
      this.task = task
      this.instanceId = Keys.getInstanceKey(task.id)
      this.serverGroup = job.name
      this.imageId = "${job.applicationName}:${job.version}"
    }
  }

  private Map<String, String> getTitusHealth(Job.TaskSummary task) {
    TaskState taskState = task.state
    HealthState healthState = HealthState.Unknown
    if (taskState in [TaskState.STOPPED, TaskState.FAILED, TaskState.CRASHED, TaskState.FINISHED, TaskState.DEAD, TaskState.TERMINATING]) {
      healthState = HealthState.Down
    } else if (taskState in [TaskState.STARTING, TaskState.DISPATCHED, TaskState.PENDING, TaskState.QUEUED]) {
      healthState = HealthState.Starting
    } else {
      healthState = HealthState.Unknown
    }
    [type: 'Titus', healthClass: 'platform', state: healthState.toString()]
  }

}
