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

import com.amazonaws.services.elasticloadbalancingv2.model.TargetTypeEnum
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.util.JsonFormat
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
import com.netflix.spinnaker.clouddriver.aws.data.ArnUtils
import com.netflix.spinnaker.clouddriver.aws.data.Keys as AwsKeys
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import com.netflix.spinnaker.clouddriver.titus.caching.Keys
import com.netflix.spinnaker.clouddriver.titus.caching.TitusCachingProvider
import com.netflix.spinnaker.clouddriver.titus.caching.utils.AwsLookupUtil
import com.netflix.spinnaker.clouddriver.titus.client.TitusAutoscalingClient
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.TitusLoadBalancerClient
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.client.model.TaskState
import com.netflix.spinnaker.clouddriver.titus.client.model.Task
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.model.TitusSecurityGroup
import com.netflix.titus.grpc.protogen.ScalingPolicy
import com.netflix.titus.grpc.protogen.ScalingPolicyResult
import com.netflix.titus.grpc.protogen.ScalingPolicyStatus
import com.netflix.titus.grpc.protogen.ScalingPolicyStatus.ScalingPolicyState
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.inject.Provider

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.ON_DEMAND
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.TARGET_GROUPS
import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.*

class TitusClusterCachingAgent implements CachingAgent, CustomScheduledAgent, OnDemandAgent {

  private static final Logger log = LoggerFactory.getLogger(TitusClusterCachingAgent)

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(SERVER_GROUPS.ns),
    AUTHORITATIVE.forType(APPLICATIONS.ns),
    AUTHORITATIVE.forType(INSTANCES.ns),
    INFORMATIVE.forType(CLUSTERS.ns),
    INFORMATIVE.forType(TARGET_GROUPS.ns)
  ] as Set)

  private final TitusCloudProvider titusCloudProvider
  private final TitusClient titusClient
  private final TitusAutoscalingClient titusAutoscalingClient
  private final TitusLoadBalancerClient titusLoadBalancerClient
  private final NetflixTitusCredentials account
  private final TitusRegion titusRegion
  private final String region
  private final ObjectMapper objectMapper
  private final OnDemandMetricsSupport metricsSupport
  private final Provider<AwsLookupUtil> awsLookupUtil
  private final long pollIntervalMillis
  private final long timeoutMillis

  TitusClusterCachingAgent(TitusCloudProvider titusCloudProvider,
                           TitusClientProvider titusClientProvider,
                           NetflixTitusCredentials account,
                           TitusRegion titusRegion,
                           ObjectMapper objectMapper,
                           Registry registry,
                           Provider<AwsLookupUtil> awsLookupUtil,
                           pollIntervalMillis,
                           timeoutMillis) {
    this.account = account
    this.titusRegion = titusRegion
    this.region = titusRegion.name

    this.titusCloudProvider = titusCloudProvider
    this.objectMapper = objectMapper
    this.metricsSupport = new OnDemandMetricsSupport(
      registry,
      this,
      "${titusCloudProvider.id}:${OnDemandAgent.OnDemandType.ServerGroup}" as String
    )
    this.titusClient = titusClientProvider.getTitusClient(account, region)
    this.titusAutoscalingClient = titusClientProvider.getTitusAutoscalingClient(account, region)
    this.titusLoadBalancerClient = titusClientProvider.getTitusLoadBalancerClient(account, region)
    this.awsLookupUtil = awsLookupUtil
    this.pollIntervalMillis = pollIntervalMillis
    this.timeoutMillis = timeoutMillis
  }

  @Override
  String getProviderName() {
    TitusCachingProvider.PROVIDER_NAME
  }

  @Override
  String getOnDemandAgentType() {
    return "${getAgentType()}-OnDemand"
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
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    if (["serverGroupName", "account", "region"].any { !data.containsKey(it) }) {
      return null
    }

    if (account.name != data.account) {
      return null
    }

    if (region != data.region) {
      return null
    }

    Job job = metricsSupport.readData {
      titusClient.findJobByName(data.serverGroupName as String)
    }

    return onDemand(providerCache, job, data)
  }

  /**
   * Avoid writing cache results to both ON_DEMAND and SERVER_GROUPS, etc.
   *
   * By writing a minimal record to ON_DEMAND only, we eliminate significant overhead (redis and network) at the cost
   * of an increase in time before a change becomes visible in the UI.
   *
   * A change will not be visible until a caching cycle has completed.
   */
  private OnDemandResult onDemand(ProviderCache providerCache, Job job, Map<String, ?> data) {
    def serverGroupKey = Keys.getServerGroupKey(data.serverGroupName as String, account.name, region)
    def cacheResults = [:]

    if (!job) {
      providerCache.evictDeletedItems(ON_DEMAND.ns, [serverGroupKey])
    } else {
      def cacheData = metricsSupport.onDemandStore {
        new DefaultCacheData(
          serverGroupKey,
          10 * 60, // ttl is 10 minutes,
          [
            cacheTime   : new Date(),
            cacheResults: [:]
          ],
          [:]
        )
      }

      cacheResults[ON_DEMAND.ns.toString()] = [cacheData]
    }

    Map<String, Collection<String>> evictions = job ? [:] : [(SERVER_GROUPS.ns): [serverGroupKey]]

    log.info("minimal onDemand cache refresh (data: ${data}, evictions: ${evictions})")
    return new OnDemandResult(
      sourceAgentType: getOnDemandAgentType(),
      cacheResult: new DefaultCacheResult(cacheResults),
      evictions: evictions
    )
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    Set<String> keys = providerCache.getIdentifiers('onDemand').findAll {
      def key = Keys.parse(it)
      return key && key.type == SERVER_GROUPS.ns && key.account == account.name && key.region == region
    }

    return fetchPendingOnDemandRequests(providerCache, keys)
  }

  @Override
  Map pendingOnDemandRequest(ProviderCache providerCache, String id) {
    def pendingOnDemandRequests = fetchPendingOnDemandRequests(providerCache, [id])
    return pendingOnDemandRequests?.getAt(0)
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
  Optional<Map<String, String>> getCacheKeyPatterns() {
    return [
      (SERVER_GROUPS.ns): Keys.getServerGroupKey('*', '*', account.name, region)
    ]
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

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    Long start = System.currentTimeMillis()
    List<Job> jobs = titusClient.getAllJobsWithTasks()

    List<CacheData> evictFromOnDemand = []
    List<CacheData> keepInOnDemand = []

    def serverGroupKeys = jobs.collect { job -> Keys.getServerGroupKey(job.name, account.name, region) } as Set<String>
    def pendingOnDemandRequestKeys = providerCache
      .filterIdentifiers(ON_DEMAND.ns, Keys.getServerGroupKey("*", "*", account.name, region))
      .findAll { serverGroupKeys.contains(it) }

    def pendingOnDemandRequestsForServerGroups = providerCache.getAll(ON_DEMAND.ns, pendingOnDemandRequestKeys)
    pendingOnDemandRequestsForServerGroups.each { CacheData onDemandEntry ->
      if (onDemandEntry.attributes.cacheTime < start && onDemandEntry.attributes.processedCount > 0) {
        evictFromOnDemand << onDemandEntry
      } else {
        keepInOnDemand << onDemandEntry
      }
    }

    def onDemandMap = keepInOnDemand.collectEntries { CacheData onDemandEntry -> [(onDemandEntry.id): onDemandEntry] }
    CacheResult result = buildCacheResult(jobs, onDemandMap, evictFromOnDemand*.id)

    result.cacheResults[ON_DEMAND.ns].each { CacheData onDemandEntry ->
      onDemandEntry.attributes.processedTime = System.currentTimeMillis()
      onDemandEntry.attributes.processedCount = (onDemandEntry.attributes.processedCount ?: 0) + 1
    }

    return result
  }

  private Collection<Map> fetchPendingOnDemandRequests(ProviderCache providerCache, Collection<String> keys) {
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

  private CacheResult buildCacheResult(List<Job> jobs,
                                       Map<String, CacheData> onDemandKeep = [:],
                                       List<String> onDemandEvict = []) {
    Map<String, CacheData> applications = createCache()
    Map<String, CacheData> clusters = createCache()
    Map<String, CacheData> serverGroups = createCache()
    Map<String, CacheData> instances = createCache()
    Map<String, CacheData> targetGroups = createCache()
    List<ScalingPolicyResult> allScalingPolicies = titusAutoscalingClient ? titusAutoscalingClient.getAllScalingPolicies() : []
    // Ignore policies in a Deleted state (may need to revisit)
    Map<String, String> allLoadBalancers = [:]
    try {
      allLoadBalancers = titusLoadBalancerClient ? titusLoadBalancerClient.allLoadBalancers : [:]
    } catch (Exception e) {
      log.error("Failed to load load balancers for ${account.name}:${region}", e)
    }
    List cacheablePolicyStates = [ScalingPolicyState.Applied, ScalingPolicyState.Deleting]
    Map<String, TitusSecurityGroup> titusSecurityGroupCache = [:]

    def serverGroupDatas = jobs.collect { job ->
      List<ScalingPolicyData> scalingPolicies = allScalingPolicies.findResults {
        it.jobId == job.id && cacheablePolicyStates.contains(it.policyState.state) ?
          new ScalingPolicyData(id: it.id.id, policy: it.scalingPolicy, status: it.policyState) :
          null
      }
      List<String> loadBalancers = allLoadBalancers.get(job.id) ?: []
      return new ServerGroupData(job, scalingPolicies, loadBalancers, account.name, region, account.stack)
    }

    serverGroupDatas.each { data ->
      cacheApplication(data, applications)
      cacheCluster(data, clusters)
      cacheTargetGroups(data, targetGroups)
    }

    // caching _all_ jobs at once allows us to optimize the security group lookups
    cacheServerGroups(serverGroupDatas, serverGroups, instances, titusSecurityGroupCache)

    new DefaultCacheResult(
      [(APPLICATIONS.ns) : applications.values(),
       (CLUSTERS.ns)     : clusters.values(),
       (SERVER_GROUPS.ns): serverGroups.values(),
       (INSTANCES.ns)    : instances.values(),
       (TARGET_GROUPS.ns): targetGroups.values(),
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
      relationships[TARGET_GROUPS.ns].addAll(data.targetGroupKeys)
    }
  }

  private void cacheTargetGroups(ServerGroupData data, Map<String, CacheData> targetGroups) {
    for (String targetGroupKey : data.targetGroupKeys) {
      targetGroups[targetGroupKey].with {
        relationships[APPLICATIONS.ns].add(data.appName)
        relationships[SERVER_GROUPS.ns].add(data.serverGroup)
      }
    }
  }

  private void cacheCluster(ServerGroupData data, Map<String, CacheData> clusters) {
    clusters[data.cluster].with {
      attributes.name = data.name.cluster
      relationships[APPLICATIONS.ns].add(data.appName)
      relationships[SERVER_GROUPS.ns].add(data.serverGroup)
      relationships[TARGET_GROUPS.ns].addAll(data.targetGroupKeys)
    }
  }

  private void cacheServerGroups(List<ServerGroupData> datas,
                                 Map<String, CacheData> serverGroups,
                                 Map<String, CacheData> instances,
                                 Map titusSecurityGroupCache) {
    def allJobs = datas*.job

    datas.each { data ->
      serverGroups[data.serverGroup].with {
        try {
          Job job = objectMapper.convertValue(data.job, Job.class)
          List<Map> policies = data.scalingPolicies ? data.scalingPolicies.collect {
            // There is probably a better way to convert a protobuf to a Map, but I don't know what it is
            [
              id    : it.id,
              status: [state: it.status.state.name(), reason: it.status.pendingReason],
              policy: objectMapper.readValue(JsonFormat.printer().print(it.policy), Map)
            ]
          } : []

          // tasks are cached independently as instances so avoid the overhead of also storing on the serialized job
          def jobTasks = job.tasks
          job.tasks = []
          attributes.job = job
          attributes.scalingPolicies = policies
          attributes.tasks = jobTasks.collect { [id: it.id, instanceId: it.instanceId] }
          attributes.region = region
          attributes.account = account.name
          attributes.targetGroups = data.targetGroupNames
          relationships[APPLICATIONS.ns].add(data.appName)
          relationships[CLUSTERS.ns].add(data.cluster)
          relationships[INSTANCES.ns].addAll(data.instanceIds)
          relationships[TARGET_GROUPS.ns].addAll(data.targetGroupKeys)
          for (Task task : jobTasks) {
            def instanceData = new InstanceData(job, task, account.name, region, account.stack)
            cacheInstance(instanceData, instances)
          }
        } catch (Exception e) {
          log.error("Failed to cache ${data.job.name} in ${account.name}", e)
        }
      }
    }
  }

  private void cacheInstance(InstanceData data, Map<String, CacheData> instances) {
    instances[data.instanceId].with {
      Task task = objectMapper.convertValue(data.task, Task)
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

  private class ScalingPolicyData {
    String id
    ScalingPolicy policy
    ScalingPolicyStatus status
  }

  private class ServerGroupData {

    final Job job
    List<ScalingPolicyData> scalingPolicies
    final Names name
    final String appName
    final String cluster
    final String serverGroup
    final Set<String> instanceIds
    final String region
    final Set<String> targetGroupKeys
    final Set<String> targetGroupNames
    final String account

    ServerGroupData(Job job, List<ScalingPolicyData> scalingPolicies, List<String> targetGroups, String account, String region, String stack) {
      this.job = job
      this.scalingPolicies = scalingPolicies

      String asgName = job.name
      if (job.labels && job.labels['name']) {
        asgName = job.labels['name']
      } else {
        if (job.appName) {
          def asgNameBuilder = new AutoScalingGroupNameBuilder()
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
      this.region = region
      this.account = account
      serverGroup = Keys.getServerGroupKey(job.name, account, region)
      instanceIds = (job.tasks.id.collect {
        Keys.getInstanceKey(it, getAwsAccountId(account, region), stack, region)
      } as Set).asImmutable()

      targetGroupNames = (targetGroups.collect {
        ArnUtils.extractTargetGroupName(it).get()
      } as Set).asImmutable()

      targetGroupKeys = (targetGroupNames.collect {
        AwsKeys.getTargetGroupKey(it, getAwsAccountName(account, region), region, TargetTypeEnum.Ip.toString(), getAwsVpcId(account, region))
      } as Set).asImmutable()

    }
  }

  private String getAwsAccountId(String account, String region) {
    awsLookupUtil.get().awsAccountId(account, region)
  }

  private String getAwsAccountName(String account, String region) {
    awsLookupUtil.get().awsAccountName(account, region)
  }

  private String getAwsVpcId(String account, String region) {
    awsLookupUtil.get().awsVpcId(account, region)
  }

  private class InstanceData {
    private final Job job
    private final Task task
    private final String instanceId
    private final String serverGroup
    private final String imageId

    public InstanceData(Job job, Task task, String account, String region, String stack) {
      this.job = job
      this.task = task
      this.instanceId = Keys.getInstanceKey(task.id, getAwsAccountId(account, region), stack, region)
      this.serverGroup = job.name
      this.imageId = "${job.applicationName}:${job.version}"
    }
  }

  private Map<String, String> getTitusHealth(Task task) {
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

  @Override
  public long getPollIntervalMillis() {
    return pollIntervalMillis
  }

  @Override
  public long getTimeoutMillis() {
    return timeoutMillis
  }

}
