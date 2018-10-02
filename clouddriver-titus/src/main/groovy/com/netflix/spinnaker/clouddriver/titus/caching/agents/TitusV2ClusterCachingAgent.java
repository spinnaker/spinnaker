/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.caching.agents;

import com.amazonaws.services.elasticloadbalancingv2.model.TargetTypeEnum;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.util.JsonFormat;
import com.netflix.frigga.Names;
import com.netflix.frigga.autoscaling.AutoScalingGroupNameBuilder;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.histogram.PercentileTimer;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.data.ArnUtils;
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider;
import com.netflix.spinnaker.clouddriver.titus.caching.Keys;
import com.netflix.spinnaker.clouddriver.titus.caching.TitusCachingProvider;
import com.netflix.spinnaker.clouddriver.titus.caching.utils.AwsLookupUtil;
import com.netflix.spinnaker.clouddriver.titus.client.TitusAutoscalingClient;
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient;
import com.netflix.spinnaker.clouddriver.titus.client.TitusLoadBalancerClient;
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion;
import com.netflix.spinnaker.clouddriver.titus.client.model.Job;
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;
import com.netflix.titus.grpc.protogen.ScalingPolicy;
import com.netflix.titus.grpc.protogen.ScalingPolicyResult;
import com.netflix.titus.grpc.protogen.ScalingPolicyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.TARGET_GROUPS;
import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class TitusV2ClusterCachingAgent implements CachingAgent, CustomScheduledAgent, OnDemandAgent {

  private static final Logger log = LoggerFactory.getLogger(TitusV2ClusterCachingAgent.class);

  private static final TypeReference<Map<String, Object>> ANY_MAP = new TypeReference<Map<String, Object>>() {};

  static final java.util.Set<AgentDataType> types = Collections.unmodifiableSet(Stream.of(
      AUTHORITATIVE.forType(SERVER_GROUPS.ns),
      AUTHORITATIVE.forType(APPLICATIONS.ns),
      INFORMATIVE.forType(IMAGES.ns),
      INFORMATIVE.forType(CLUSTERS.ns),
      INFORMATIVE.forType(TARGET_GROUPS.ns)
    ).collect(Collectors.toSet()));

  private final TitusClient titusClient;
  private final TitusAutoscalingClient titusAutoscalingClient;
  private final TitusLoadBalancerClient titusLoadBalancerClient;
  private final NetflixTitusCredentials account;
  private final TitusRegion region;
  private final ObjectMapper objectMapper;
  private final OnDemandMetricsSupport metricsSupport;
  private final Provider<AwsLookupUtil> awsLookupUtil;
  private final long pollIntervalMillis;
  private final long timeoutMillis;
  private final Registry registry;
  private final Id metricId;

  public TitusV2ClusterCachingAgent(TitusClientProvider titusClientProvider,
                                    NetflixTitusCredentials account,
                                    TitusRegion region,
                                    ObjectMapper objectMapper,
                                    Registry registry,
                                    Provider<AwsLookupUtil> awsLookupUtil,
                                    Long pollIntervalMillis,
                                    Long timeoutMillis) {
    this.account = account;
    this.region = region;

    this.objectMapper = objectMapper;
    this.metricsSupport = new OnDemandMetricsSupport(
      registry,
      this,
      TitusCloudProvider.ID + ":" + OnDemandType.ServerGroup
    );
    this.titusClient = titusClientProvider.getTitusClient(account, region.getName());
    this.titusAutoscalingClient = titusClientProvider.getTitusAutoscalingClient(account, region.getName());
    this.titusLoadBalancerClient = titusClientProvider.getTitusLoadBalancerClient(account, region.getName());
    this.awsLookupUtil = awsLookupUtil;
    this.pollIntervalMillis = pollIntervalMillis;
    this.timeoutMillis = timeoutMillis;
    this.registry = registry;

    this.metricId = registry.createId("titus.cache.cluster").withTag("account", account.getName()).withTag("region", region.getName());
  }

  @Override
  public long getPollIntervalMillis() {
    return pollIntervalMillis;
  }

  @Override
  public long getTimeoutMillis() {
    return timeoutMillis;
  }

  @Override
  public String getProviderName() {
    return TitusCachingProvider.PROVIDER_NAME;
  }

  @Override
  public String getAgentType() {
    return account.getName() + "/" + region.getName() + "/" + TitusV2ClusterCachingAgent.class.getSimpleName();
  }

  @Override
  public String getOnDemandAgentType() {
    return getAgentType() + "-OnDemand";
  }

  @Override
  public OnDemandMetricsSupport getMetricsSupport() {
    return metricsSupport;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public Optional<Map<String, String>> getCacheKeyPatterns() {
    Map<String, String> cachekeyPatterns = new HashMap<>();
    cachekeyPatterns.put(SERVER_GROUPS.ns, Keys.getServerGroupV2Key("*", "*", account.getName(), region.getName()));
    return Optional.of(cachekeyPatterns);
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type == OnDemandType.ServerGroup && cloudProvider.equals(TitusCloudProvider.ID);
  }

  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    Long startTime = System.currentTimeMillis();

    if (!data.containsKey("serverGroupName") || !data.containsKey("account") || !data.containsKey("region")) {
      return null;
    }

    if (!account.getName().equals(data.get("account"))) {
      return null;
    }

    if (!region.getName().equals(data.get("region"))) {
      return null;
    }

    Job job = metricsSupport.readData( () -> {
      try {
        return titusClient.findJobByName(data.get("serverGroupName").toString());
      } catch (io.grpc.StatusRuntimeException e) {
        return null;
      }
    });

    OnDemandResult onDemandResult = onDemand(providerCache, job, data);
    PercentileTimer
      .get(registry, metricId.withTag("operation", "handleOnDemand"))
      .record(System.currentTimeMillis() - startTime, MILLISECONDS);

    return onDemandResult;
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
    String serverGroupKey = Keys.getServerGroupV2Key(data.get("serverGroupName").toString(), account.getName(), region.getName());
    Map<String, Collection<CacheData>> cacheResults = new HashMap<>();
    if (job == null) {
      // avoid writing an empty onDemand cache record (instead delete any that may have previously existed)
      providerCache.evictDeletedItems(ON_DEMAND.ns, Collections.singletonList(serverGroupKey));
    } else {
      Map <String, Object> attributes = new HashMap<>();
      attributes.put("cacheTime", new Date());
      attributes.put("cacheResults", Collections.emptyMap());

      CacheData cacheData = metricsSupport.onDemandStore( () -> new DefaultCacheData(
        serverGroupKey,
        10 * 60, // ttl is 10 minutes
        attributes,
        Collections.emptyMap()
      ));

      cacheResults.computeIfAbsent(ON_DEMAND.ns, key -> new ArrayList<>()).add(cacheData);
    }
    Map<String, Collection<String>> evictions = job != null
      ? Collections.emptyMap()
      : Collections.singletonMap(SERVER_GROUPS.ns, Collections.singletonList(serverGroupKey));

    log.info("minimal onDemand cache refresh (data: {}, evictions: {})", data, evictions);
    return new OnDemandResult(getOnDemandAgentType(), new DefaultCacheResult(cacheResults), evictions);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    Long startTime = System.currentTimeMillis();

    log.info("Describing items in {}", getAgentType());
    List<CacheData> evictFromOnDemand = new ArrayList<>();
    List<CacheData> keepInOnDemand = new ArrayList<>();


    List<Job> jobs = titusClient.getAllJobsWithoutTasks();
    PercentileTimer
      .get(registry, metricId.withTag("operation", "getAllJobsWithoutTasks"))
      .record(System.currentTimeMillis() - startTime, MILLISECONDS);

    Long startScalingPolicyTime = System.currentTimeMillis();
    List<ScalingPolicyResult> scalingPolicyResults = titusAutoscalingClient != null
      ? titusAutoscalingClient.getAllScalingPolicies()
      : Collections.emptyList();
    PercentileTimer
      .get(registry, metricId.withTag("operation", "getScalingPolicies"))
      .record(System.currentTimeMillis() - startScalingPolicyTime, MILLISECONDS);

    Long startLoadBalancerTime = System.currentTimeMillis();
    Map<String, List<String>> allLoadBalancers = titusLoadBalancerClient != null
      ? titusLoadBalancerClient.getAllLoadBalancers()
      : Collections.emptyMap();
    PercentileTimer
      .get(registry, metricId.withTag("operation", "getLoadBalancers"))
      .record(System.currentTimeMillis() - startLoadBalancerTime, MILLISECONDS);

    Long startJobIdsTime = System.currentTimeMillis();
    Map<String, List<String>> taskAndJobIds = titusClient.getTaskIdsForJobIds();
    PercentileTimer
      .get(registry, metricId.withTag("operation", "getTaskIdsForJobIds"))
      .record(System.currentTimeMillis() - startJobIdsTime, MILLISECONDS);

    List<String> serverGroupKeys = jobs.stream()
      .map(job -> Keys.getServerGroupV2Key(job.getName(), account.getName(), region.getName()))
      .collect(Collectors.toList());

    List<String> pendingOnDemandRequestKeys = providerCache
      .filterIdentifiers(ON_DEMAND.ns, Keys.getServerGroupV2Key("*", "*", account.getName(), region.getName()))
      .stream().filter(serverGroupKeys::contains).collect(Collectors.toList());

    Collection<CacheData> pendingOnDemandRequestsForServerGroups = providerCache.getAll(ON_DEMAND.ns, pendingOnDemandRequestKeys);

    pendingOnDemandRequestsForServerGroups.forEach( onDemandEntry -> {
      if (Long.parseLong(onDemandEntry.getAttributes().get("cacheTime").toString()) < startTime
        && Long.parseLong(onDemandEntry.getAttributes().getOrDefault("processedCount", "0").toString()) > 0) {
        evictFromOnDemand.add(onDemandEntry);
      } else {
        keepInOnDemand.add(onDemandEntry);
      }
    });

    Map<String, CacheData> onDemandMap = keepInOnDemand.stream().collect(Collectors.toMap(CacheData::getId, it -> it));
    List<String> evictFromOnDemandIds = evictFromOnDemand.stream().map(CacheData::getId).collect(Collectors.toList());

    CacheResult result = buildCacheResult(
      jobs,
      scalingPolicyResults,
      allLoadBalancers,
      taskAndJobIds,
      onDemandMap,
      evictFromOnDemandIds
    );

    result.getCacheResults().get(ON_DEMAND.ns).forEach( onDemandEntry -> {
      onDemandEntry.getAttributes().put("processedTime", System.currentTimeMillis());
      onDemandEntry.getAttributes().put("processedCount", Long.parseLong(onDemandEntry.getAttributes().getOrDefault("processedCount", "0").toString()) + 1);
    });

    PercentileTimer
      .get(registry, metricId.withTag("operation", "loadData"))
      .record(System.currentTimeMillis() - startTime, MILLISECONDS);
    return result;
  }

  /**
   * Used to build a cache result, whether normal or on demand,
   * by transforming known Titus objects into Spinnaker cached objects
   */
  private CacheResult buildCacheResult(List<Job> jobs,
                                       List<ScalingPolicyResult> scalingPolicyResults,
                                       Map<String, List<String>> allLoadBalancers,
                                       Map<String, List<String>> taskAndJobIds,
                                       Map<String, CacheData> onDemandKeep,
                                       List<String> onDemandEvict) {
    if (onDemandKeep == null) {
      onDemandKeep = new HashMap<>();
    }
    if (onDemandEvict == null) {
      onDemandEvict = new ArrayList<>();
    }

    // INITIALIZE CACHES
    Map<String, CacheData> applicationCache = createCache();
    Map<String, CacheData> clusterCache = createCache();
    Map<String, CacheData> serverGroupCache = createCache();
    Map<String, CacheData> targetGroupCache = createCache();
    Map<String, CacheData> imageCache = createCache();

    // Ignore policies in a Deleted state (may need to revisit)
    List cacheablePolicyStates = Arrays.asList(
      ScalingPolicyStatus.ScalingPolicyState.Applied,
      ScalingPolicyStatus.ScalingPolicyState.Deleting
    );

    List<ServerGroupData> serverGroupDatas = jobs.stream()
      .map( job -> {
        List<ScalingPolicyData> jobScalingPolicies = scalingPolicyResults.stream()
          .filter( it -> it.getJobId().equalsIgnoreCase(job.getId()) && cacheablePolicyStates.contains(it.getPolicyState().getState()))
          .map( it -> new ScalingPolicyData(it.getId().getId(), it.getScalingPolicy(), it.getPolicyState()))
          .collect(Collectors.toList());

        List<String> jobLoadBalancers = allLoadBalancers.getOrDefault(job.getId(), Collections.emptyList());
        return new ServerGroupData(job, jobScalingPolicies, jobLoadBalancers, taskAndJobIds.get(job.getId()), account.getName(), region.getName());
      })
      .collect(Collectors.toList());

    serverGroupDatas.forEach(data -> {
      cacheApplication(data, applicationCache);
      cacheCluster(data, clusterCache);
      cacheServerGroup(data, serverGroupCache);
      cacheImage(data, imageCache);
    });

    Map<String, Collection<CacheData>> cacheResults = new HashMap<>();
    cacheResults.put(APPLICATIONS.ns, applicationCache.values());
    cacheResults.put(CLUSTERS.ns, clusterCache.values());
    cacheResults.put(SERVER_GROUPS.ns, serverGroupCache.values());
    cacheResults.put(TARGET_GROUPS.ns, targetGroupCache.values());
    cacheResults.put(IMAGES.ns, imageCache.values());
    cacheResults.put(ON_DEMAND.ns, onDemandKeep.values());
    Map<String, Collection<String>> evictions = new HashMap<>();
    evictions.put(ON_DEMAND.ns, onDemandEvict);

    log.info("Caching {} applications in {}", applicationCache.size(), getAgentType());
    log.info("Caching {} server groups in {}", serverGroupCache.size(), getAgentType());
    log.info("Caching {} clusters in {}", clusterCache.size(), getAgentType());
    log.info("Caching {} target groups in {}", targetGroupCache.size(), getAgentType());
    log.info("Caching {} images in {}", imageCache.size(), getAgentType());

    return new DefaultCacheResult(cacheResults, evictions);
  }

  /**
   * Build authoritative cache object for applications based on server group data
   */
  private void cacheApplication(ServerGroupData data, Map<String, CacheData> applications) {
    CacheData applicationCache = applications.getOrDefault(data.appNameKey, new MutableCacheData(data.appNameKey));
    applicationCache.getAttributes().put("name", data.name.getApp());
    Map<String, Collection<String>> relationships = applicationCache.getRelationships();
    relationships.computeIfAbsent(CLUSTERS.ns, key -> new HashSet<>()).add(data.clusterKey);
    relationships.computeIfAbsent(SERVER_GROUPS.ns, key -> new HashSet<>()).add(data.serverGroupKey);
    relationships.computeIfAbsent(TARGET_GROUPS.ns, key -> new HashSet<>()).addAll(data.targetGroupKeys);
    applications.put(data.appNameKey, applicationCache);
  }

  /**
   * Build informative cache object for clusters based on server group data
   */
  private void cacheCluster(ServerGroupData data, Map<String, CacheData> clusters) {
    CacheData clusterCache = clusters.getOrDefault(data.clusterKey, new MutableCacheData(data.clusterKey));
    clusterCache.getAttributes().put("name", data.name.getCluster());
    Map<String, Collection<String>> relationships = clusterCache.getRelationships();
    relationships.computeIfAbsent(APPLICATIONS.ns, key -> new HashSet<>()).add(data.appNameKey);
    relationships.computeIfAbsent(SERVER_GROUPS.ns, key -> new HashSet<>()).add(data.serverGroupKey);
    relationships.computeIfAbsent(TARGET_GROUPS.ns, key -> new HashSet<>()).addAll(data.targetGroupKeys);
    clusters.put(data.clusterKey, clusterCache);
  }

  private void cacheServerGroup(ServerGroupData data, Map<String, CacheData> serverGroups) {
    CacheData serverGroupCache = serverGroups.getOrDefault(data.serverGroupKey, new MutableCacheData(data.serverGroupKey));
    List<Map> policies = data.scalingPolicies != null
      ? data.scalingPolicies.stream().map(ScalingPolicyData::toMap).collect(Collectors.toList())
      : new ArrayList<>();

    Map<String, Object> attributes = serverGroupCache.getAttributes();
    attributes.put("job", data.job);
    attributes.put("scalingPolicies", policies);
    attributes.put("region", region.getName());
    attributes.put("account", account.getName());
    attributes.put("targetGroups", data.targetGroupNames);
    attributes.put("taskIds", data.taskIds); //todo: needed?

    Map<String, Collection<String>> relationships = serverGroupCache.getRelationships();
    relationships.computeIfAbsent(APPLICATIONS.ns, key -> new HashSet<>()).add(data.appNameKey);
    relationships.computeIfAbsent(CLUSTERS.ns, key -> new HashSet<>()).add(data.clusterKey);
    relationships.computeIfAbsent(TARGET_GROUPS.ns, key -> new HashSet<>()).addAll(data.targetGroupKeys);
    relationships.computeIfAbsent(IMAGES.ns, key -> new HashSet<>()).add(data.imageKey);
    relationships.computeIfAbsent(INSTANCES.ns, key -> new HashSet<>()).addAll(data.taskKeys);

    serverGroups.put(data.serverGroupKey, serverGroupCache);
  }

  private void cacheImage(ServerGroupData data, Map<String, CacheData> images) {
    CacheData imageCache = images.getOrDefault(data.imageKey, new MutableCacheData(data.imageKey));
    imageCache.getRelationships().computeIfAbsent(SERVER_GROUPS.ns, key -> new HashSet<>()).add(data.serverGroupKey);
    images.put(data.imageKey, imageCache);
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    Set<String> keys = providerCache.getIdentifiers("onDemand").stream()
      .filter(it -> {
        Map<String, String> key = Keys.parse(it);
        return key != null &&
          key.get("type").equals(SERVER_GROUPS.ns) &&
          key.get("account").equals(account.getName()) &&
          key.get("region").equals(region.getName());
      })
      .collect(Collectors.toSet());
    return fetchPendingOnDemandRequests(providerCache, keys);
  }

  private Collection<Map> fetchPendingOnDemandRequests(ProviderCache providerCache, Collection<String> keys) {
    return providerCache.getAll("onDemand", keys, RelationshipCacheFilter.none()).stream()
      .map(it -> {
        Map<String, Object> result = new HashMap<>();
        result.put("id", it.getId());
        result.put("details", Keys.parse(it.getId()));
        result.put("cacheTime", it.getAttributes().get("cacheTime"));
        result.put("proccessedCount", it.getAttributes().get("processedCount"));
        result.put("processedTime", it.getAttributes().get("processedTime"));
        return result;
      })
      .collect(Collectors.toList());
  }


  @Override
  public Map pendingOnDemandRequest(ProviderCache providerCache, String id) {
    Collection<Map> pendingOnDemandRequests = fetchPendingOnDemandRequests(providerCache, Collections.singletonList(id));
    return pendingOnDemandRequests.isEmpty()
      ? Collections.emptyMap()
      : pendingOnDemandRequests.stream().findFirst().get();
  }

  private Map<String, CacheData> createCache() {
    return new HashMap<>();
  }

  private String getAwsAccountId(String account, String region) {
    return awsLookupUtil.get().awsAccountId(account, region);
  }

  private String getAwsAccountName(String account, String region) {
    return awsLookupUtil.get().awsAccountName(account, region);
  }

  private String getAwsVpcId(String account, String region) {
    return awsLookupUtil.get().awsVpcId(account, region);
  }

  static class MutableCacheData implements CacheData {
    final String id;
    int ttlSeconds = -1;
    final Map<String, Object> attributes = new HashMap<>();
    final Map<String, Collection<String>> relationships = new HashMap<>();

    public MutableCacheData(String id) {
      this.id = id;
    }

    @JsonCreator
    public MutableCacheData(@JsonProperty("id") String id,
                            @JsonProperty("attributes") Map<String, Object> attributes,
                            @JsonProperty("relationships") Map<String, Collection<String>> relationships) {
      this(id);
      this.attributes.putAll(attributes);
      this.relationships.putAll(relationships);
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public int getTtlSeconds() {
      return ttlSeconds;
    }

    @Override
    public Map<String, Object> getAttributes() {
      return attributes;
    }

    @Override
    public Map<String, Collection<String>> getRelationships() {
      return relationships;
    }
  }

  private class ScalingPolicyData {
    String id;
    ScalingPolicy policy;
    ScalingPolicyStatus status;

    ScalingPolicyData(ScalingPolicyResult scalingPolicyResult) {
      this(scalingPolicyResult.getId().getId(), scalingPolicyResult.getScalingPolicy(), scalingPolicyResult.getPolicyState());
    }

    ScalingPolicyData(String id, ScalingPolicy policy, ScalingPolicyStatus status) {
      this.id = id;
      this.policy = policy;
      this.status = status;
    }

    protected Map<String, Object> toMap() {
      Map<String, String> status = new HashMap<>();
      status.put("state", this.status.getState().name());
      status.put("reason", this.status.getPendingReason());

      Map<String, Object> result = new HashMap<>();
      result.put("id", id);
      result.put("status", status);


      try {
        String scalingPolicy = JsonFormat.printer().print(policy);
        result.put("policy", objectMapper.readValue(scalingPolicy, ANY_MAP));
      } catch (Exception e) {
        log.warn("Failed to serialize scaling policy for scaling policy {}", e);
        result.put("policy", Collections.emptyMap());
      }

      return result;
    }
  }

  private class ServerGroupData {

    final Job job;
    List<ScalingPolicyData> scalingPolicies;
    final Names name;
    final String appNameKey;
    final String clusterKey;
    final String serverGroupKey;
    final String region;
    final Set<String> targetGroupKeys;
    final Set<String> targetGroupNames;
    final String account;
    final String imageId;
    final String imageKey;
    final List<String> taskIds;
    final List<String> taskKeys;

    ServerGroupData(Job job, List<ScalingPolicyData> scalingPolicies, List<String> targetGroups, List<String> taskIds, String account, String region) {
      this.job = job;
      this.scalingPolicies = scalingPolicies;
      this.imageId = job.getApplicationName() + ":" + job.getVersion();
      this.imageKey = Keys.getImageV2Key(imageId, getAwsAccountId(account, region), region);
      this.taskIds = taskIds;
      this.taskKeys = taskIds == null
        ? Collections.emptyList()
        : taskIds.stream().map(it -> Keys.getInstanceV2Key(it, account, region)).collect(Collectors.toList());

      String asgName = job.getName();
      if (job.getLabels().containsKey("name")) {
        asgName = job.getLabels().get("name");
      } else {
        if (job.getAppName() != null) {
          AutoScalingGroupNameBuilder asgNameBuilder = new AutoScalingGroupNameBuilder();
          asgNameBuilder.setAppName(job.getAppName());
          asgNameBuilder.setDetail(job.getJobGroupDetail());
          asgNameBuilder.setStack(job.getJobGroupStack());
          String version = job.getJobGroupSequence();
          asgName = asgNameBuilder.buildGroupName() + (version != null ? "-" + version : "");
        }
      }

      name = Names.parseName(asgName);
      appNameKey = Keys.getApplicationKey(name.getApp());
      clusterKey = Keys.getClusterV2Key(name.getCluster(), name.getApp(), account);
      this.region = region;
      this.account = account;
      serverGroupKey = Keys.getServerGroupV2Key(job.getName(), account, region);

      targetGroupNames = targetGroups.stream()
        .map(ArnUtils::extractTargetGroupName)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toSet());

      targetGroupKeys = targetGroupNames.stream()
        .map(it -> com.netflix.spinnaker.clouddriver.aws.data.Keys.getTargetGroupKey(it, getAwsAccountName(account, region), region, TargetTypeEnum.Ip.toString(), getAwsVpcId(account, region)))
        .collect(Collectors.toSet());
    }
  }
}
