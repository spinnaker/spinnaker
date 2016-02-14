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
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.*
import static com.netflix.spinnaker.clouddriver.aws.data.Keys.Namespace.*
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider

class ClusterCachingAgent implements CachingAgent, OnDemandAgent, AccountAware, DriftMetric {
  final Logger log = LoggerFactory.getLogger(getClass())
  @Deprecated
  private static final String LEGACY_ON_DEMAND_TYPE = 'AmazonServerGroup'

  private static final String ON_DEMAND_TYPE = 'ServerGroup'

  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(SERVER_GROUPS.ns),
    AUTHORITATIVE.forType(APPLICATIONS.ns),
    INFORMATIVE.forType(CLUSTERS.ns),
    INFORMATIVE.forType(LOAD_BALANCERS.ns),
    INFORMATIVE.forType(LAUNCH_CONFIGS.ns),
    INFORMATIVE.forType(INSTANCES.ns)
  ] as Set)

  final AmazonCloudProvider amazonCloudProvider
  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper
  final Registry registry

  final OnDemandMetricsSupport metricsSupport

  ClusterCachingAgent(AmazonCloudProvider amazonCloudProvider,
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
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, amazonCloudProvider.id + ":" + ON_DEMAND_TYPE)
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
  boolean handles(String type) {
    type == LEGACY_ON_DEMAND_TYPE
  }

  @Override
  boolean handles(String type, String cloudProvider) {
    type == ON_DEMAND_TYPE && cloudProvider == amazonCloudProvider.id
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
        asgs            : [asg],
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
    log.info("Describing auto scaling groups in ${agentType}")

    def request = new DescribeAutoScalingGroupsRequest()
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
      if (account.eddaEnabled) {
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
    log.info("Describing scaling policies in ${agentType}")

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
    log.info("Describing scheduled actions in ${agentType}")

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
    log.info("Describing alarms in ${agentType}")

    def request = new DescribeAlarmsRequest()
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
    log.info("Describing items in ${agentType}")

    def clients = new AmazonClients(amazonClientProvider, account, region, false)

    def autoScalingGroupsResult = loadAutoScalingGroups(clients)
    def scalingPolicies = loadScalingPolicies(clients)
    def scheduledActions = loadScheduledActions(clients)

    Long start = autoScalingGroupsResult.start
    List<AutoScalingGroup> asgs = autoScalingGroupsResult.asgs

    def evictableOnDemandCacheDatas = []
    def usableOnDemandCacheDatas = []
    providerCache.getAll(ON_DEMAND.ns, asgs.collect { Keys.getServerGroupKey(it.autoScalingGroupName, account.name, region) }).each {
      if (it.attributes.cacheTime < start && it.attributes.processedCount > 0) {
        if (account.eddaEnabled) {
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
    log.info("Caching ${cacheResults[APPLICATIONS.ns]?.size()} applications in ${agentType}")
    log.info("Caching ${cacheResults[CLUSTERS.ns]?.size()} clusters in ${agentType}")
    log.info("Caching ${cacheResults[SERVER_GROUPS.ns]?.size()} server groups in ${agentType}")
    log.info("Caching ${cacheResults[LOAD_BALANCERS.ns]?.size()} load balancers in ${agentType}")
    log.info("Caching ${cacheResults[LAUNCH_CONFIGS.ns]?.size()} launch configs in ${agentType}")
    log.info("Caching ${cacheResults[INSTANCES.ns]?.size()} instances in ${agentType}")
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
    def keys = providerCache.getIdentifiers(ON_DEMAND.ns)
    keys = keys.findAll {
      def key = Keys.parse(it)
      key.type == SERVER_GROUPS.ns && key.account == account.name && key.region == region
    }
    return providerCache.getAll(ON_DEMAND.ns, keys, RelationshipCacheFilter.none()).collect {
      [
        id: it.id,
        details  : Keys.parse(it.id),
        cacheTime: it.attributes.cacheTime,
        processedCount: it.attributes.processedCount,
        processedTime: it.attributes.processedTime
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
    }
  }

  private void cacheCluster(AsgData data, Map<String, CacheData> clusters) {
    clusters[data.cluster].with {
      attributes.name = data.name.cluster
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
      attributes.vpcId = data.vpcId
      attributes.scalingPolicies = data.scalingPolicies
      attributes.scheduledActions = data.scheduledActions

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

  private AutoScalingGroup loadAutoScalingGroup(String autoScalingGroupName, boolean skipEdda) {
    def autoScaling = amazonClientProvider.getAutoScaling(account, region, skipEdda)
    def result = autoScaling.describeAutoScalingGroups(
      new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(autoScalingGroupName)
    )

    if (result.autoScalingGroups && !result.autoScalingGroups.isEmpty()) {
      def asg = result.autoScalingGroups.get(0)

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
        String[] subnets = asg.getVPCZoneIdentifier().split(',')
        Set<String> vpcIds = subnets.collect { subnetMap[it] }
        if (vpcIds.size() != 1) {
          throw new RuntimeException("failed to resolve one vpc (found ${vpcIds}) for subnets ${subnets} in ASG ${asg.autoScalingGroupName} account ${account} region ${region}")
        }
        vpcId = vpcIds.first()
      }
      this.vpcId = vpcId
      launchConfig = Keys.getLaunchConfigKey(asg.launchConfigurationName, account, region)

      loadBalancerNames = (asg.loadBalancerNames.collect {
        Keys.getLoadBalancerKey(it, account, region, vpcId)
      } as Set).asImmutable()
      instanceIds = (asg.instances.instanceId.collect { Keys.getInstanceKey(it, account, region) } as Set).asImmutable()
    }
  }
}
