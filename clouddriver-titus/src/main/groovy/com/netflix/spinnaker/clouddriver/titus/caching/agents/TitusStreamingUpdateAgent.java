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
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.titus.grpc.protogen.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import static java.util.Collections.*;
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
  private final DynamicConfigService dynamicConfigService;

  private final Logger log = LoggerFactory.getLogger(TitusStreamingUpdateAgent.class);

  private static final Set<TaskStatus.TaskState> FINISHED_TASK_STATES = unmodifiableSet(Stream.of(
    TaskStatus.TaskState.Finished,
    TaskStatus.TaskState.KillInitiated
  ).collect(Collectors.toSet()));

  private static final Set<JobStatus.JobState> FINISHED_JOB_STATES = unmodifiableSet(Stream.of(
    JobStatus.JobState.Finished,
    JobStatus.JobState.KillInitiated
  ).collect(Collectors.toSet()));

  private static final Set<TaskStatus.TaskState> FILTERED_TASK_STATES = unmodifiableSet(Stream.of(
    TaskStatus.TaskState.Launched,
    TaskStatus.TaskState.Started,
    TaskStatus.TaskState.StartInitiated
  ).collect(Collectors.toSet()));

  private static final Set<AgentDataType> TYPES = unmodifiableSet(Stream.of(
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
                                   Provider<AwsLookupUtil> awsLookupUtil,
                                   DynamicConfigService dynamicConfigService) {
    this.account = account;
    this.region = region;
    this.objectMapper = objectMapper;
    this.titusClient = titusClientProvider.getTitusClient(account, region.getName());
    this.titusAutoscalingClient = titusClientProvider.getTitusAutoscalingClient(account, region.getName());
    this.titusLoadBalancerClient = titusClientProvider.getTitusLoadBalancerClient(account, region.getName());
    this.registry = registry;
    this.awsLookupUtil = awsLookupUtil;
    this.dynamicConfigService = dynamicConfigService;
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

    StreamingCacheExecution(ProviderRegistry providerRegistry) {
      this.providerRegistry = providerRegistry;
      this.cache = providerRegistry.getProviderCache(getProviderName());
    }

    private String getAgentType() {
      return account.getName() + "/" + region.getName() + "/" + TitusStreamingUpdateAgent.class.getSimpleName();
    }

    /**
     * Subscribes to a Titus observeJobs event stream. At initial connect, Titus streams individual
     * events for every job and task running on the stack, followed by a SNAPSHOTEND event. Once
     * received, the agent builds cacheResults for the full snapshot, equivalent to the standard
     * index-the-world caching agent. In the process, we cache mappings between jobIds to
     * applications, clusters, and server groups within a StreamingCacheState object.
     * <p>
     * After the initial snapshot persist, the agent continues to consume observeJobs events, updating
     * StreamingCacheState, including a list of jobIds we've received events for. Once either
     * titus.streaming.changeThreshold events have been consumed, or titus.streaming.timeThresholdMs ms has passed,
     * cacheResults are built for the full resource graph of applications that have had job/task updates.
     * This is more work than only directly updating i.e. server groups based on job updates or instance
     * based on task updates, but avoids pitfalls in properly maintaining relationships to or deleting
     * higher level objects. if the last server group in a cluster is deleted, the cluster object
     * must also be deleted, and the application object updated. The later cannot currently be done
     * incrementally in an atomic operation; safely updating an application object requires rebuilding it
     * with full context.
     */
    @Override
    public void executeAgent(Agent agent) {
      Long startTime = System.currentTimeMillis();

      StreamingCacheState state = new StreamingCacheState();

      ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
      final Future handler = executor.submit(() -> {
        Iterator<JobChangeNotification> notificationIt = titusClient.observeJobs(
          ObserveJobsQuery.newBuilder()
            .putFilteringCriteria("jobType", "SERVICE")
            .putFilteringCriteria("attributes", "source:spinnaker")
            .build()
        );

        while (continueStreaming(startTime)) {
          try {
            while (notificationIt.hasNext() && continueStreaming(startTime)) {
              JobChangeNotification notification = notificationIt.next();
              switch (notification.getNotificationCase()) {
                case JOBUPDATE:
                  updateJob(state, notification.getJobUpdate().getJob());
                  break;
                case TASKUPDATE:
                  if (notification.getTaskUpdate().getMovedFromAnotherJob()) {
                    Task task = notification.getTaskUpdate().getTask();
                    String destinationJobId = task.getJobId();
                    String sourceJobId = task.getTaskContextOrDefault("task.movedFromJob", null);
                    log.info("{} task moved from job {} to {}", task.getId(), sourceJobId, destinationJobId);
                    updateMovedTask(state, task, sourceJobId);
                  }
                  updateTask(state, notification.getTaskUpdate().getTask());
                  break;
                case SNAPSHOTEND:
                  state.lastUpdate.set(0);
                  log.info("{} snapshot finished in {}ms", getAgentType(), System.currentTimeMillis() - startTime);
                  state.tasks.keySet().retainAll(state.jobs.keySet());
                  if (state.snapshotComplete) {
                    log.error("{} received >1 SNAPSHOTEND events, this is unexpected and may be handled incorrectly",
                      getAgentType()
                    );
                  }
                  state.snapshotComplete = true;
                  break;
              }

              if (state.snapshotComplete) {
                writeToCache(state);
                if (!state.savedSnapshot) {
                  state.savedSnapshot = true;
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
            state.snapshotComplete = false;
            state.savedSnapshot = false;
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

    private void updateJob(StreamingCacheState state, Job job) {
      String jobId = job.getId();
      String application = job.getJobDescriptor().getApplicationName();

      state.jobIdToApp.put(jobId, application);
      if (state.snapshotComplete) {
        state.updatedJobs.add(jobId);
      }

      if (FINISHED_JOB_STATES.contains(job.getStatus().getState())) {
        if (state.snapshotComplete && state.tasks.containsKey(jobId)) {
          state.tasks.get(jobId).forEach(t ->
            state.completedInstanceIds.add(Keys.getInstanceV2Key(t.getId(), account.getName(), region.getName()))
          );
        }
        state.tasks.remove(jobId);
        if (state.jobs.containsKey(jobId)) {
          state.jobs.remove(jobId);
        } else if (state.snapshotComplete) {
          log.debug("{} updateJob: jobId: {} has finished, but not present in current snapshot set",
            getAgentType(),
            jobId
          );
        }
      } else {
        state.jobs.put(jobId, job);
      }

      state.changes.incrementAndGet();
    }

    private void updateTask(StreamingCacheState state, Task task) {
      String jobId = task.getJobId();
      if (FILTERED_TASK_STATES.contains(task.getStatus().getState())) {
        state.tasks.computeIfAbsent(jobId, t -> new HashSet<>()).remove(task);
        state.tasks.get(jobId).add(task);
      } else if (FINISHED_TASK_STATES.contains(task.getStatus().getState())) {
        if (state.snapshotComplete) {
          state.completedInstanceIds.add(
            Keys.getInstanceV2Key(task.getId(), account.getName(), region.getName())
          );
        }
        if (state.tasks.containsKey(jobId)) {
          state.tasks.get(jobId).remove(task);
        } else if (state.snapshotComplete) {
          log.debug("{} updateTask: task: {} jobId: {} has finished, but task not present in current snapshot set",
            getAgentType(),
            task.getId(),
            jobId
          );
        }
      }

      if (state.snapshotComplete) {
        state.updatedJobs.add(jobId);
      }

      state.changes.incrementAndGet();
    }

    private void updateMovedTask(StreamingCacheState state, Task task, String sourceJobId) {
      if (sourceJobId != null) {
        if (state.tasks.containsKey(sourceJobId)) {
          state.tasks.get(sourceJobId).remove(task);
          state.updatedJobs.add(sourceJobId);
        }
      }
    }

    private void writeToCache(StreamingCacheState state) {
      long startTime = System.currentTimeMillis();

      if (!state.savedSnapshot ||
        state.changes.get() >=
          dynamicConfigService.getConfig(Integer.class, "titus.streaming.changeThreshold", 1000) ||
        (startTime - state.lastUpdate.get() >
          dynamicConfigService.getConfig(Integer.class, "titus.streaming.timeThresholdMs", 5000) &&
          state.changes.get() > 0)
      ) {
        if (!state.savedSnapshot) {
          log.info("Storing snapshot with {} job and tasks in {}", state.changes.get(), getAgentType());
        } else {
          state.tasks.keySet().retainAll(state.jobs.keySet());

          log.info("Updating: {} changes ( last update {} milliseconds ) in {}",
            state.changes.get(),
            startTime - state.lastUpdate.get(),
            getAgentType());
        }

        List<ScalingPolicyResult> scalingPolicyResults = titusAutoscalingClient != null
          ? titusAutoscalingClient.getAllScalingPolicies()
          : emptyList();
        PercentileTimer
          .get(registry, metricId.withTag("operation", "getScalingPolicies"))
          .record(System.currentTimeMillis() - startTime, MILLISECONDS);

        long startLoadBalancerTime = System.currentTimeMillis();
        Map<String, List<String>> allLoadBalancers = titusLoadBalancerClient != null
          ? titusLoadBalancerClient.getAllLoadBalancers()
          : emptyMap();
        PercentileTimer
          .get(registry, metricId.withTag("operation", "getLoadBalancers"))
          .record(System.currentTimeMillis() - startLoadBalancerTime, MILLISECONDS);

        CacheResult result = buildCacheResult(state, scalingPolicyResults, allLoadBalancers);

        Collection<String> authoritative = TYPES.stream()
          .filter(t -> t.getAuthority().equals(AUTHORITATIVE))
          .map(AgentDataType::getTypeName)
          .collect(Collectors.toSet());

        if (state.savedSnapshot) {
          // Incremental update without implicit evictions
          cache.addCacheResult(getAgentType(), authoritative, result);
        } else {
          cache.putCacheResult(getAgentType(), authoritative, result);
        }

        // prune jobIdToApp
        Set<String> completedJobs = new HashSet<>(state.jobIdToApp.keySet());
        completedJobs.removeAll(state.jobs.keySet());
        completedJobs.forEach(j -> state.jobIdToApp.remove(j));

        state.updatedJobs = new HashSet<>();
        state.lastUpdate.set(System.currentTimeMillis());
        state.changes.set(0);

        PercentileTimer
          .get(registry, metricId.withTag("operation", "processSnapshot"))
          .record(System.currentTimeMillis() - startTime, MILLISECONDS);
      }
    }

    private CacheResult buildCacheResult(StreamingCacheState state,
                                         List<ScalingPolicyResult> scalingPolicyResults,
                                         Map<String, List<String>> allLoadBalancers) {
      // INITIALIZE CACHES
      Map<String, CacheData> applicationCache = createCache();
      Map<String, CacheData> clusterCache = createCache();
      Map<String, CacheData> serverGroupCache = createCache();
      Map<String, CacheData> targetGroupCache = createCache();
      Map<String, CacheData> imageCache = createCache();
      Map<String, CacheData> instancesCache = createCache();

      // These are used to calculate deletes when updating incrementally
      Set<String> currentApps = new HashSet<>();
      Set<String> currentClusters = new HashSet<>();
      Set<String> currentServerGroups = new HashSet<>();

      Map<String, Job> jobs;

      if (state.savedSnapshot) {
        List<String> missingJobMappings = state.updatedJobs.stream()
          .filter(j -> !state.jobIdToApp.containsKey(j))
          .collect(Collectors.toList());

        if (!missingJobMappings.isEmpty()) {
          log.error("{} updatedJobs missing from jobIdToApp cache: {}", getAgentType(), missingJobMappings);
        }

        Set<String> changedApplications = state.updatedJobs.stream()
          .map(j -> state.jobIdToApp.get(j))
          .collect(Collectors.toSet());
        changedApplications.remove(null);

        currentApps.addAll(changedApplications);

        Set<String> jobsNeeded = state.jobIdToApp.entrySet().stream()
          .filter(entry -> changedApplications.contains(entry.getValue()))
          .map(Map.Entry::getKey)
          .collect(Collectors.toSet());

        jobs = state.jobs.entrySet().stream()
          .filter(e -> jobsNeeded.contains(e.getKey()))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
      } else {
        jobs = state.jobs;
      }

      List<ServerGroupData> serverGroupDatas = jobs.values().stream()
        .map(job -> {
          List<ScalingPolicyData> jobScalingPolicies = scalingPolicyResults.stream()
            .filter(it -> it.getJobId().equalsIgnoreCase(job.getId())
              && CACHEABLE_POLICY_STATES.contains(it.getPolicyState().getState()))
            .map(it -> new ScalingPolicyData(it.getId().getId(), it.getScalingPolicy(), it.getPolicyState()))
            .collect(Collectors.toList());

          List<String> jobLoadBalancers = allLoadBalancers.getOrDefault(job.getId(), emptyList());
          return new ServerGroupData(new com.netflix.spinnaker.clouddriver.titus.client.model.Job(job,
            EMPTY_LIST),
            jobScalingPolicies,
            jobLoadBalancers,
            state.tasks.getOrDefault(job.getId(), emptySet())
              .stream()
              .map(Task::getId)
              .collect(Collectors.toSet()),
            account.getName(),
            region.getName());
        })
        .collect(Collectors.toList());

      serverGroupDatas.forEach(data -> {
        String app = StringUtils.substringAfterLast(data.appNameKey, ":");

        if (StringUtils.isNotEmpty(app)) {
          state.appToClusters.computeIfAbsent(app, c -> new HashSet<>()).add(data.clusterKey);
          state.appsToServerGroups.computeIfAbsent(app, c -> new HashSet<>()).add(data.serverGroupKey);
          state.clusterKeyToApp.put(data.clusterKey, app);
          state.sgKeyToApp.put(data.serverGroupKey, app);
        }

        if (state.savedSnapshot) {
          currentApps.add(app);
          currentClusters.add(data.clusterKey);
          currentServerGroups.add(data.serverGroupKey);
        }

        cacheApplication(data, applicationCache);
        cacheCluster(data, clusterCache);
        cacheServerGroup(data, serverGroupCache);
        cacheImage(data, imageCache);
        for (Task task : (Set<Task>) state.tasks.getOrDefault(data.job.getId(), EMPTY_SET)) {
          InstanceData instanceData =
            new InstanceData(new com.netflix.spinnaker.clouddriver.titus.client.model.Task(task),
              data.job.getName(),
              account.getName(),
              region.getName());
          cacheInstance(instanceData, instancesCache);
        }
      });

      if (state.savedSnapshot) {
        List<String> missingClusters = state.appToClusters.entrySet().stream()
          .filter(e -> currentApps.contains(e.getKey()))
          .flatMap(e -> e.getValue().stream())
          .filter(c -> !currentClusters.contains(c))
          .collect(Collectors.toList());

        List<String> missingServerGroups = state.appsToServerGroups.entrySet().stream()
          .filter(e -> currentApps.contains(e.getKey()))
          .flatMap(e -> e.getValue().stream())
          .filter(c -> !currentServerGroups.contains(c))
          .collect(Collectors.toList());

        if (!missingClusters.isEmpty()) {
          log.info("Evicting {} clusters in {}", missingClusters.size(), getAgentType());
          cache.evictDeletedItems(CLUSTERS.ns, missingClusters);
          missingClusters.forEach(cluster -> {
            state.appToClusters.getOrDefault(state.clusterKeyToApp.get(cluster), emptySet()).remove(cluster);
            state.clusterKeyToApp.remove(cluster);
          });
        }

        if (!missingServerGroups.isEmpty()) {
          log.info("Evicting {} server groups in {}", missingServerGroups.size(), getAgentType());
          cache.evictDeletedItems(SERVER_GROUPS.ns, missingServerGroups);
          missingServerGroups.forEach(sg -> {
            state.appsToServerGroups.getOrDefault(state.sgKeyToApp.get(sg), emptySet()).remove(sg);
            state.sgKeyToApp.remove(sg);
          });
        }

        if (!state.completedInstanceIds.isEmpty()) {
          log.info("Evicting {} instances in {}", state.completedInstanceIds.size(), getAgentType());
          cache.evictDeletedItems(INSTANCES.ns, state.completedInstanceIds);
          state.completedInstanceIds = new HashSet<>();
        }
      }

      Map<String, Collection<CacheData>> cacheResults = new HashMap<>();
      cacheResults.put(APPLICATIONS.ns, applicationCache.values());
      cacheResults.put(CLUSTERS.ns, clusterCache.values());
      cacheResults.put(SERVER_GROUPS.ns, serverGroupCache.values());
      cacheResults.put(TARGET_GROUPS.ns, targetGroupCache.values());
      cacheResults.put(IMAGES.ns, imageCache.values());
      cacheResults.put(INSTANCES.ns, instancesCache.values());

      String action = state.savedSnapshot ? "Incrementally updating" : "Snapshot caching";

      log.info("{} {} applications in {}", action, applicationCache.size(), getAgentType());
      log.info("{} {} server groups in {}", action, serverGroupCache.size(), getAgentType());
      log.info("{} {} clusters in {}", action, clusterCache.size(), getAgentType());
      log.info("{} {} target groups in {}", action, targetGroupCache.size(), getAgentType());
      log.info("{} {} images in {}", action, imageCache.size(), getAgentType());
      log.info("{} {} instances in {}", action, instancesCache.size(), getAgentType());

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
      instanceCache.getAttributes().put(HEALTH.ns, singletonList(getTitusHealth(data.task)));
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

    class StreamingCacheState {
      AtomicInteger changes = new AtomicInteger(0);
      AtomicLong lastUpdate = new AtomicLong(0);

      Map<String, Job> jobs = new HashMap<>();
      Map<String, Set<Task>> tasks = new HashMap<>();

      Map<String, String> jobIdToApp = new HashMap<>();
      Map<String, Set<String>> appToClusters = new HashMap<>();
      Map<String, Set<String>> appsToServerGroups = new HashMap<>();
      Map<String, String> clusterKeyToApp = new HashMap<>();
      Map<String, String> sgKeyToApp = new HashMap<>();

      Set<String> completedInstanceIds = new HashSet<>();
      Set<String> updatedJobs = new HashSet<>();

      Boolean snapshotComplete = false;
      Boolean savedSnapshot = false;
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
        result.put("policy", emptyMap());
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
        ? emptySet()
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