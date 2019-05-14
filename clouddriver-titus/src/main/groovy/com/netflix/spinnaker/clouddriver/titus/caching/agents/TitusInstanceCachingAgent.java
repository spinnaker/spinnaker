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

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.histogram.PercentileTimer;
import com.netflix.spinnaker.cats.agent.*;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.caching.Keys;
import com.netflix.spinnaker.clouddriver.titus.caching.TitusCachingProvider;
import com.netflix.spinnaker.clouddriver.titus.caching.utils.AwsLookupUtil;
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient;
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion;
import com.netflix.spinnaker.clouddriver.titus.client.model.Task;
import com.netflix.spinnaker.clouddriver.titus.client.model.TaskState;
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caching agent for Titus tasks, mapping to the concept of Spinnaker Instances A Titus job has a
 * set of Titus tasks.
 */
public class TitusInstanceCachingAgent implements CachingAgent {

  private static final Logger log = LoggerFactory.getLogger(TitusInstanceCachingAgent.class);
  private static final TypeReference<Map<String, Object>> ATTRIBUTES =
      new TypeReference<Map<String, Object>>() {};

  private static final java.util.Set<AgentDataType> types =
      Collections.unmodifiableSet(
          Stream.of(AUTHORITATIVE.forType(INSTANCES.ns), INFORMATIVE.forType(SERVER_GROUPS.ns))
              .collect(Collectors.toSet()));

  private static final List<TaskState> DOWN_TASK_STATES =
      Arrays.asList(
          TaskState.STOPPED,
          TaskState.FAILED,
          TaskState.CRASHED,
          TaskState.FINISHED,
          TaskState.DEAD,
          TaskState.TERMINATING);
  private static final List<TaskState> STARTING_TASK_STATES =
      Arrays.asList(TaskState.STARTING, TaskState.DISPATCHED, TaskState.PENDING, TaskState.QUEUED);

  private final TitusClient titusClient;
  private final NetflixTitusCredentials account;
  private final TitusRegion region;
  private final ObjectMapper objectMapper;
  private final Provider<AwsLookupUtil> awsLookupUtil;
  private final Id metricId;
  private final Registry registry;

  public TitusInstanceCachingAgent(
      TitusClientProvider titusClientProvider,
      NetflixTitusCredentials account,
      TitusRegion region,
      ObjectMapper objectMapper,
      Registry registry,
      Provider<AwsLookupUtil> awsLookupUtil) {
    this.account = account;
    this.region = region;

    this.objectMapper = objectMapper;
    this.titusClient = titusClientProvider.getTitusClient(account, region.getName());
    this.awsLookupUtil = awsLookupUtil;
    this.registry = registry;

    this.metricId =
        registry
            .createId("titus.cache.instance")
            .withTag("account", account.getName())
            .withTag("region", region.getName());
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public Optional<Map<String, String>> getCacheKeyPatterns() {
    Map<String, String> cachekeyPatterns = new HashMap<>();
    cachekeyPatterns.put(
        SERVER_GROUPS.ns, Keys.getServerGroupV2Key("*", "*", account.getName(), region.getName()));
    cachekeyPatterns.put(
        INSTANCES.ns, Keys.getInstanceV2Key("*", account.getName(), region.getName()));
    return Optional.of(cachekeyPatterns);
  }

  @Override
  public String getAgentType() {
    return account.getName()
        + "/"
        + region.getName()
        + "/"
        + TitusInstanceCachingAgent.class.getSimpleName();
  }

  @Override
  public String getProviderName() {
    return TitusCachingProvider.PROVIDER_NAME;
  }

  @Override
  public boolean handlesAccount(String accountName) {
    return false;
  }

  static class MutableCacheData implements CacheData {

    final String id;
    int ttlSeconds = -1;
    final Map<String, Object> attributes = new HashMap<>();
    final Map<String, Collection<String>> relationships = new HashMap<>();

    public MutableCacheData(String id) {
      this.id = id;
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

  private Map<String, CacheData> createCache() {
    return new HashMap<>();
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in {}", getAgentType());
    Long startTime = System.currentTimeMillis();

    List<Task> tasks = titusClient.getAllTasks();

    // TODO emjburns: do we want to use timer or PercentileTimer?
    // PercentileTimer gives us better data but is more runtime expensive to call
    PercentileTimer.get(registry, metricId.withTag("operation", "getAllTasks"))
        .record(System.currentTimeMillis() - startTime, MILLISECONDS);

    // Titus tasks only know the job ID, we get all job names from titus in one call
    // and use them to cache the instances.
    Long jobNamesStartTime = System.currentTimeMillis();
    Map<String, String> jobNames = titusClient.getAllJobNames();
    PercentileTimer.get(registry, metricId.withTag("operation", "getAllJobNames"))
        .record(System.currentTimeMillis() - jobNamesStartTime, MILLISECONDS);

    Map<String, CacheData> serverGroups = createCache();
    Map<String, CacheData> instances = createCache();

    for (Task task : tasks) {
      InstanceData data =
          new InstanceData(
              task, jobNames.get(task.getJobId()), account.getName(), region.getName());
      cacheInstance(data, instances);
      cacheServerGroup(data, serverGroups);
    }

    log.info("Caching {} instances in {}", instances.size(), getAgentType());
    log.info("Caching {} server groups in {}", serverGroups.size(), getAgentType());

    Map<String, Collection<CacheData>> cacheResult = new HashMap<>();
    cacheResult.put(INSTANCES.ns, instances.values());
    cacheResult.put(SERVER_GROUPS.ns, serverGroups.values());

    PercentileTimer.get(registry, metricId.withTag("operation", "loadData"))
        .record(System.currentTimeMillis() - startTime, MILLISECONDS);
    log.info(
        "Caching completed in {}s in {}",
        MILLISECONDS.toSeconds(System.currentTimeMillis() - startTime),
        getAgentType());

    return new DefaultCacheResult(cacheResult);
  }

  private void cacheInstance(InstanceData data, Map<String, CacheData> instances) {
    CacheData instanceCache =
        instances.getOrDefault(data.instanceId, new MutableCacheData(data.instanceId));
    instanceCache.getAttributes().putAll(objectMapper.convertValue(data.task, ATTRIBUTES));
    instanceCache
        .getAttributes()
        .put(HEALTH.ns, Collections.singletonList(getTitusHealth(data.task)));
    instanceCache.getAttributes().put("task", data.task);
    instanceCache.getAttributes().put("jobId", data.jobId);

    if (!data.serverGroup.isEmpty()) {
      instanceCache
          .getRelationships()
          .computeIfAbsent(SERVER_GROUPS.ns, key -> new HashSet<>())
          .add(data.serverGroup);
    } else {
      instanceCache
          .getRelationships()
          .computeIfAbsent(SERVER_GROUPS.ns, key -> new HashSet<>())
          .clear();
    }
    instances.put(data.instanceId, instanceCache);
  }

  private void cacheServerGroup(InstanceData data, Map<String, CacheData> serverGroups) {
    if (!data.serverGroup.isEmpty()) {
      CacheData serverGroupCache =
          serverGroups.getOrDefault(data.serverGroup, new MutableCacheData(data.serverGroup));
      serverGroupCache
          .getRelationships()
          .computeIfAbsent(INSTANCES.ns, key -> new HashSet<>())
          .add(data.instanceId);
      serverGroups.put(data.serverGroup, serverGroupCache);
    }
  }

  private Map<String, String> getTitusHealth(Task task) {
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

  private String getAwsAccountId(String account, String region) {
    return awsLookupUtil.get().awsAccountId(account, region);
  }

  private String getAwsVpcId(String account, String region) {
    return awsLookupUtil.get().awsVpcId(account, region);
  }

  private class InstanceData {
    // The instance key, not the task id
    private final String instanceId;
    private final Task task;
    private final String jobId;
    private final String serverGroup;

    InstanceData(Task task, String jobName, String account, String region) {
      this.instanceId = Keys.getInstanceV2Key(task.getId(), account, region);
      this.task = task;
      this.jobId = task.getJobId();
      this.serverGroup = jobName != null ? Keys.getServerGroupV2Key(jobName, account, region) : "";
    }
  }
}
