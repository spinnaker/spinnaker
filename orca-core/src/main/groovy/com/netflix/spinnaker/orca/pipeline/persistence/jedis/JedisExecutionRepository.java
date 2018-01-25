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

package com.netflix.spinnaker.orca.pipeline.persistence.jedis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionSerializationException;
import com.netflix.spinnaker.orca.pipeline.persistence.StageSerializationException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.BinaryClient;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.google.common.collect.Maps.filterValues;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.DEFAULT_EXECUTION_ENGINE;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE;
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE;
import static java.lang.System.currentTimeMillis;
import static java.lang.String.format;
import static java.util.Collections.*;
import static net.logstash.logback.argument.StructuredArguments.value;
import static redis.clients.jedis.BinaryClient.LIST_POSITION.AFTER;
import static redis.clients.jedis.BinaryClient.LIST_POSITION.BEFORE;

@Component
public class JedisExecutionRepository extends AbstractRedisExecutionRepository {

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Autowired
  public JedisExecutionRepository(
    Registry registry,
    @Qualifier("redisClientDelegate") RedisClientDelegate redisClientDelegate,
    @Qualifier("previousRedisClientDelegate") Optional<RedisClientDelegate> previousRedisClientDelegate,
    @Qualifier("queryAllScheduler") Scheduler queryAllScheduler,
    @Qualifier("queryByAppScheduler") Scheduler queryByAppScheduler,
    @Value("${chunkSize.executionRepository:75}") Integer threadPoolChunkSize
  ) {
    super(registry, redisClientDelegate, previousRedisClientDelegate, queryAllScheduler, queryByAppScheduler, threadPoolChunkSize);
  }

  public JedisExecutionRepository(
    Registry registry,
    RedisClientDelegate redisClientDelegate,
    Optional<RedisClientDelegate> previousRedisClientDelegate,
    Integer threadPoolSize,
    Integer threadPoolChunkSize
  ) {
    super(
      registry,
      redisClientDelegate,
      previousRedisClientDelegate,
      Schedulers.from(Executors.newFixedThreadPool(10)),
      Schedulers.from(Executors.newFixedThreadPool(threadPoolSize)),
      threadPoolChunkSize
    );
  }

  @Override
  public void store(@Nonnull Execution execution) {
    getRedisDelegateForId(execution.getId()).withTransaction(tx -> {
      storeExecutionInternal(tx, execution);
      if (execution.getType() == PIPELINE) {
        tx.zadd(executionsByPipelineKey(
          execution.getPipelineConfigId()),
          execution.getBuildTime() != null ? execution.getBuildTime() : currentTimeMillis(),
          execution.getId());
      }
      tx.exec();
    });
  }

  @Override
  public void storeStage(@Nonnull Stage stage) {
    ExecutionType type = stage.getExecution().getType();
    String key = format("%s:%s", type, stage.getExecution().getId());
    getRedisDelegateForId(key).withTransaction(tx -> {
      storeStageInternal(tx, type, stage);
      tx.exec();
    });
  }

  @Override
  public void removeStage(@Nonnull Execution execution, @Nonnull String stageId) {
    String key = format("%s:%s", execution.getType(), execution.getId());
    String indexKey = format("%s:stageIndex", key);
    getRedisDelegateForId(key).withCommandsClient(c -> {
      List<String> stageIds = new ArrayList<>(Arrays.asList(c.hget(key, "stageIndex").split(",")));
      stageIds.remove(stageId);
      boolean hasDiscreteIndex = c.exists(indexKey);
      List<String> keys = c.hkeys(key).stream()
        .filter(k -> k.startsWith("stage." + stageId))
        .collect(Collectors.toList());

      getRedisDelegateForId(key).withTransaction(tx -> {
        tx.hset(key, "stageIndex", stageIds.stream().collect(Collectors.joining(",")));
        if (hasDiscreteIndex) {
          tx.lrem(indexKey, 0, stageId);
        } else {
          tx.rpush(indexKey, stageIds.toArray(new String[0]));
        }
        tx.hdel(key, keys.toArray(new String[0]));
        tx.exec();
      });
    });
  }

  @Override
  public void addStage(@Nonnull Stage stage) {
    if (stage.getSyntheticStageOwner() == null || stage.getParentStageId() == null) {
      throw new IllegalArgumentException("Only synthetic stages can be inserted ad-hoc");
    }

    ExecutionType type = stage.getExecution().getType();
    String key = format("%s:%s", type, stage.getExecution().getId());
    String indexKey = format("%s:stageIndex", key);
    getRedisDelegateForId(key).withTransaction(tx -> {
      storeStageInternal(tx, type, stage);
      BinaryClient.LIST_POSITION pos = stage.getSyntheticStageOwner() == STAGE_BEFORE ? BEFORE : AFTER;
      tx.linsert(indexKey, pos, stage.getParentStageId(), stage.getId());
      tx.exec();
    });
    // TODO : Remove all use of execution.stageIndex
    getRedisDelegateForId(key).withCommandsClient(c -> {
      List<String> stageIds = c.lrange(indexKey, 0, -1);
      c.hset(key, "stageIndex", stageIds.stream()
        .collect(Collectors.joining(",")));
    });
  }

  @Override
  public @Nonnull
  Execution retrieve(@Nonnull ExecutionType type, @Nonnull String id) {
    return retrieveInternal(getRedisDelegateForId(format("%s:%s", type, id)), type, id);
  }

  @Override
  public @Nonnull
  Observable<Execution> retrieve(@Nonnull ExecutionType type) {
    List<Observable<Execution>> observables = allRedisDelegates().stream()
      .map(d -> all(type, d))
      .collect(Collectors.toList());
    return Observable.merge(observables);
  }

  @Override
  public @Nonnull
  Observable<Execution> retrievePipelinesForApplication(@Nonnull String application) {
    List<Observable<Execution>> observables = allRedisDelegates().stream()
      .map(d -> allForApplication(PIPELINE, application, d))
      .collect(Collectors.toList());
    return Observable.merge(observables);
  }

  @Override
  public @Nonnull
  Observable<Execution> retrievePipelinesForPipelineConfigId(@Nonnull String pipelineConfigId,
                                                             @Nonnull ExecutionCriteria criteria) {
    /*
     * Fetch pipeline ids from the primary redis (and secondary if configured)
     */
    Map<RedisClientDelegate, List<String>> filteredPipelineIdsByDelegate = new HashMap<>();
    if (!criteria.getStatuses().isEmpty()) {
      final List<Response<String>> fetches = new ArrayList<>();
      allRedisDelegates().forEach(d -> d.withCommandsClient(c -> {
        List<String> pipelineKeys = new ArrayList<>(c.zrevrange(executionsByPipelineKey(pipelineConfigId), 0, -1));
        if (pipelineKeys.isEmpty()) {
          return;
        }
        Set<String> allowedExecutionStatuses = new HashSet<>(criteria.getStatuses());

        d.withMultiKeyPipeline(p -> {
          fetches.addAll(pipelineKeys.stream()
            .map(k -> p.hget(pipelineKey(k), "status"))
            .collect(Collectors.toList()));
          p.sync();
        });

        AtomicInteger index = new AtomicInteger();
        fetches.forEach(e -> {
          if (allowedExecutionStatuses.contains(e.get())) {
            filteredPipelineIdsByDelegate.computeIfAbsent(d, p -> new ArrayList<>()).add(pipelineKeys.get(index.get()));
          }
          index.incrementAndGet();
        });
      }));
    }

    Func2<RedisClientDelegate, Iterable<String>, Func1<String, Iterable<String>>> fnBuilder =
      (RedisClientDelegate redisClientDelegate, Iterable<String> pipelineIds) ->
        (String key) ->
          redisClientDelegate.withCommandsClient(p ->
            !criteria.getStatuses().isEmpty() ? pipelineIds : p.zrevrange(key, 0, (criteria.getLimit() - 1))
          );

    /*
     * Construct an observable that will retrieve pipelines from the primary redis
     */
    List<String> currentPipelineIds = filteredPipelineIdsByDelegate.getOrDefault(redisClientDelegate, new ArrayList<>());
    currentPipelineIds = currentPipelineIds.subList(0, Math.min(criteria.getLimit(), currentPipelineIds.size()));

    Observable<Execution> currentObservable = retrieveObservable(
      PIPELINE,
      executionsByPipelineKey(pipelineConfigId),
      fnBuilder.call(redisClientDelegate, currentPipelineIds),
      queryByAppScheduler,
      redisClientDelegate
    );

    if (previousRedisClientDelegate.isPresent()) {
      /*
       * If configured, construct an observable the will retrieve pipelines from the secondary redis
       */
      List<String> previousPipelineIds = filteredPipelineIdsByDelegate.getOrDefault(previousRedisClientDelegate.get(), new ArrayList<>());
      previousPipelineIds.removeAll(currentPipelineIds);
      previousPipelineIds = previousPipelineIds.subList(0, Math.min(criteria.getLimit(), previousPipelineIds.size()));

      Observable<Execution> previousObservable = retrieveObservable(
        PIPELINE,
        executionsByPipelineKey(pipelineConfigId),
        fnBuilder.call(previousRedisClientDelegate.get(), previousPipelineIds),
        queryByAppScheduler,
        previousRedisClientDelegate.get()
      );

      // merge primary + secondary observables
      return Observable.merge(currentObservable, previousObservable);
    }

    return currentObservable;
  }

  @Override
  public @Nonnull
  Observable<Execution> retrieveOrchestrationsForApplication(@Nonnull String application, @Nonnull ExecutionCriteria criteria) {
    String allOrchestrationsKey = appKey(ORCHESTRATION, application);

    /*
     * Fetch orchestration ids from the primary redis (and secondary if configured)
     */
    Map<RedisClientDelegate, List<String>> filteredOrchestrationIdsByDelegate = new HashMap<>();
    if (!criteria.getStatuses().isEmpty()) {
      final List<Response<String>> fetches = new ArrayList<>();
      allRedisDelegates().forEach(d -> d.withCommandsClient(c -> {
        List<String> orchestrationKeys = new ArrayList<>(c.smembers(allOrchestrationsKey));
        if (orchestrationKeys.isEmpty()) {
          return;
        }
        Set<String> allowedExecutionStatuses = new HashSet<>(criteria.getStatuses());

        d.withMultiKeyPipeline(p -> {
          fetches.addAll(orchestrationKeys.stream()
            .map(k -> p.hget(orchestrationKey(k), "status"))
            .collect(Collectors.toList()));
          p.sync();
        });
        AtomicInteger index = new AtomicInteger();
        fetches.forEach(e -> {
          if (allowedExecutionStatuses.contains(e.get())) {
            filteredOrchestrationIdsByDelegate.computeIfAbsent(d, o -> new ArrayList<>()).add(orchestrationKeys.get(index.get()));
          }
          index.incrementAndGet();
        });
      }));
    }

    Func2<RedisClientDelegate, Iterable<String>, Func1<String, Iterable<String>>> fnBuilder =
      (RedisClientDelegate redisClientDelegate, Iterable<String> orchestrationIds) ->
        (String key) -> (
          redisClientDelegate.withCommandsClient(c -> {
            if (!criteria.getStatuses().isEmpty()) {
              return orchestrationIds;
            }
            List<String> unfiltered = new ArrayList<>(c.smembers(key));
            return unfiltered.subList(0, Math.min(criteria.getLimit(), unfiltered.size()));
          }));

    /*
     * Construct an observable that will retrieve orchestrations frcm the primary redis
     */
    List<String> currentOrchestrationIds = filteredOrchestrationIdsByDelegate.getOrDefault(redisClientDelegate, new ArrayList<>());
    currentOrchestrationIds = currentOrchestrationIds.subList(0, Math.min(criteria.getLimit(), currentOrchestrationIds.size()));

    Observable<Execution> currentObservable = retrieveObservable(
      ORCHESTRATION,
      allOrchestrationsKey,
      fnBuilder.call(redisClientDelegate, currentOrchestrationIds),
      queryByAppScheduler,
      redisClientDelegate
    );

    if (previousRedisClientDelegate.isPresent()) {
      /*
       * If configured, construct an observable the will retrieve orchestrations from the secondary redis
       */
      List<String> previousOrchestrationIds = filteredOrchestrationIdsByDelegate.getOrDefault(previousRedisClientDelegate.get(), new ArrayList<>());
      previousOrchestrationIds.removeAll(currentOrchestrationIds);
      previousOrchestrationIds = previousOrchestrationIds.subList(0, Math.min(criteria.getLimit(), previousOrchestrationIds.size()));

      Observable<Execution> previousObservable = retrieveObservable(
        ORCHESTRATION,
        allOrchestrationsKey,
        fnBuilder.call(previousRedisClientDelegate.get(), previousOrchestrationIds),
        queryByAppScheduler,
        previousRedisClientDelegate.get()
      );

      // merge primary + secondary observables
      return Observable.merge(currentObservable, previousObservable);
    }

    return currentObservable;
  }

  @Override
  public @Nonnull
  Execution retrieveOrchestrationForCorrelationId(
    @Nonnull String correlationId) throws ExecutionNotFoundException {
    String key = format("correlation:%s", correlationId);
    return getRedisDelegateForId(key).withCommandsClient(correlationRedis -> {
      String orchestrationId = correlationRedis.get(key);

      if (orchestrationId != null) {
        Execution orchestration = retrieveInternal(
          getRedisDelegateForId(orchestrationId),
          ORCHESTRATION,
          orchestrationId);
        if (!orchestration.getStatus().isComplete()) {
          return orchestration;
        }
        correlationRedis.del(key);
      }
      throw new ExecutionNotFoundException(
        format("No Orchestration found for correlation ID %s", correlationId)
      );
    });
  }

  private void storeExecutionInternal(Transaction tx, Execution execution) {
    tx.sadd(alljobsKey(execution.getType()), execution.getId());
    tx.sadd(appKey(execution.getType(), execution.getApplication()), execution.getId());

    String key = format("%s:%s", execution.getType(), execution.getId());
    String indexKey = format("%s:stageIndex", key);

    Map<String, String> map = new HashMap<>();
    try {
      map.put("application", execution.getApplication());
      map.put("canceled", String.valueOf(execution.isCanceled()));
      map.put("limitConcurrent", String.valueOf(execution.isLimitConcurrent()));
      map.put("buildTime", String.valueOf(execution.getBuildTime() != null ? execution.getBuildTime() : 0L));
      map.put("startTime", execution.getStartTime() != null ? execution.getStartTime().toString() : null);
      map.put("endTime", execution.getEndTime() != null ? execution.getEndTime().toString() : null);
      map.put("status", execution.getStatus().name());
      map.put("authentication", mapper.writeValueAsString(execution.getAuthentication()));
      map.put("paused", mapper.writeValueAsString(execution.getPaused()));
      map.put("keepWaitingPipelines", String.valueOf(execution.isKeepWaitingPipelines()));
      if (execution.getExecutionEngine().name() != null) {
        map.put("executionEngine", String.valueOf(execution.getExecutionEngine().name()));
      } else {
        map.put("executionEngine", DEFAULT_EXECUTION_ENGINE.name());
      }
      map.put("origin", execution.getOrigin());
      map.put("trigger", mapper.writeValueAsString(execution.getTrigger()));
      if (!execution.getStages().isEmpty()) {
        map.put("stageIndex", execution.getStages().stream().map(Stage::getId).collect(Collectors.joining(",")));
      }
    } catch (JsonProcessingException e) {
      throw new ExecutionSerializationException("Failed serializing execution", e);
    }
    // TODO: remove this and only use the list
    if (!execution.getStages().isEmpty()) {
      tx.del(indexKey);
      tx.rpush(indexKey, execution.getStages().stream()
        .map(Stage::getId)
        .collect(Collectors.toList())
        .toArray(new String[0])
      );
    }
    execution.getStages().forEach(s -> map.putAll(serializeStage(s)));

    if (execution.getType() == PIPELINE) {
      try {
        map.put("name", execution.getName());
        map.put("pipelineConfigId", execution.getPipelineConfigId());
        map.put("notifications", mapper.writeValueAsString(execution.getNotifications()));
        map.put("initialConfig", mapper.writeValueAsString(execution.getInitialConfig()));
      } catch (JsonProcessingException e) {
        throw new ExecutionSerializationException("Failed serializing execution", e);
      }
    } else if (execution.getType() == ORCHESTRATION) {
      map.put("description", execution.getDescription());
    }
    if (execution.getTrigger().containsKey("correlationId")) {
      tx.set(
        format("correlation:%s", execution.getTrigger().get("correlationId")),
        execution.getId()
      );
    }

    tx.hdel(key, "config");
    tx.hmset(key, filterValues(map, Objects::nonNull));
  }

  private void storeStageInternal(Transaction tx, ExecutionType type, Stage stage) {
    String key = format("%s:%s", type, stage.getExecution().getId());

    Map<String, String> serializedStage = serializeStage(stage);
    List<String> keysToRemove = serializedStage.entrySet().stream()
      .filter(e -> e.getValue() == null)
      .map(Map.Entry::getKey)
      .collect(Collectors.toList());

    serializedStage.values().removeIf(Objects::isNull);
    tx.hmset(key, serializedStage);

    if (!keysToRemove.isEmpty()) {
      tx.hdel(key, keysToRemove.toArray(new String[0]));
    }
  }

  private Execution retrieveInternal(RedisClientDelegate redisClientDelegate, ExecutionType type, String id) throws ExecutionNotFoundException {
    String key = format("%s:%s", type, id);
    String indexKey = format("%s:stageIndex", key);

    boolean exists = redisClientDelegate.withCommandsClient(c -> {
      return c.exists(key);
    });
    if (!exists) {
      throw new ExecutionNotFoundException("No ${type} found for $id");
    }

    final Map<String, String> map = new HashMap<>();
    final List<String> stageIds = new ArrayList<>();

    redisClientDelegate.withTransaction(tx -> {
      Response<Map<String, String>> execResponse = tx.hgetAll(key);
      Response<List<String>> indexResponse = tx.lrange(indexKey, 0, -1);
      tx.exec();

      map.putAll(execResponse.get());

      if (!indexResponse.get().isEmpty()) {
        stageIds.addAll(indexResponse.get());
      } else if (StringUtils.isNotEmpty(map.get("stageIndex"))) {
        stageIds.addAll(Arrays.asList(map.get("stageIndex").split(",")));
      }
    });

    Execution execution = new Execution(type, id, map.get("application"));

    try {
      execution.setCanceled(Boolean.parseBoolean(map.get("canceled")));
      execution.setCanceledBy(map.get("canceledBy"));
      execution.setCancellationReason(map.get("cancellationReason"));
      execution.setLimitConcurrent(Boolean.parseBoolean(map.get("limitConcurrent")));
      execution.setBuildTime(NumberUtils.createLong(map.get("buildTime")));
      execution.setStartTime(NumberUtils.createLong(map.get("startTime")));
      execution.setEndTime(NumberUtils.createLong(map.get("endTime")));
      if (map.get("status") != null) {
        execution.setStatus(ExecutionStatus.valueOf(map.get("status")));
      }
      execution.setAuthentication(mapper.readValue(map.get("authentication"), Execution.AuthenticationDetails.class));
      if (map.get("paused") != null) {
        execution.setPaused(mapper.readValue(map.get("paused"), Execution.PausedDetails.class));
      }
      execution.setKeepWaitingPipelines(Boolean.parseBoolean(map.get("keepWaitingPipelines")));
      execution.setOrigin(map.get("origin"));
      if (map.get("trigger") != null) {
        Map<String, Object> trigger = mapper.readValue(map.get("trigger"), Map.class);
        if (trigger.containsKey("parentExecution")) {
          trigger.put("parentExecution", mapper.convertValue(trigger.get("parentExecution"), Execution.class));
        }
        execution.getTrigger().putAll(trigger);
      }
    } catch (Exception e) {
      throw new ExecutionSerializationException("Failed serializing execution json", e);
    }

    try {
      if (map.get("executionEngine") == null) {
        execution.setExecutionEngine(DEFAULT_EXECUTION_ENGINE);
      } else {
        execution.setExecutionEngine(Execution.ExecutionEngine.valueOf(map.get("executionEngine")));
      }
    } catch (IllegalArgumentException e) {
      execution.setExecutionEngine(DEFAULT_EXECUTION_ENGINE);
    }

    stageIds.forEach(stageId -> {
      String prefix = format("stage.%s.", stageId);
      Stage stage = new Stage();
      try {
        stage.setId(stageId);
        stage.setRefId(map.get(prefix + "refId"));
        stage.setType(map.get(prefix + "type"));
        stage.setName(map.get(prefix + "name"));
        stage.setStartTime(NumberUtils.createLong(map.get(prefix + "startTime")));
        stage.setEndTime(NumberUtils.createLong(map.get(prefix + "endTime")));
        stage.setStatus(ExecutionStatus.valueOf(map.get(prefix + "status")));
        if (map.get(prefix + "syntheticStageOwner") != null) {
          stage.setSyntheticStageOwner(SyntheticStageOwner.valueOf(map.get(prefix + "syntheticStageOwner")));
        }
        stage.setParentStageId(map.get(prefix + "parentStageId"));
        if (map.get(prefix + "requisiteStageRefIds") != null) {
          stage.setRequisiteStageRefIds(Arrays.asList(map.get(prefix + "requisiteStageRefIds").split(",")));
        } else {
          stage.setRequisiteStageRefIds(emptySet());
        }
        stage.setScheduledTime(NumberUtils.createLong(map.get(prefix + "scheduledTime")));
        if (map.get(prefix + "context") != null) {
          stage.setContext(mapper.readValue(map.get(prefix + "context"), MAP_STRING_TO_OBJECT));
        } else {
          stage.setContext(emptyMap());
        }
        if (map.get(prefix + "outputs") != null) {
          stage.setOutputs(mapper.readValue(map.get(prefix + "outputs"), MAP_STRING_TO_OBJECT));
        } else {
          stage.setOutputs(emptyMap());
        }
        if (map.get(prefix + "tasks") != null) {
          stage.setTasks(mapper.readValue(map.get(prefix + "tasks"), LIST_OF_TASKS));
        } else {
          stage.setTasks(emptyList());
        }
        stage.setExecution(execution);
        execution.getStages().add(stage);
      } catch (IOException e) {
        throw new StageSerializationException("Failed serializing stage json", e);
      }
    });

    if (execution.getType() == PIPELINE) {
      execution.setName(map.get("name"));
      execution.setPipelineConfigId(map.get("pipelineConfigId"));
      try {
        if (map.get("notifications") != null) {
          execution.getNotifications().addAll(mapper.readValue(map.get("notifications"), List.class));
        }
        if (map.get("initialConfig") != null) {
          execution.getInitialConfig().putAll(mapper.readValue(map.get("initialConfig"), Map.class));
        }
      } catch (IOException e) {
        throw new ExecutionSerializationException("Failed serializing execution json", e);
      }
    } else if (execution.getType() == ORCHESTRATION) {
      execution.setDescription(map.get("description"));
    }
    return execution;
  }


  private Observable<Execution> all(ExecutionType type, RedisClientDelegate redisClientDelegate) {
    return retrieveObservable(type, alljobsKey(type), queryAllScheduler, redisClientDelegate);
  }

  private Observable<Execution> allForApplication(ExecutionType type, String application, RedisClientDelegate redisClientDelegate) {
    return retrieveObservable(type, appKey(type, application), queryByAppScheduler, redisClientDelegate);
  }

  private Observable<Execution> retrieveObservable(ExecutionType type,
                                                   String lookupKey,
                                                   Scheduler scheduler,
                                                   RedisClientDelegate redisClientDelegate) {

    Func0<Func1<String, Iterable<String>>> fnBuilder = () -> (String key) ->
      redisClientDelegate.withCommandsClient(c -> {
        return c.smembers(key);
      });

    return retrieveObservable(type, lookupKey, fnBuilder.call(), scheduler, redisClientDelegate);
  }

  @SuppressWarnings("unchecked")
  private Observable<Execution> retrieveObservable(ExecutionType type,
                                                   String lookupKey,
                                                   Func1<String, Iterable<String>> lookupKeyFetcher,
                                                   Scheduler scheduler,
                                                   RedisClientDelegate redisClientDelegate) {
    return Observable
      .just(lookupKey)
      .flatMapIterable(lookupKeyFetcher)
      .buffer(chunkSize)
      .flatMap((Collection<String> ids) ->
        Observable
          .from(ids)
          .flatMap((String executionId) -> {
            try {
              return Observable.just(retrieveInternal(redisClientDelegate, type, executionId));
            } catch (ExecutionNotFoundException ignored) {
              log.info("Execution ({}) does not exist", value("executionId", executionId));
              redisClientDelegate.withCommandsClient(c -> {
                if (c.type(lookupKey).equals("zset")) {
                  c.zrem(lookupKey, executionId);
                } else {
                  c.srem(lookupKey, executionId);
                }
              });
            } catch (Exception e) {
              log.error("Failed to retrieve execution '{}'", value("executionId", executionId), e);
            }
            return Observable.empty();
          }))
      .subscribeOn(scheduler);
  }
}
