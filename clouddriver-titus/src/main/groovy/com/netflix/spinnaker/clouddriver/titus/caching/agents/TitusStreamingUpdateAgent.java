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
import com.netflix.spinnaker.cats.agent.*;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.clouddriver.aws.data.ArnUtils;
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.caching.Keys;
import com.netflix.spinnaker.clouddriver.titus.caching.TitusCachingProvider;
import com.netflix.spinnaker.clouddriver.titus.caching.utils.AwsLookupUtil;
import com.netflix.spinnaker.clouddriver.titus.client.TitusAutoscalingClient;
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient;
import com.netflix.spinnaker.clouddriver.titus.client.TitusLoadBalancerClient;
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion;
import com.netflix.spinnaker.clouddriver.titus.client.model.TaskState;
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;
import com.netflix.titus.grpc.protogen.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.TARGET_GROUPS;
import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Slf4j
public class TitusStreamingUpdateAgent implements CustomScheduledAgent {

  private static final TypeReference<Map<String, Object>> ANY_MAP = new TypeReference<Map<String, Object>>() {
  };

  private final TitusClient titusClient;
  private final TitusAutoscalingClient titusAutoscalingClient;
  private final TitusLoadBalancerClient titusLoadBalancerClient;
  private final NetflixTitusCredentials account;
  private final TitusRegion region;
  private final ObjectMapper objectMapper;
  private final Registry registry;
  private final Id metricId;
  private final Provider<AwsLookupUtil> awsLookupUtil;

  // TODO: these thresholds should be dynamic properties
  private final long TIME_UPDATE_THRESHOLD_MS = TimeUnit.SECONDS.toMillis(15);
  private final long ITEMS_CHANGED_THRESHOLD = 10000;

  private final Logger log = LoggerFactory.getLogger(TitusStreamingUpdateAgent.class);

  private static final Set<TaskStatus.TaskState> FINISHED_TASK_STATES = Collections.unmodifiableSet(Stream.of(
    TaskStatus.TaskState.Finished,
    TaskStatus.TaskState.KillInitiated
  ).collect(Collectors.toSet()));

  private static final Set<JobStatus.JobState> FINISHED_JOB_STATES = Collections.unmodifiableSet(Stream.of(
    JobStatus.JobState.Finished,
    JobStatus.JobState.KillInitiated
  ).collect(Collectors.toSet()));

  private static final Set<TaskStatus.TaskState> FILTERED_TASK_STATES = Collections.unmodifiableSet(Stream.of(
    TaskStatus.TaskState.Launched,
    TaskStatus.TaskState.Started,
    TaskStatus.TaskState.StartInitiated
  ).collect(Collectors.toSet()));

  private static final Set<AgentDataType> TYPES = Collections.unmodifiableSet(Stream.of(
    AUTHORITATIVE.forType(SERVER_GROUPS.ns),
    AUTHORITATIVE.forType(CLUSTERS.ns),
    AUTHORITATIVE.forType(APPLICATIONS.ns),
    AUTHORITATIVE.forType(INSTANCES.ns),
    INFORMATIVE.forType(IMAGES.ns),
    INFORMATIVE.forType(TARGET_GROUPS.ns)
  ).collect(Collectors.toSet()));

  private static final List<TaskState> DOWN_TASK_STATES = Arrays.asList(
    TaskState.STOPPED,
    TaskState.FAILED,
    TaskState.CRASHED,
    TaskState.FINISHED,
    TaskState.DEAD,
    TaskState.TERMINATING
  );

  private static final List<TaskState> STARTING_TASK_STATES = Arrays.asList(
    TaskState.STARTING,
    TaskState.DISPATCHED,
    TaskState.PENDING,
    TaskState.QUEUED
  );

  private static final List CACHEABLE_POLICY_STATES = Arrays.asList(
    ScalingPolicyStatus.ScalingPolicyState.Applied,
    ScalingPolicyStatus.ScalingPolicyState.Deleting
  );

  public TitusStreamingUpdateAgent(TitusClientProvider titusClientProvider,
                                   NetflixTitusCredentials account,
                                   TitusRegion region,
                                   ObjectMapper objectMapper,
                                   Registry registry,
                                   Provider<AwsLookupUtil> awsLookupUtil
  ) {
    this.account = account;
    this.region = region;
    this.objectMapper = objectMapper;
    this.titusClient = titusClientProvider.getTitusClient(account, region.getName());
    this.titusAutoscalingClient = titusClientProvider.getTitusAutoscalingClient(account, region.getName());
    this.titusLoadBalancerClient = titusClientProvider.getTitusLoadBalancerClient(account, region.getName());
    this.registry = registry;
    this.awsLookupUtil = awsLookupUtil;
    this.metricId = registry.createId("titus.cache.streaming")
      .withTag("account", account.getName())
      .withTag("region", region.getName());
  }

  @Override
  public String getProviderName() {
    return TitusCachingProvider.PROVIDER_NAME;
  }

  @Override
  public AgentExecution getAgentExecution(ProviderRegistry providerRegistry) {
    return new StreamingCacheExecution(providerRegistry);
  }

  class StreamingCacheExecution implements AgentExecution {
    private final ProviderRegistry providerRegistry;
    private final ProviderCache cache;
    private AtomicInteger changes = new AtomicInteger(0);
    private AtomicLong lastUpdate = new AtomicLong(0);

    Map<String, Job> jobs = new HashMap<>();
    Map<String, Set<Task>> tasks = new HashMap<>();

    StreamingCacheExecution(ProviderRegistry providerRegistry) {
      this.providerRegistry = providerRegistry;
      this.cache = providerRegistry.getProviderCache(getProviderName());
    }

    private String getAgentType() {
      return account.getName() + "/" + region.getName() + "/" + TitusStreamingUpdateAgent.class.getSimpleName();
    }

    @Override
    public void executeAgent(Agent agent) {
      Long startTime = System.currentTimeMillis();

      ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
      final Future handler = executor.submit(() -> {
        Iterator<JobChangeNotification> notificationIt = titusClient.observeJobs(
          ObserveJobsQuery.newBuilder()
            .putFilteringCriteria("jobType", "SERVICE")
            .putFilteringCriteria("attributes", "source:spinnaker")
            .build()
        );

        Boolean snapshotComplete = false;
        Boolean savedSnapshot = false;

        while (continueStreaming(startTime)) {
          try {
            while (notificationIt.hasNext() && continueStreaming(startTime)) {
              JobChangeNotification notification = notificationIt.next();
              switch (notification.getNotificationCase()) {
                case JOBUPDATE:
                  updateJob(notification.getJobUpdate().getJob());
                  break;
                case TASKUPDATE:
                  updateTask(notification.getTaskUpdate().getTask(), snapshotComplete);
                  break;
                case SNAPSHOTEND:
                  lastUpdate.set(0);
                  log.info("{} snapshot finished in {}ms", getAgentType(), System.currentTimeMillis() - startTime);
                  tasks.keySet().retainAll(jobs.keySet());
                  if (snapshotComplete) {
                    log.error("{} received >1 SNAPSHOTEND events, this is unexpected and may be handled incorrectly",
                      getAgentType()
                    );
                  }
                  snapshotComplete = true;
                  break;
              }

              if (snapshotComplete) {
                writeToCache(!savedSnapshot);
                if (!savedSnapshot) {
                  savedSnapshot = true;
                }
              }
            }
          } catch (io.grpc.StatusRuntimeException e) {
            log.warn("gRPC exception while streaming {} updates, attempting to reconnect", getAgentType(), e);
            notificationIt = titusClient.observeJobs(
              ObserveJobsQuery.newBuilder()
                .putFilteringCriteria("jobType", "SERVICE")
                .putFilteringCriteria("attributes", "source:spinnaker")
                .build()
            );
            snapshotComplete = false;
            savedSnapshot = false;
          } catch (Exception e) {
            log.error("Exception while streaming {} titus updates", getAgentType(), e);
          }
        }
      });

      executor.schedule(() -> {
        handler.cancel(true);
      }, getTimeoutMillis(), TimeUnit.MILLISECONDS);
      CompletableFuture.completedFuture(handler).join();
    }

    private void updateJob(Job job) {
      String jobId = job.getId();
      if (FINISHED_JOB_STATES.contains(job.getStatus().getState())) {
        if (jobs.containsKey(jobId)) {
          jobs.remove(jobId);
          if (tasks.containsKey(jobId)) {
            tasks.remove(jobId);
          }
          changes.incrementAndGet();
        }
      } else {
        jobs.put(jobId, job);
        changes.incrementAndGet();
      }
    }

    private void updateTask(Task task, Boolean snapshotComplete) {
      String jobId = task.getJobId();
      if (FILTERED_TASK_STATES.contains(task.getStatus().getState())) {
        tasks.computeIfAbsent(jobId, t -> new HashSet<>()).add(task);
        changes.incrementAndGet();
      } else if (FINISHED_TASK_STATES.contains(task.getStatus().getState())) {
        if (tasks.containsKey(jobId)) {
          tasks.get(jobId).remove(task);
          changes.incrementAndGet();
        } else {
          if (snapshotComplete) {
            log.warn("{} jobId {} (task {}) has finished, but not present in current snapshot set",
              getAgentType(),
              jobId,
              task.getId()
            );
          }
        }
      }
    }

    /**
     * Once we persist the first snapshot, we only update the cache if the time from the last update exceeds
     * TIME_UPDATE_THRESHOLD_MS or the number of changes exceeds ITEMS_CHANGED_THRESHOLD.
     * <p>
     * TODO: After the full snapshot write, we should support incremental updates to the ProviderCache.
     * Instead of always calling ProviderCache.putCacheResult (which requires the full result set from
     * a caching agent, and calls backingStore.mergeAll()), we should call i.e. ProviderCache.putCacheData
     * (calls backingStore.merge()) and ProviderCache.evictDeletedItems() as needed.
     */
    private void writeToCache(Boolean firstSnapshotWrite) {
      long startTime = System.currentTimeMillis();

      if (firstSnapshotWrite ||
        changes.get() >= ITEMS_CHANGED_THRESHOLD ||
        (startTime - lastUpdate.get() > TIME_UPDATE_THRESHOLD_MS && changes.get() > 0)
      ) {
        if (firstSnapshotWrite) {
          log.info("Storing snapshot with {} job and tasks in {}", changes.get(), getAgentType());
        } else {
          log.info("Updating: {} changes ( last update {} milliseconds ) in {}",
            changes.get(),
            startTime - lastUpdate.get(),
            getAgentType());
        }

        List<ScalingPolicyResult> scalingPolicyResults = titusAutoscalingClient != null
          ? titusAutoscalingClient.getAllScalingPolicies()
          : Collections.emptyList();
        PercentileTimer
          .get(registry, metricId.withTag("operation", "getScalingPolicies"))
          .record(System.currentTimeMillis() - startTime, MILLISECONDS);

        long startLoadBalancerTime = System.currentTimeMillis();
        Map<String, List<String>> allLoadBalancers = titusLoadBalancerClient != null
          ? titusLoadBalancerClient.getAllLoadBalancers()
          : Collections.emptyMap();
        PercentileTimer
          .get(registry, metricId.withTag("operation", "getLoadBalancers"))
          .record(System.currentTimeMillis() - startLoadBalancerTime, MILLISECONDS);

        CacheResult result = buildCacheResult(
          scalingPolicyResults,
          allLoadBalancers
        );

        Collection<String> authoritative = TYPES.stream()
          .filter(t -> t.getAuthority().equals(AUTHORITATIVE))
          .map(AgentDataType::getTypeName)
          .collect(Collectors.toSet());

        cache.putCacheResult(getAgentType(), authoritative, result);
        lastUpdate.set(System.currentTimeMillis());
        changes.set(0);

        PercentileTimer
          .get(registry, metricId.withTag("operation", "processSnapshot"))
          .record(System.currentTimeMillis() - startTime, MILLISECONDS);
      }
    }

    private CacheResult buildCacheResult(List<ScalingPolicyResult> scalingPolicyResults,
                                         Map<String, List<String>> allLoadBalancers) {
      // INITIALIZE CACHES
      Map<String, CacheData> applicationCache = createCache();
      Map<String, CacheData> clusterCache = createCache();
      Map<String, CacheData> serverGroupCache = createCache();
      Map<String, CacheData> targetGroupCache = createCache();
      Map<String, CacheData> imageCache = createCache();
      Map<String, CacheData> instancesCache = createCache();

      List<ServerGroupData> serverGroupDatas = jobs.values().stream()
        .map(job -> {
          List<ScalingPolicyData> jobScalingPolicies = scalingPolicyResults.stream()
            .filter(it -> it.getJobId().equalsIgnoreCase(job.getId())
              && CACHEABLE_POLICY_STATES.contains(it.getPolicyState().getState()))
            .map(it -> new ScalingPolicyData(it.getId().getId(), it.getScalingPolicy(), it.getPolicyState()))
            .collect(Collectors.toList());

          List<String> jobLoadBalancers = allLoadBalancers.getOrDefault(job.getId(), Collections.emptyList());
          return new ServerGroupData(new com.netflix.spinnaker.clouddriver.titus.client.model.Job(job,
            Collections.EMPTY_LIST),
            jobScalingPolicies,
            jobLoadBalancers,
            tasks.getOrDefault(job.getId(), Collections.emptySet())
              .stream()
              .map(Task::getId)
              .collect(Collectors.toSet()),
            account.getName(),
            region.getName());
        })
        .collect(Collectors.toList());

      serverGroupDatas.forEach(data -> {
        cacheApplication(data, applicationCache);
        cacheCluster(data, clusterCache);
        cacheServerGroup(data, serverGroupCache);
        cacheImage(data, imageCache);
        for (Task task : (Set<Task>) tasks.getOrDefault(data.job.getId(), Collections.EMPTY_SET)) {
          InstanceData instanceData =
            new InstanceData(new com.netflix.spinnaker.clouddriver.titus.client.model.Task(task),
              data.job.getName(),
              account.getName(),
              region.getName());
          cacheInstance(instanceData, instancesCache);
        }
      });

      Map<String, Collection<CacheData>> cacheResults = new HashMap<>();
      cacheResults.put(APPLICATIONS.ns, applicationCache.values());
      cacheResults.put(CLUSTERS.ns, clusterCache.values());
      cacheResults.put(SERVER_GROUPS.ns, serverGroupCache.values());
      cacheResults.put(TARGET_GROUPS.ns, targetGroupCache.values());
      cacheResults.put(IMAGES.ns, imageCache.values());
      cacheResults.put(INSTANCES.ns, instancesCache.values());

      log.info("Caching {} applications in {}", applicationCache.size(), getAgentType());
      log.info("Caching {} server groups in {}", serverGroupCache.size(), getAgentType());
      log.info("Caching {} clusters in {}", clusterCache.size(), getAgentType());
      log.info("Caching {} target groups in {}", targetGroupCache.size(), getAgentType());
      log.info("Caching {} images in {}", imageCache.size(), getAgentType());
      log.info("Caching {} instances in {}", instancesCache.size(), getAgentType());

      return new DefaultCacheResult(cacheResults);
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
      CacheData serverGroupCache =
        serverGroups.getOrDefault(data.serverGroupKey, new MutableCacheData(data.serverGroupKey));
      List<Map> policies = data.scalingPolicies != null
        ? data.scalingPolicies.stream().map(ScalingPolicyData::toMap).collect(Collectors.toList())
        : new ArrayList<>();

      Map<String, Object> attributes = serverGroupCache.getAttributes();
      attributes.put("job", data.job);
      attributes.put("scalingPolicies", policies);
      attributes.put("region", region.getName());
      attributes.put("account", account.getName());
      attributes.put("targetGroups", data.targetGroupNames);

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

    private void cacheInstance(InstanceData data, Map<String, CacheData> instances) {
      CacheData instanceCache = instances.getOrDefault(data.instanceId, new MutableCacheData(data.instanceId));
      instanceCache.getAttributes().putAll(objectMapper.convertValue(data.task, ANY_MAP));
      instanceCache.getAttributes().put(HEALTH.ns, Collections.singletonList(getTitusHealth(data.task)));
      instanceCache.getAttributes().put("task", data.task);
      instanceCache.getAttributes().put("jobId", data.jobId);

      if (!data.serverGroup.isEmpty()) {
        instanceCache.getRelationships()
          .computeIfAbsent(SERVER_GROUPS.ns, key -> new HashSet<>())
          .add(data.serverGroup);
      } else {
        instanceCache.getRelationships().computeIfAbsent(SERVER_GROUPS.ns, key -> new HashSet<>()).clear();
      }
      instances.put(data.instanceId, instanceCache);
    }
  }

  @Override
  public boolean handlesAccount(String accountName) {
    return this.account.getName().equals(accountName);
  }

  @Override
  public String getAgentType() {
    return account.getName() + "/" + region.getName() + "/" + TitusStreamingUpdateAgent.class.getSimpleName();
  }

  @Override
  public long getPollIntervalMillis() {
    return TimeUnit.MINUTES.toMillis(3);
  }

  // TODO: AgentSchedulers need to support ttl heartbeats for proper streaming agent support.
  // We really want a short poll interval (for fast agent failover across instances) with a
  // timeout that can be extended indefinitely while streaming updates are actively processed.
  @Override
  public long getTimeoutMillis() {
    return TimeUnit.MINUTES.toMillis(3);
  }

  /***
   *
   * @return Time in milliseconds prior to timeout that the streaming agent will stop polling Titus for updates
   */
  private long getPadTimeMillis() {
    return TimeUnit.SECONDS.toMillis(5);
  }

  private boolean continueStreaming(long startTime) {
    return System.currentTimeMillis() < (startTime + getTimeoutMillis() - getPadTimeMillis());
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
      this(scalingPolicyResult.getId().getId(),
        scalingPolicyResult.getScalingPolicy(),
        scalingPolicyResult.getPolicyState());
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
        log.warn("Failed to serialize scaling policy for scaling policy {}", getAgentType(), e);
        result.put("policy", Collections.emptyMap());
      }

      return result;
    }
  }

  private class ServerGroupData {

    final com.netflix.spinnaker.clouddriver.titus.client.model.Job job;
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
    final Set<String> taskKeys;

    ServerGroupData(com.netflix.spinnaker.clouddriver.titus.client.model.Job job,
                    List<ScalingPolicyData> scalingPolicies, List<String> targetGroups, Set<String> taskIds,
                    String account, String region) {
      this.job = job;
      this.scalingPolicies = scalingPolicies;
      this.imageId = job.getApplicationName() + ":" + job.getVersion();
      this.imageKey = Keys.getImageV2Key(imageId, getAwsAccountId(account, region), region);
      this.taskKeys = taskIds == null
        ? Collections.emptySet()
        : taskIds.stream().map(it -> Keys.getInstanceV2Key(it, account, region)).collect(Collectors.toSet());

      String asgName = getAsgName(job);

      name = Names.parseName(asgName);
      appNameKey = Keys.getApplicationKey(name.getApp());
      clusterKey = Keys.getClusterV2Key(name.getCluster(), name.getApp(), account);
      this.region = region;
      this.account = account;
      serverGroupKey = Keys.getServerGroupV2Key(asgName, account, region);

      targetGroupNames = targetGroups.stream()
        .map(ArnUtils::extractTargetGroupName)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toSet());

      targetGroupKeys = targetGroupNames.stream()
        .map(it -> com.netflix.spinnaker.clouddriver.aws.data.Keys.getTargetGroupKey(it,
          getAwsAccountName(account, region),
          region,
          TargetTypeEnum.Ip.toString(),
          getAwsVpcId(account, region)))
        .collect(Collectors.toSet());
    }
  }

  private class InstanceData {
    // The instance key, not the task id
    private final String instanceId;
    private final com.netflix.spinnaker.clouddriver.titus.client.model.Task task;
    private final String jobId;
    private final String serverGroup;

    InstanceData(com.netflix.spinnaker.clouddriver.titus.client.model.Task task, String jobName, String account,
                 String region) {
      this.instanceId = Keys.getInstanceV2Key(task.getId(), account, region);
      this.task = task;
      this.jobId = task.getJobId();
      this.serverGroup = jobName != null
        ? Keys.getServerGroupV2Key(jobName, account, region)
        : "";
    }
  }

  private Map<String, String> getTitusHealth(com.netflix.spinnaker.clouddriver.titus.client.model.Task task) {
    TaskState taskState = task.getState();
    HealthState healthState = HealthState.Unknown;
    if (DOWN_TASK_STATES.contains(taskState)) {
      healthState = HealthState.Down;
    } else if (STARTING_TASK_STATES.contains(taskState)) {
      healthState = HealthState.Starting;
    }

    Map<String, String> response = new HashMap<>();
    response.put("type", "Titus");
    response.put("healthClass", "platform");
    response.put("state", healthState.toString());
    return response;
  }

  private String getAsgName(com.netflix.spinnaker.clouddriver.titus.client.model.Job job) {
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
    return asgName;
  }

}
