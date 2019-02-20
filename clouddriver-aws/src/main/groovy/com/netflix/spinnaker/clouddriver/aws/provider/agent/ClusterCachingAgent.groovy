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

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.DescribePoliciesRequest
import com.amazonaws.services.autoscaling.model.DescribeScheduledActionsRequest
import com.amazonaws.services.autoscaling.model.Instance
import com.amazonaws.services.autoscaling.model.ScalingPolicy
import com.amazonaws.services.autoscaling.model.ScheduledUpdateGroupAction
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsRequest
import com.amazonaws.services.cloudwatch.model.MetricAlarm
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest
import com.amazonaws.services.ec2.model.Subnet
import com.amazonaws.services.elasticloadbalancingv2.model.TargetTypeEnum
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.frigga.Names
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.data.ArnUtils
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.*
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.*
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider

class ClusterCachingAgent implements CachingAgent, OnDemandAgent, AccountAware, DriftMetric {
  final Logger log = LoggerFactory.getLogger(getClass())

  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(CLUSTERS.ns),
    AUTHORITATIVE.forType(SERVER_GROUPS.ns),
    AUTHORITATIVE.forType(APPLICATIONS.ns),
    INFORMATIVE.forType(LOAD_BALANCERS.ns),
    INFORMATIVE.forType(TARGET_GROUPS.ns),
    INFORMATIVE.forType(LAUNCH_CONFIGS.ns),
    INFORMATIVE.forType(INSTANCES.ns)
  ] as Set)

  final AmazonCloudProvider amazonCloudProvider
  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper
  final Registry registry
  final EddaTimeoutConfig eddaTimeoutConfig

  final OnDemandMetricsSupport metricsSupport

  ClusterCachingAgent(AmazonCloudProvider amazonCloudProvider,
                      AmazonClientProvider amazonClientProvider,
                      NetflixAmazonCredentials account,
                      String region,
                      ObjectMapper objectMapper,
                      Registry registry,
                      EddaTimeoutConfig eddaTimeoutConfig) {
    this.amazonCloudProvider = amazonCloudProvider
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    this.registry = registry
    this.eddaTimeoutConfig = eddaTimeoutConfig
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, "${amazonCloudProvider.id}:${OnDemandAgent.OnDemandType.ServerGroup}")
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
  String getAccountName() {
    account.name
  }

  @Override
  String getOnDemandAgentType() {
    "${getAgentType()}-OnDemand"
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

  static class AmazonClients {
    final AmazonAutoScaling autoScaling
    final AmazonEC2 amazonEC2
    final AmazonCloudWatch amazonCloudWatch

    public AmazonClients(AmazonClientProvider amazonClientProvider, NetflixAmazonCredentials account, String region, boolean skipEdda) {
      autoScaling = amazonClientProvider.getAutoScaling(account, region, skipEdda)
      amazonEC2 = amazonClientProvider.getAmazonEC2(account, region, skipEdda)
      amazonCloudWatch = amazonClientProvider.getAmazonCloudWatch(account, region, skipEdda)
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
      this(id);
      this.attributes.putAll(attributes);
      this.relationships.putAll(relationships);
    }
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    type == OnDemandAgent.OnDemandType.ServerGroup && cloudProvider == amazonCloudProvider.id
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

    String serverGroupName = data.serverGroupName.toString()

    Map onDemandData = metricsSupport.readData {
      def asg = loadAutoScalingGroup(serverGroupName, true)

      def clients = new AmazonClients(amazonClientProvider, account, region, true)
      Map<String, Collection<Map>> scalingPolicies = asg ? loadScalingPolicies(clients, serverGroupName) : [:]
      Map<String, Collection<Map>> scheduledActions = asg ? loadScheduledActions(clients, serverGroupName) : [:]

      Map<String, String> subnetMap = [:]
      if (asg?.getVPCZoneIdentifier()) {
        subnetMap.putAll(getSubnetToVpcIdMap(clients, asg.getVPCZoneIdentifier().split(',')))
      }

      return [
        asgs            : asg ? [asg] : [],
        scalingPolicies : scalingPolicies,
        scheduledActions: scheduledActions,
        subnetMap       : subnetMap
      ]
    }

    def cacheResult = metricsSupport.transformData {
      buildCacheResult(onDemandData.asgs, onDemandData.scalingPolicies, onDemandData.scheduledActions, onDemandData.subnetMap, [:], [])
    }
    def cacheResultAsJson = objectMapper.writeValueAsString(cacheResult.cacheResults)

    if (cacheResult.cacheResults.values().flatten().isEmpty()) {
      // avoid writing an empty onDemand cache record (instead delete any that may have previously existed)
      providerCache.evictDeletedItems(ON_DEMAND.ns, [Keys.getServerGroupKey(serverGroupName, account.name, region)])
    } else {
      metricsSupport.onDemandStore {
        def cacheData = new DefaultCacheData(
          Keys.getServerGroupKey(serverGroupName, account.name, region),
          10 * 60,
          [
            cacheTime   : new Date(),
            cacheResults: cacheResultAsJson
          ],
          [:]
        )

        providerCache.putCacheData(ON_DEMAND.ns, cacheData)
      }
    }

    Map<String, Collection<String>> evictions = onDemandData.asgs ? [:] : [
      (SERVER_GROUPS.ns): [
        Keys.getServerGroupKey(serverGroupName, account.name, region)
      ]
    ]

    log.info("onDemand cache refresh (data: ${data}, evictions: ${evictions}, cacheResult: ${cacheResultAsJson})")
    return new OnDemandAgent.OnDemandResult(
      sourceAgentType: getOnDemandAgentType(), cacheResult: cacheResult, evictions: evictions
    )
  }

  private Map<String, CacheData> cache() {
    [:].withDefault { String id -> new MutableCacheData(id) }
  }

  Map<String, String> getSubnetToVpcIdMap(AmazonClients clients, String... subnetIds) {
    Map<String, String> subnetMap = [:]
    def request = new DescribeSubnetsRequest()
    if (subnetIds.length > 0) {
      request.withSubnetIds(subnetIds)
    }
    for (Subnet subnet : clients.amazonEC2.describeSubnets(request).subnets) {
      String existing = subnetMap.put(subnet.subnetId, subnet.vpcId)
      if (existing != null && existing != subnet.vpcId) {
        throw new RuntimeException("Unexpected non unique subnetId to vpcId mapping")
      }
    }
    subnetMap
  }

  private AutoScalingGroupsResults loadAutoScalingGroups(AmazonClients clients) {
    log.debug("Describing auto scaling groups in ${agentType}")

    def request = new DescribeAutoScalingGroupsRequest().withMaxRecords(100)

    Long start = account.eddaEnabled ? null : System.currentTimeMillis()

    List<AutoScalingGroup> asgs = []
    while (true) {
      def resp = clients.autoScaling.describeAutoScalingGroups(request)
      if (account.eddaEnabled) {
        start = amazonClientProvider.lastModified ?: 0
      }
      asgs.addAll(resp.autoScalingGroups)
      if (resp.nextToken) {
        request.withNextToken(resp.nextToken)
      } else {
        break
      }
    }

    if (!start) {
      if (account.eddaEnabled && asgs) {
        log.warn("${agentType} did not receive lastModified value in response metadata")
      }
      start = System.currentTimeMillis()
    }

    // A non-null status indicates that the ASG is in the process of being destroyed (no sense indexing)
    asgs = asgs.findAll { it.status == null }

    new AutoScalingGroupsResults(start: start, asgs: asgs)
  }

  private Map<String, List<Map>> loadScalingPolicies(AmazonClients clients) {
    loadScalingPolicies(clients, null)
  }

  private Map<String, List<Map>> loadScalingPolicies(AmazonClients clients, String asgName) {
    log.debug("Describing scaling policies in ${agentType}")

    def request = new DescribePoliciesRequest()
    if (asgName) {
      request.withAutoScalingGroupName(asgName)
    }
    List<ScalingPolicy> scalingPolicies = []
    while (true) {
      def resp = clients.autoScaling.describePolicies(request)
      scalingPolicies.addAll(resp.scalingPolicies)
      if (resp.nextToken) {
        request.withNextToken(resp.nextToken)
      } else {
        break
      }
    }
    def alarmNames = []
    if (asgName) {
      alarmNames = scalingPolicies.findResults { it.alarms.findResults { it.alarmName } }.flatten().unique()
    }

    Map<String, Map> alarms = [:]
    if (!asgName || alarmNames) {
      alarms = loadAlarms(clients, alarmNames)
    }

    scalingPolicies
      .findResults { buildScalingPolicy(it, alarms) }
      .groupBy { it.autoScalingGroupName }
  }

  private Map<String, List<Map>> loadScheduledActions(AmazonClients clients) {
    loadScheduledActions(clients, null)
  }

  private Map<String, List<Map>> loadScheduledActions(AmazonClients clients, String asgName) {
    log.debug("Describing scheduled actions in ${agentType}")

    def request = new DescribeScheduledActionsRequest()
    if (asgName) {
      request.withAutoScalingGroupName(asgName)
    }
    List<ScheduledUpdateGroupAction> scheduledActions = []
    while (true) {
      def resp = clients.autoScaling.describeScheduledActions(request)
      scheduledActions.addAll(resp.scheduledUpdateGroupActions)
      if (resp.nextToken) {
        request.withNextToken(resp.nextToken)
      } else {
        break
      }
    }
    scheduledActions
      .findResults { toMap(it) }
      .groupBy { it.autoScalingGroupName }
  }

  private Map<String, Object> toMap(obj) {
    objectMapper.convertValue(obj, Map)
  }

  private Map<String, Map> loadAlarms(AmazonClients clients, List alarmNames) {
    log.debug("Describing alarms in ${agentType}")

    def request = new DescribeAlarmsRequest().withMaxRecords(100)
    if (alarmNames.size()) {
      request.withAlarmNames(alarmNames)
    }
    List<MetricAlarm> alarms = []
    while (true) {
      def resp = clients.amazonCloudWatch.describeAlarms(request)
      alarms.addAll(resp.metricAlarms)
      if (resp.nextToken) {
        request.withNextToken(resp.nextToken)
      } else {
        break
      }
    }
    alarms.collectEntries { [(it.alarmArn): toMap(it)] }
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.debug("Describing items in ${agentType}")

    def clients = new AmazonClients(amazonClientProvider, account, region, false)

    def autoScalingGroupsResult = loadAutoScalingGroups(clients)
    def scalingPolicies = loadScalingPolicies(clients)
    def scheduledActions = loadScheduledActions(clients)

    Long start = autoScalingGroupsResult.start
    List<AutoScalingGroup> asgs = autoScalingGroupsResult.asgs

    def evictableOnDemandCacheDatas = []
    def usableOnDemandCacheDatas = []

    def serverGroupKeys = asgs.collect { Keys.getServerGroupKey(it.autoScalingGroupName, account.name, region) } as Set<String>
    def pendingOnDemandRequestKeys = providerCache
      .filterIdentifiers(ON_DEMAND.ns, Keys.getServerGroupKey("*", "*", account.name, region))
      .findAll { serverGroupKeys.contains(it) }

    def pendingOnDemandRequestsForServerGroups = providerCache.getAll(ON_DEMAND.ns, pendingOnDemandRequestKeys)
    pendingOnDemandRequestsForServerGroups.each {
      if (it.attributes.cacheTime < start && it.attributes.processedCount > 0) {
        if (account.eddaEnabled && !eddaTimeoutConfig.disabledRegions.contains(region)) {
          def asgFromEdda = asgs.find { asg -> it.id.endsWith(":${asg.autoScalingGroupName}") }
          def asgFromAws = loadAutoScalingGroup(asgFromEdda.autoScalingGroupName, true)

          if (areSimilarAutoScalingGroups(asgFromEdda, asgFromAws)) {
            log.info("Evicting previous onDemand value for ${asgFromEdda.autoScalingGroupName} (processedCount: ${it.attributes.processedCount} ... ${flattenAutoScalingGroup(asgFromEdda)} vs ${flattenAutoScalingGroup(asgFromAws)}")
            evictableOnDemandCacheDatas << it
          } else {
            log.info("Preserving previous onDemand value for ${asgFromEdda.autoScalingGroupName} (${flattenAutoScalingGroup(asgFromEdda)} vs ${flattenAutoScalingGroup(asgFromAws)}")
            usableOnDemandCacheDatas << it
          }
        } else {
          evictableOnDemandCacheDatas << it
        }
      } else {
        usableOnDemandCacheDatas << it
      }
    }

    CacheResult result = buildCacheResult(asgs, scalingPolicies, scheduledActions, getSubnetToVpcIdMap(clients), usableOnDemandCacheDatas.collectEntries { [it.id, it] }, evictableOnDemandCacheDatas*.id)
    recordDrift(start)
    def cacheResults = result.cacheResults
    log.debug("Caching ${cacheResults[APPLICATIONS.ns]?.size()} applications in ${agentType}")
    log.debug("Caching ${cacheResults[CLUSTERS.ns]?.size()} clusters in ${agentType}")
    log.debug("Caching ${cacheResults[SERVER_GROUPS.ns]?.size()} server groups in ${agentType}")
    log.debug("Caching ${cacheResults[LOAD_BALANCERS.ns]?.size()} load balancers in ${agentType}")
    log.debug("Caching ${cacheResults[TARGET_GROUPS.ns]?.size()} target groups in ${agentType}")
    log.debug("Caching ${cacheResults[LAUNCH_CONFIGS.ns]?.size()} launch configs in ${agentType}")
    log.debug("Caching ${cacheResults[INSTANCES.ns]?.size()} instances in ${agentType}")
    if (evictableOnDemandCacheDatas) {
      log.info("Evicting onDemand cache keys (${evictableOnDemandCacheDatas.collect { "${it.id}/${start - it.attributes.cacheTime}ms" }.join(", ")})")
    }

    cacheResults[ON_DEMAND.ns].each {
      it.attributes.processedTime = System.currentTimeMillis()
      it.attributes.processedCount = (it.attributes.processedCount ?: 0) + 1
    }

    result
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    def keys = providerCache.filterIdentifiers(ON_DEMAND.ns, Keys.getServerGroupKey("*", "*", account.name, region))
    return fetchPendingOnDemandRequests(providerCache, keys)
  }

  @Override
  Map pendingOnDemandRequest(ProviderCache providerCache, String id) {
    def pendingOnDemandRequests = fetchPendingOnDemandRequests(providerCache, [id])
    return pendingOnDemandRequests?.getAt(0)
  }

  private Collection<Map> fetchPendingOnDemandRequests(ProviderCache providerCache, Collection<String> keys) {
    return providerCache.getAll(ON_DEMAND.ns, keys, RelationshipCacheFilter.none()).collect {
      def details = Keys.parse(it.id)

      return [
        id            : it.id,
        details       : details,
        moniker       : convertOnDemandDetails(details),
        cacheTime     : it.attributes.cacheTime,
        cacheExpiry   : it.attributes.cacheExpiry,
        processedCount: it.attributes.processedCount,
        processedTime : it.attributes.processedTime
      ]
    }
  }

  private CacheResult buildCacheResult(Collection<AutoScalingGroup> asgs,
                                       Map<String, List<Map>> scalingPolicies,
                                       Map<String, List<Map>> scheduledActions,
                                       Map<String, String> subnetMap,
                                       Map<String, CacheData> onDemandCacheDataByAsg,
                                       Collection<String> evictableOnDemandCacheDataIdentifiers) {
    Map<String, CacheData> applications = cache()
    Map<String, CacheData> clusters = cache()
    Map<String, CacheData> serverGroups = cache()
    Map<String, CacheData> loadBalancers = cache()
    Map<String, CacheData> targetGroups = cache()
    Map<String, CacheData> launchConfigs = cache()
    Map<String, CacheData> instances = cache()

    for (AutoScalingGroup asg : asgs) {
      def onDemandCacheData = onDemandCacheDataByAsg ? onDemandCacheDataByAsg[Keys.getServerGroupKey(asg.autoScalingGroupName, account.name, region)] : null
      if (onDemandCacheData) {
        log.info("Using onDemand cache value (id: ${onDemandCacheData.id}, json: ${onDemandCacheData.attributes.cacheResults})")

        Map<String, List<CacheData>> cacheResults = objectMapper.readValue(onDemandCacheData.attributes.cacheResults as String, new TypeReference<Map<String, List<MutableCacheData>>>() {
        })
        cache(cacheResults["applications"], applications)
        cache(cacheResults["clusters"], clusters)
        cache(cacheResults["serverGroups"], serverGroups)
        cache(cacheResults["loadBalancers"], loadBalancers)
        cache(cacheResults["targetGroups"], targetGroups)
        cache(cacheResults["launchConfigs"], launchConfigs)
        cache(cacheResults["instances"], instances)
      } else {
        try {
          AsgData data = new AsgData(asg, scalingPolicies[asg.autoScalingGroupName], scheduledActions[asg.autoScalingGroupName], account.name, region, subnetMap)
          cacheApplication(data, applications)
          cacheCluster(data, clusters)
          cacheServerGroup(data, serverGroups)
          cacheLaunchConfig(data, launchConfigs)
          cacheInstances(data, instances)
          cacheLoadBalancers(data, loadBalancers)
          cacheTargetGroups(data, targetGroups)
        } catch (Exception ex) {
          log.warn("Failed to cache ${asg.autoScalingGroupName} in ${account.name}/${region}", ex)
        }
      }
    }

    new DefaultCacheResult([
      (APPLICATIONS.ns)  : applications.values(),
      (CLUSTERS.ns)      : clusters.values(),
      (SERVER_GROUPS.ns) : serverGroups.values(),
      (LOAD_BALANCERS.ns): loadBalancers.values(),
      (TARGET_GROUPS.ns): targetGroups.values(),
      (LAUNCH_CONFIGS.ns): launchConfigs.values(),
      (INSTANCES.ns)     : instances.values(),
      (ON_DEMAND.ns)     : onDemandCacheDataByAsg.values()
    ], [
      (ON_DEMAND.ns)     : evictableOnDemandCacheDataIdentifiers
    ])
  }

  private void cache(List<CacheData> data, Map<String, CacheData> cacheDataById) {
    data.each {
      def existingCacheData = cacheDataById[it.id]
      if (!existingCacheData) {
        cacheDataById[it.id] = it
      } else {
        existingCacheData.attributes.putAll(it.attributes)
        it.relationships.each { String relationshipName, Collection<String> relationships ->
          existingCacheData.relationships[relationshipName].addAll(relationships)
        }
      }
    }
  }

  private void cacheApplication(AsgData data, Map<String, CacheData> applications) {
    applications[data.appName].with {
      attributes.name = data.name.app
      relationships[CLUSTERS.ns].add(data.cluster)
      relationships[SERVER_GROUPS.ns].add(data.serverGroup)
      relationships[LOAD_BALANCERS.ns].addAll(data.loadBalancerNames)
      relationships[TARGET_GROUPS.ns].addAll(data.targetGroupKeys)
    }
  }

  private void cacheCluster(AsgData data, Map<String, CacheData> clusters) {
    clusters[data.cluster].with {
      attributes.name = data.name.cluster
      relationships[APPLICATIONS.ns].add(data.appName)
      relationships[SERVER_GROUPS.ns].add(data.serverGroup)
      relationships[LOAD_BALANCERS.ns].addAll(data.loadBalancerNames)
      relationships[TARGET_GROUPS.ns].addAll(data.targetGroupKeys)
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
      attributes.vpcId = data.vpcId
      attributes.scalingPolicies = data.scalingPolicies
      attributes.scheduledActions = data.scheduledActions
      attributes.targetGroups = data.targetGroupNames

      relationships[APPLICATIONS.ns].add(data.appName)
      relationships[CLUSTERS.ns].add(data.cluster)
      relationships[LOAD_BALANCERS.ns].addAll(data.loadBalancerNames)
      relationships[TARGET_GROUPS.ns].addAll(data.targetGroupKeys)
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

  private void cacheTargetGroups(AsgData data, Map<String, CacheData> targetGroups) {
    for (String targetGroupKey : data.targetGroupKeys) {
      targetGroups[targetGroupKey].with {
        relationships[APPLICATIONS.ns].add(data.appName)
        relationships[SERVER_GROUPS.ns].add(data.serverGroup)
      }
    }
  }

  private AutoScalingGroup loadAutoScalingGroup(String autoScalingGroupName, boolean skipEdda) {
    def autoScaling = amazonClientProvider.getAutoScaling(account, region, skipEdda)
    def result = autoScaling.describeAutoScalingGroups(
      new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(autoScalingGroupName)
    )

    if (result.autoScalingGroups && !result.autoScalingGroups.isEmpty()) {
      AutoScalingGroup asg = result.autoScalingGroups.get(0)

      // A non-null status indicates that the ASG is in the process of being destroyed
      return (asg.status == null) ? asg : null
    }

    return null
  }

  private Map buildScalingPolicy(ScalingPolicy scalingPolicy, Map<String, Map> metricAlarms) {
    Map policy = objectMapper.convertValue(scalingPolicy, Map)
    policy.alarms = scalingPolicy.alarms.findResults {
      metricAlarms[it.alarmARN]
    }
    policy
  }

  private static Map flattenAutoScalingGroup(AutoScalingGroup asg) {
    if (!asg) {
      return [:]
    }

    return [
      desiredCapacity   : asg.desiredCapacity,
      minSize           : asg.minSize,
      maxSize           : asg.maxSize,
      suspendedProcesses: asg.suspendedProcesses*.processName.sort()
    ]
  }

  private static boolean areSimilarAutoScalingGroups(AutoScalingGroup asg1, AutoScalingGroup asg2) {
    return flattenAutoScalingGroup(asg1) == flattenAutoScalingGroup(asg2)
  }

  private static class AutoScalingGroupsResults {
    Long start
    List<AutoScalingGroup> asgs
  }

  private static class AsgData {
    final AutoScalingGroup asg
    final Names name
    final String appName
    final String cluster
    final String serverGroup
    final String vpcId
    final String launchConfig
    final Set<String> loadBalancerNames
    final Set<String> targetGroupKeys
    final Set<String> targetGroupNames
    final Set<String> instanceIds
    final List<Map> scalingPolicies
    final List<Map> scheduledActions

    public AsgData(AutoScalingGroup asg,
                   List<Map> scalingPolicies,
                   List<Map> scheduledActions,
                   String account,
                   String region,
                   Map<String, String> subnetMap) {
      this.asg = asg
      this.scalingPolicies = scalingPolicies ?: []
      this.scheduledActions = scheduledActions ?: []

      name = Names.parseName(asg.autoScalingGroupName)
      appName = Keys.getApplicationKey(name.app)
      cluster = Keys.getClusterKey(name.cluster, name.app, account)
      serverGroup = Keys.getServerGroupKey(asg.autoScalingGroupName, account, region)
      String vpcId = null
      if (asg.getVPCZoneIdentifier()) {
        ArrayList<String> subnets = asg.getVPCZoneIdentifier().split(',')
        Set<String> vpcIds = subnets.findResults { subnetMap[it] }
        if (vpcIds.size() != 1) {
          throw new RuntimeException("failed to resolve only one vpc (found ${vpcIds}) for subnets ${subnets} in ASG ${asg.autoScalingGroupName} account ${account} region ${region}")
        }
        vpcId = vpcIds.first()
      }
      this.vpcId = vpcId
      launchConfig = Keys.getLaunchConfigKey(asg.launchConfigurationName, account, region)
      loadBalancerNames = (asg.loadBalancerNames.collect {
        Keys.getLoadBalancerKey(it, account, region, vpcId, null)
      } as Set).asImmutable()

      targetGroupNames = (asg.targetGroupARNs.collect {
        ArnUtils.extractTargetGroupName(it).get()
      } as Set).asImmutable()

      targetGroupKeys = (targetGroupNames.collect {
        Keys.getTargetGroupKey(it, account, region, TargetTypeEnum.Instance.toString(), vpcId)
      } as Set).asImmutable()

      instanceIds = (asg.instances.instanceId.collect { Keys.getInstanceKey(it, account, region) } as Set).asImmutable()
    }
  }
}
