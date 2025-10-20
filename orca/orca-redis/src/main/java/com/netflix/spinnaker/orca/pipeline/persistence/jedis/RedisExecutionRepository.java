/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.persistence.jedis;

import static com.google.common.collect.Maps.filterValues;
import static com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner.STAGE_BEFORE;
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.BUFFERED;
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.CANCELED;
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.NOT_STARTED;
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION;
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static com.netflix.spinnaker.orca.config.RedisConfiguration.Clients.EXECUTION_REPOSITORY;
import static com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl.NO_TRIGGER;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.*;
import static net.logstash.logback.argument.StructuredArguments.value;
import static redis.clients.jedis.ListPosition.AFTER;
import static redis.clients.jedis.ListPosition.BEFORE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.RedisClientSelector;
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner;
import com.netflix.spinnaker.orca.api.pipeline.models.*;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution.PausedDetails;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.pipeline.model.*;
import com.netflix.spinnaker.orca.pipeline.persistence.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.ListPosition;
import redis.clients.jedis.Response;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;

public class RedisExecutionRepository implements ExecutionRepository {

  private static final TypeReference<List<TaskExecution>> LIST_OF_TASKS =
      new TypeReference<List<TaskExecution>>() {};
  private static final TypeReference<Map<String, Object>> MAP_STRING_TO_OBJECT =
      new TypeReference<Map<String, Object>>() {};
  private static final TypeReference<List<SystemNotification>> LIST_OF_SYSTEM_NOTIFICATIONS =
      new TypeReference<List<SystemNotification>>() {};

  private final RedisClientDelegate redisClientDelegate;
  private final Optional<RedisClientDelegate> previousRedisClientDelegate;
  private final ObjectMapper mapper = OrcaObjectMapper.getInstance();
  private final int chunkSize;
  private final Scheduler queryAllScheduler;
  private final Scheduler queryByAppScheduler;
  private final Registry registry;
  private static String bufferedPrefix;

  private final Logger log = LoggerFactory.getLogger(getClass());

  public RedisExecutionRepository(
      Registry registry,
      RedisClientSelector redisClientSelector,
      Scheduler queryAllScheduler,
      Scheduler queryByAppScheduler,
      Integer threadPoolChunkSize,
      String bufferedPrefix) {
    this.registry = registry;
    this.redisClientDelegate = redisClientSelector.primary(EXECUTION_REPOSITORY);
    this.previousRedisClientDelegate = redisClientSelector.previous(EXECUTION_REPOSITORY);
    this.queryAllScheduler = queryAllScheduler;
    this.queryByAppScheduler = queryByAppScheduler;
    this.chunkSize = threadPoolChunkSize;
    this.bufferedPrefix = bufferedPrefix;
  }

  public RedisExecutionRepository(
      Registry registry,
      RedisClientSelector redisClientSelector,
      Integer threadPoolSize,
      Integer threadPoolChunkSize) {
    this.registry = registry;
    this.redisClientDelegate = redisClientSelector.primary(EXECUTION_REPOSITORY);
    this.previousRedisClientDelegate = redisClientSelector.previous(EXECUTION_REPOSITORY);

    this.queryAllScheduler = Schedulers.from(Executors.newFixedThreadPool(10));
    this.queryByAppScheduler = Schedulers.from(Executors.newFixedThreadPool(threadPoolSize));
    this.chunkSize = threadPoolChunkSize;
  }

  @Override
  public void store(@Nonnull PipelineExecution execution) {
    RedisClientDelegate delegate = getRedisDelegate(execution);
    storeExecutionInternal(delegate, execution);

    if (execution.getType() == PIPELINE) {
      delegate.withCommandsClient(
          c -> {
            c.zadd(
                executionsByPipelineKey(execution.getPipelineConfigId()),
                execution.getBuildTime() != null ? execution.getBuildTime() : currentTimeMillis(),
                execution.getId());
          });
    }
  }

  @Override
  public void storeStage(@Nonnull StageExecution stage) {
    storeStageInternal(getRedisDelegate(stage), stage, false);
  }

  @Override
  public void updateStageContext(@Nonnull StageExecution stage) {
    RedisClientDelegate delegate = getRedisDelegate(stage);
    String key = executionKey(stage);
    String contextKey = format("stage.%s.context", stage.getId());
    delegate.withCommandsClient(
        c -> {
          try {
            c.hset(key, contextKey, mapper.writeValueAsString(stage.getContext()));
          } catch (JsonProcessingException e) {
            throw new StageSerializationException(
                format(
                    "Failed serializing stage, executionId: %s, stageId: %s",
                    stage.getExecution().getId(), stage.getId()),
                e);
          }
        });
  }

  @Override
  public void removeStage(@Nonnull PipelineExecution execution, @Nonnull String stageId) {
    RedisClientDelegate delegate = getRedisDelegate(execution);
    String key = executionKey(execution);
    String indexKey = format("%s:stageIndex", key);

    List<String> stageKeys =
        delegate.withCommandsClient(
            c -> {
              return c.hkeys(key).stream()
                  .filter(k -> k.startsWith("stage." + stageId))
                  .collect(Collectors.toList());
            });

    delegate.withTransaction(
        tx -> {
          tx.lrem(indexKey, 0, stageId);
          tx.hdel(key, stageKeys.toArray(new String[0]));
          tx.exec();
        });
  }

  @Override
  public void addStage(@Nonnull StageExecution stage) {
    if (stage.getSyntheticStageOwner() == null || stage.getParentStageId() == null) {
      throw new IllegalArgumentException("Only synthetic stages can be inserted ad-hoc");
    }

    storeStageInternal(getRedisDelegate(stage), stage, true);
  }

  @Override
  public void cancel(ExecutionType type, @Nonnull String id) {
    cancel(type, id, null, null);
  }

  @Override
  public void cancel(ExecutionType type, @Nonnull String id, String user, String reason) {
    ImmutablePair<String, RedisClientDelegate> pair = fetchKey(id);
    RedisClientDelegate delegate = pair.getRight();
    PipelineExecution execution = retrieve(type, id);
    delegate.withCommandsClient(
        c -> {
          Map<String, String> data = new HashMap<>();
          data.put("canceled", "true");
          execution.setCanceled(true);
          if (StringUtils.isNotEmpty(user)) {
            data.put("canceledBy", user);
            execution.setCanceledBy(user);
          }
          if (StringUtils.isNotEmpty(reason)) {
            data.put("cancellationReason", reason);
            execution.setCancellationReason(reason);
          }
          ExecutionStatus currentStatus = ExecutionStatus.valueOf(c.hget(pair.getLeft(), "status"));
          if (currentStatus == ExecutionStatus.NOT_STARTED) {
            data.put("status", ExecutionStatus.CANCELED.name());
          } else if (!execution.getStatus().isComplete()
              && execution.getStages().stream()
                  .allMatch(
                      stage ->
                          stage.getStatus().isComplete() || stage.getStatus() == NOT_STARTED)) {
            // In some cases, a race condition could occur between a pipeline cancellation and
            // completion.
            // This could cause the pipeline status to be incorrectly updated to RUNNING even when
            // there are
            // no more stages to run, resulting in the pipeline remaining in the RUNNING state
            // indefinitely.
            // Explicitly setting the status to CANCELED here takes care of such race conditions.
            execution.setStatus(CANCELED);
          }
          store(execution);
          c.hmset(pair.getLeft(), data);
          c.srem(allBufferedExecutionsKey(type), id);
        });
  }

  @Override
  public void pause(ExecutionType type, @Nonnull String id, String user) {
    ImmutablePair<String, RedisClientDelegate> pair = fetchKey(id);
    RedisClientDelegate delegate = pair.getRight();

    delegate.withCommandsClient(
        c -> {
          ExecutionStatus currentStatus = ExecutionStatus.valueOf(c.hget(pair.getLeft(), "status"));
          if (currentStatus != ExecutionStatus.RUNNING) {
            throw new UnpausablePipelineException(
                format(
                    "Unable to pause pipeline that is not RUNNING (executionId: %s, currentStatus: %s)",
                    id, currentStatus));
          }

          PausedDetails pausedDetails = new PausedDetails();
          pausedDetails.setPausedBy(user);
          pausedDetails.setPauseTime(currentTimeMillis());

          Map<String, String> data = new HashMap<>();
          try {
            data.put("paused", mapper.writeValueAsString(pausedDetails));
          } catch (JsonProcessingException e) {
            throw new ExecutionSerializationException("Failed converting pausedDetails to json", e);
          }
          data.put("status", ExecutionStatus.PAUSED.toString());
          c.hmset(pair.getLeft(), data);
          c.srem(allBufferedExecutionsKey(type), id);
        });
  }

  @Override
  public void resume(ExecutionType type, @Nonnull String id, String user) {
    resume(type, id, user, false);
  }

  @Override
  public void resume(
      ExecutionType type, @Nonnull String id, String user, boolean ignoreCurrentStatus) {
    ImmutablePair<String, RedisClientDelegate> pair = fetchKey(id);
    RedisClientDelegate delegate = pair.getRight();

    delegate.withCommandsClient(
        c -> {
          ExecutionStatus currentStatus = ExecutionStatus.valueOf(c.hget(pair.getLeft(), "status"));
          if (!ignoreCurrentStatus && currentStatus != ExecutionStatus.PAUSED) {
            throw new UnresumablePipelineException(
                format(
                    "Unable to resume pipeline that is not PAUSED (executionId: %s, currentStatus: %s)",
                    id, currentStatus));
          }

          try {
            PausedDetails pausedDetails =
                mapper.readValue(c.hget(pair.getLeft(), "paused"), PausedDetails.class);
            pausedDetails.setResumedBy(user);
            pausedDetails.setResumeTime(currentTimeMillis());

            Map<String, String> data = new HashMap<>();
            data.put("paused", mapper.writeValueAsString(pausedDetails));
            data.put("status", ExecutionStatus.RUNNING.toString());
            c.hmset(pair.getLeft(), data);
            c.srem(allBufferedExecutionsKey(type), id);
          } catch (IOException e) {
            throw new ExecutionSerializationException("Failed converting pausedDetails to json", e);
          }
        });
  }

  @Override
  public boolean isCanceled(ExecutionType type, @Nonnull String id) {
    ImmutablePair<String, RedisClientDelegate> pair = fetchKey(id);
    RedisClientDelegate delegate = pair.getRight();

    return delegate.withCommandsClient(
        c -> {
          return Boolean.valueOf(c.hget(pair.getLeft(), "canceled"));
        });
  }

  @Override
  public void updateStatus(
      ExecutionType type, @Nonnull String id, @Nonnull ExecutionStatus status) {
    ImmutablePair<String, RedisClientDelegate> pair = fetchKey(id);
    RedisClientDelegate delegate = pair.getRight();
    String key = pair.getLeft();

    delegate.withCommandsClient(
        c -> {
          Map<String, String> data = new HashMap<>();
          data.put("status", status.name());
          if (status == ExecutionStatus.RUNNING) {
            data.put("canceled", "false");
            data.put("startTime", String.valueOf(currentTimeMillis()));
          } else if (status.isComplete() && c.hget(key, "startTime") != null) {
            data.put("endTime", String.valueOf(currentTimeMillis()));
          }
          if (status == BUFFERED) {
            c.sadd(allBufferedExecutionsKey(type), id);
          } else {
            c.srem(allBufferedExecutionsKey(type), id);
          }
          c.hmset(key, data);
        });
  }

  @Override
  public @Nonnull PipelineExecution retrieve(@Nonnull ExecutionType type, @Nonnull String id) {
    RedisClientDelegate delegate = getRedisDelegate(type, id);
    return retrieveInternal(delegate, type, id);
  }

  @Override
  public @Nonnull Observable<PipelineExecution> retrieve(@Nonnull ExecutionType type) {
    List<Observable<PipelineExecution>> observables =
        allRedisDelegates().stream().map(d -> all(type, d)).collect(Collectors.toList());
    return Observable.merge(observables);
  }

  @Override
  public @Nonnull Observable<PipelineExecution> retrieve(
      @Nonnull ExecutionType type, @Nonnull ExecutionCriteria criteria) {
    List<Observable<PipelineExecution>> observables =
        allRedisDelegates().stream()
            .map(
                d -> {
                  Observable<PipelineExecution> observable = all(type, d);
                  if (!criteria.getStatuses().isEmpty()) {
                    observable =
                        observable.filter(
                            execution -> criteria.getStatuses().contains(execution.getStatus()));
                  }
                  if (criteria.getPageSize() > 0) {
                    observable = observable.limit(criteria.getPageSize());
                  }
                  return observable;
                })
            .collect(Collectors.toList());
    return Observable.merge(observables);
  }

  @Override
  public void delete(@Nonnull ExecutionType type, @Nonnull String id) {
    RedisClientDelegate delegate = getRedisDelegate(type, id);
    deleteInternal(delegate, type, id);
  }

  @Override
  public void delete(@Nonnull ExecutionType type, @Nonnull List<String> idsToDelete) {
    // NOTE: Not a great implementation, but this method right now is only used on
    // SqlExecutionRepository which has
    // a performant implementation
    idsToDelete.forEach(id -> delete(type, id));
  }

  @Override
  public @Nonnull Observable<PipelineExecution> retrievePipelinesForApplication(
      @Nonnull String application) {
    List<Observable<PipelineExecution>> observables =
        allRedisDelegates().stream()
            .map(d -> allForApplication(PIPELINE, application, d))
            .collect(Collectors.toList());
    return Observable.merge(observables);
  }

  @Override
  public @Nonnull Observable<PipelineExecution> retrievePipelinesForPipelineConfigId(
      @Nonnull String pipelineConfigId, @Nonnull ExecutionCriteria criteria) {
    /*
     * Fetch pipeline ids from the primary redis (and secondary if configured)
     */
    Map<RedisClientDelegate, List<String>> filteredPipelineIdsByDelegate = new HashMap<>();
    if (!criteria.getStatuses().isEmpty()) {
      allRedisDelegates()
          .forEach(
              d ->
                  d.withCommandsClient(
                      c -> {
                        List<String> pipelineKeys =
                            new ArrayList<>(
                                c.zrevrange(executionsByPipelineKey(pipelineConfigId), 0, -1));

                        if (pipelineKeys.isEmpty()) {
                          return;
                        }

                        Set<ExecutionStatus> allowedExecutionStatuses =
                            new HashSet<>(criteria.getStatuses());
                        List<ExecutionStatus> statuses =
                            fetchMultiExecutionStatus(
                                d,
                                pipelineKeys.stream()
                                    .map(key -> pipelineKey(key))
                                    .collect(Collectors.toList()));

                        AtomicInteger index = new AtomicInteger();
                        statuses.forEach(
                            s -> {
                              if (allowedExecutionStatuses.contains(s)) {
                                filteredPipelineIdsByDelegate
                                    .computeIfAbsent(d, p -> new ArrayList<>())
                                    .add(pipelineKeys.get(index.get()));
                              }
                              index.incrementAndGet();
                            });
                      }));
    }

    Func2<RedisClientDelegate, Iterable<String>, Func1<String, Iterable<String>>> fnBuilder =
        (RedisClientDelegate redisClientDelegate, Iterable<String> pipelineIds) ->
            (String key) ->
                !criteria.getStatuses().isEmpty()
                    ? pipelineIds
                    : redisClientDelegate.withCommandsClient(
                        p -> {
                          return p.zrevrange(key, 0, (criteria.getPageSize() - 1));
                        });

    /*
     * Construct an observable that will retrieve pipelines from the primary redis
     */
    List<String> currentPipelineIds =
        filteredPipelineIdsByDelegate.getOrDefault(redisClientDelegate, new ArrayList<>());
    currentPipelineIds =
        currentPipelineIds.subList(0, Math.min(criteria.getPageSize(), currentPipelineIds.size()));

    Observable<PipelineExecution> currentObservable =
        retrieveObservable(
            PIPELINE,
            executionsByPipelineKey(pipelineConfigId),
            fnBuilder.call(redisClientDelegate, currentPipelineIds),
            queryByAppScheduler,
            redisClientDelegate);

    if (previousRedisClientDelegate.isPresent()) {
      /*
       * If configured, construct an observable the will retrieve pipelines from the secondary redis
       */
      List<String> previousPipelineIds =
          filteredPipelineIdsByDelegate.getOrDefault(
              previousRedisClientDelegate.get(), new ArrayList<>());
      previousPipelineIds.removeAll(currentPipelineIds);
      previousPipelineIds =
          previousPipelineIds.subList(
              0, Math.min(criteria.getPageSize(), previousPipelineIds.size()));

      Observable<PipelineExecution> previousObservable =
          retrieveObservable(
              PIPELINE,
              executionsByPipelineKey(pipelineConfigId),
              fnBuilder.call(previousRedisClientDelegate.get(), previousPipelineIds),
              queryByAppScheduler,
              previousRedisClientDelegate.get());

      // merge primary + secondary observables
      return Observable.merge(currentObservable, previousObservable);
    }

    return currentObservable;
  }

  @Override
  public @Nonnull List<String> retrievePipelineConfigIdsForApplication(
      @Nonnull String application) {
    // TODO: not implemented yet - this method, at present, is primarily meant for the
    // SqlExecutionRepository
    //  implementation.
    return List.of();
  }

  @Override
  public @Nonnull List<String> retrieveAndFilterPipelineExecutionIdsForApplication(
      @Nonnull String application,
      @Nonnull List<String> pipelineConfigIds,
      @Nonnull ExecutionCriteria criteria) {
    // TODO: not implemented yet - this method, at present, is primarily meant for the
    // SqlExecutionRepository
    //  implementation.
    return List.of();
  }

  @Override
  public @Nonnull List<PipelineExecution> retrievePipelineExecutionDetailsForApplication(
      @Nonnull String application,
      @Nonnull List<String> pipelineExecutionIds,
      int queryTimeoutSeconds) {
    // TODO: not implemented yet - this method, at present, is primarily meant for the
    // SqlExecutionRepository
    //  implementation.
    return List.of();
  }

  @Override
  public @Nonnull List<String> retrievePipelineConfigIdsForApplicationWithCriteria(
      @Nonnull String application, @Nonnull ExecutionCriteria criteria) {
    // TODO: not implemented yet - this method, at present, is primarily meant for the
    // SqlExecutionRepository
    //  implementation.
    return List.of();
  }

  /*
   * There is no guarantee that the returned results will be sorted.
   * @param limit and the param @offset are only implemented in SqlExecutionRepository
   */
  @Override
  public @Nonnull List<PipelineExecution>
      retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
          @Nonnull List<String> pipelineConfigIds,
          long buildTimeStartBoundary,
          long buildTimeEndBoundary,
          ExecutionCriteria executionCriteria) {
    List<PipelineExecution> executions = new ArrayList<>();
    allRedisDelegates()
        .forEach(
            d -> {
              List<PipelineExecution> pipelines =
                  getPipelinesForPipelineConfigIdsBetweenBuildTimeBoundaryFromRedis(
                      d, pipelineConfigIds, buildTimeStartBoundary, buildTimeEndBoundary);
              executions.addAll(pipelines);
            });
    return executions.stream()
        .filter(
            it -> {
              if (executionCriteria.getStatuses().isEmpty()) {
                return true;
              } else {
                return executionCriteria.getStatuses().contains(it.getStatus());
              }
            })
        .collect(Collectors.toList());
  }

  @Override
  public List<PipelineExecution> retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
      @Nonnull List<String> pipelineConfigIds,
      long buildTimeStartBoundary,
      long buildTimeEndBoundary,
      ExecutionCriteria executionCriteria) {
    List<PipelineExecution> executions =
        retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
            pipelineConfigIds, buildTimeStartBoundary, buildTimeEndBoundary, executionCriteria);

    executions.sort(executionCriteria.getSortType());
    return executions;
  }

  @Override
  public @Nonnull Observable<PipelineExecution> retrieveOrchestrationsForApplication(
      @Nonnull String application, @Nonnull ExecutionCriteria criteria) {
    String allOrchestrationsKey = appKey(ORCHESTRATION, application);

    /*
     * Fetch orchestration ids from the primary redis (and secondary if configured)
     */
    Map<RedisClientDelegate, List<String>> filteredOrchestrationIdsByDelegate = new HashMap<>();
    if (!criteria.getStatuses().isEmpty()) {
      allRedisDelegates()
          .forEach(
              d ->
                  d.withCommandsClient(
                      c -> {
                        List<String> orchestrationKeys =
                            new ArrayList<>(c.smembers(allOrchestrationsKey));

                        if (orchestrationKeys.isEmpty()) {
                          return;
                        }

                        Set<ExecutionStatus> allowedExecutionStatuses =
                            new HashSet<>(criteria.getStatuses());
                        List<ExecutionStatus> statuses =
                            fetchMultiExecutionStatus(
                                d,
                                orchestrationKeys.stream()
                                    .map(key -> orchestrationKey(key))
                                    .collect(Collectors.toList()));

                        AtomicInteger index = new AtomicInteger();
                        statuses.forEach(
                            e -> {
                              if (allowedExecutionStatuses.contains(e)) {
                                filteredOrchestrationIdsByDelegate
                                    .computeIfAbsent(d, o -> new ArrayList<>())
                                    .add(orchestrationKeys.get(index.get()));
                              }
                              index.incrementAndGet();
                            });
                      }));
    }

    Func2<RedisClientDelegate, Iterable<String>, Func1<String, Iterable<String>>> fnBuilder =
        (RedisClientDelegate redisClientDelegate, Iterable<String> orchestrationIds) ->
            (String key) ->
                (redisClientDelegate.withCommandsClient(
                    c -> {
                      if (!criteria.getStatuses().isEmpty()) {
                        return orchestrationIds;
                      }
                      List<String> unfiltered = new ArrayList<>(c.smembers(key));
                      return unfiltered.subList(
                          0, Math.min(criteria.getPageSize(), unfiltered.size()));
                    }));

    /*
     * Construct an observable that will retrieve orchestrations frcm the primary redis
     */
    List<String> currentOrchestrationIds =
        filteredOrchestrationIdsByDelegate.getOrDefault(redisClientDelegate, new ArrayList<>());
    currentOrchestrationIds =
        currentOrchestrationIds.subList(
            0, Math.min(criteria.getPageSize(), currentOrchestrationIds.size()));

    Observable<PipelineExecution> currentObservable =
        retrieveObservable(
            ORCHESTRATION,
            allOrchestrationsKey,
            fnBuilder.call(redisClientDelegate, currentOrchestrationIds),
            queryByAppScheduler,
            redisClientDelegate);

    if (previousRedisClientDelegate.isPresent()) {
      /*
       * If configured, construct an observable the will retrieve orchestrations from the secondary redis
       */
      List<String> previousOrchestrationIds =
          filteredOrchestrationIdsByDelegate.getOrDefault(
              previousRedisClientDelegate.get(), new ArrayList<>());
      previousOrchestrationIds.removeAll(currentOrchestrationIds);
      previousOrchestrationIds =
          previousOrchestrationIds.subList(
              0, Math.min(criteria.getPageSize(), previousOrchestrationIds.size()));

      Observable<PipelineExecution> previousObservable =
          retrieveObservable(
              ORCHESTRATION,
              allOrchestrationsKey,
              fnBuilder.call(previousRedisClientDelegate.get(), previousOrchestrationIds),
              queryByAppScheduler,
              previousRedisClientDelegate.get());

      // merge primary + secondary observables
      return Observable.merge(currentObservable, previousObservable);
    }

    return currentObservable;
  }

  @Nonnull
  @Override
  public List<PipelineExecution> retrieveOrchestrationsForApplication(
      @Nonnull String application,
      @Nonnull ExecutionCriteria criteria,
      ExecutionComparator sorter) {
    List<PipelineExecution> executions =
        retrieveOrchestrationsForApplication(application, criteria)
            .filter(
                (orchestration) -> {
                  if (criteria.getStartTimeCutoff() != null) {
                    long startTime = Optional.ofNullable(orchestration.getStartTime()).orElse(0L);
                    return startTime == 0
                        || (startTime > criteria.getStartTimeCutoff().toEpochMilli());
                  }
                  return true;
                })
            .subscribeOn(Schedulers.io())
            .toList()
            .toBlocking()
            .single();

    if (sorter != null) {
      executions.sort(sorter);
    }

    return executions.subList(0, Math.min(executions.size(), criteria.getPageSize()));
  }

  @Nonnull
  @Override
  public PipelineExecution retrieveByCorrelationId(
      @Nonnull ExecutionType executionType, @Nonnull String correlationId)
      throws ExecutionNotFoundException {
    if (executionType == PIPELINE) {
      return retrievePipelineForCorrelationId(correlationId);
    }
    return retrieveOrchestrationForCorrelationId(correlationId);
  }

  @Override
  public @Nonnull PipelineExecution retrieveOrchestrationForCorrelationId(
      @Nonnull String correlationId) throws ExecutionNotFoundException {
    String key = format("correlation:%s", correlationId);
    return getRedisDelegate(key)
        .withCommandsClient(
            correlationRedis -> {
              String orchestrationId = correlationRedis.get(key);

              if (orchestrationId != null) {
                PipelineExecution orchestration =
                    retrieveInternal(
                        getRedisDelegate(orchestrationKey(orchestrationId)),
                        ORCHESTRATION,
                        orchestrationId);
                if (!orchestration.getStatus().isComplete()) {
                  return orchestration;
                }
                correlationRedis.del(key);
              }
              throw new ExecutionNotFoundException(
                  format("No Orchestration found for correlation ID %s", correlationId));
            });
  }

  @Nonnull
  @Override
  public PipelineExecution retrievePipelineForCorrelationId(@Nonnull String correlationId)
      throws ExecutionNotFoundException {
    String key = format("correlation:%s", correlationId);
    return getRedisDelegate(key)
        .withCommandsClient(
            correlationRedis -> {
              String pipelineId = correlationRedis.get(key);

              if (pipelineId != null) {
                PipelineExecution pipeline =
                    retrieveInternal(
                        getRedisDelegate(pipelineKey(pipelineId)), PIPELINE, pipelineId);
                if (!pipeline.getStatus().isComplete()) {
                  return pipeline;
                }
                correlationRedis.del(key);
              }
              throw new ExecutionNotFoundException(
                  format("No Pipeline found for correlation ID %s", correlationId));
            });
  }

  @Override
  public @Nonnull List<PipelineExecution> retrieveBufferedExecutions() {
    List<Observable<PipelineExecution>> observables =
        allRedisDelegates().stream()
            .map(
                d ->
                    Arrays.asList(
                        retrieveObservable(
                            PIPELINE, allBufferedExecutionsKey(PIPELINE), queryAllScheduler, d),
                        retrieveObservable(
                            ORCHESTRATION,
                            allBufferedExecutionsKey(ORCHESTRATION),
                            queryAllScheduler,
                            d)))
            .flatMap(List::stream)
            .collect(Collectors.toList());

    return Observable.merge(observables)
        .filter(e -> e.getStatus() == BUFFERED)
        .toList()
        .toBlocking()
        .single();
  }

  @Nonnull
  @Override
  public List<String> retrieveAllApplicationNames(@Nullable ExecutionType type) {
    return retrieveAllApplicationNames(type, 0);
  }

  @Nonnull
  @Override
  public List<String> retrieveAllApplicationNames(@Nullable ExecutionType type, int minExecutions) {
    return redisClientDelegate.withMultiClient(
        mc -> {
          ScanParams scanParams = new ScanParams().match(executionKeyPattern(type)).count(2000);
          String cursor = "0";

          Map<String, Long> apps = new HashMap<>();
          while (true) {
            String finalCursor = cursor;
            ScanResult<String> chunk = mc.scan(finalCursor, scanParams);

            if (redisClientDelegate.supportsMultiKeyPipelines()) {
              List<ImmutablePair<String, Response<Long>>> pipelineResults = new ArrayList<>();
              redisClientDelegate.withMultiKeyPipeline(
                  p -> {
                    chunk
                        .getResult()
                        .forEach(
                            id -> {
                              String app = id.split(":")[2];
                              pipelineResults.add(new ImmutablePair<>(app, p.scard(id)));
                            });
                    p.sync();
                  });

              pipelineResults.forEach(
                  p ->
                      apps.compute(
                          p.left,
                          (app, numExecutions) ->
                              Optional.ofNullable(numExecutions).orElse(0L) + p.right.get()));
            } else {
              redisClientDelegate.withCommandsClient(
                  cc -> {
                    chunk
                        .getResult()
                        .forEach(
                            id -> {
                              String[] parts = id.split(":");
                              String app = parts[2];
                              long cardinality = cc.scard(id);
                              apps.compute(
                                  app,
                                  (appKey, numExecutions) ->
                                      Optional.ofNullable(numExecutions).orElse(0L) + cardinality);
                            });
                  });
            }

            cursor = chunk.getCursor();
            if (cursor.equals("0")) {
              break;
            }
          }

          return apps.entrySet().stream()
              .filter(e -> e.getValue().intValue() >= minExecutions)
              .map(Map.Entry::getKey)
              .collect(Collectors.toList());
        });
  }

  @Override
  public boolean hasExecution(@Nonnull ExecutionType type, @Nonnull String id) {
    return redisClientDelegate.withCommandsClient(
        c -> {
          return c.exists(executionKey(type, id));
        });
  }

  @Override
  public List<String> retrieveAllExecutionIds(@Nonnull ExecutionType type) {
    return new ArrayList<>(
        redisClientDelegate.withCommandsClient(
            c -> {
              return c.smembers(alljobsKey(type));
            }));
  }

  private Map<String, String> buildExecutionMapFromRedisResponse(List<String> entries) {
    if (entries.size() % 2 != 0) {
      throw new RuntimeException(
          "Failed to convert Redis response to map because the number of entries is not even");
    }
    Map<String, String> map = new HashMap<>();
    String nextKey = null;
    for (int i = 0; i < entries.size(); i++) {
      if (i % 2 == 0) {
        nextKey = entries.get(i);
      } else {
        map.put(nextKey, entries.get(i));
      }
    }
    return map;
  }

  protected PipelineExecution buildExecution(
      @Nonnull PipelineExecution execution,
      @Nonnull Map<String, String> map,
      List<String> stageIds) {
    Id serializationErrorId =
        registry
            .createId("executions.deserialization.error")
            .withTag("executionType", execution.getType().toString())
            .withTag("application", execution.getApplication());

    try {
      execution.setCanceled(Boolean.parseBoolean(map.get("canceled")));
      execution.setCanceledBy(map.get("canceledBy"));
      execution.setCancellationReason(map.get("cancellationReason"));
      execution.setLimitConcurrent(Boolean.parseBoolean(map.get("limitConcurrent")));
      execution.setMaxConcurrentExecutions(
          (map.containsKey("maxConcurrentExecutions"))
              ? Integer.parseInt(map.get("maxConcurrentExecutions"))
              : 0);
      execution.setBuildTime(NumberUtils.createLong(map.get("buildTime")));
      execution.setStartTime(NumberUtils.createLong(map.get("startTime")));
      if (map.get("startTimeExpiry") != null) {
        execution.setStartTimeExpiry(Long.valueOf(map.get("startTimeExpiry")));
      }
      execution.setEndTime(NumberUtils.createLong(map.get("endTime")));
      if (map.get("status") != null) {
        execution.setStatus(ExecutionStatus.valueOf(map.get("status")));
      }
      execution.setAuthentication(
          mapper.readValue(
              map.get("authentication"), PipelineExecution.AuthenticationDetails.class));
      if (map.get("paused") != null) {
        execution.setPaused(mapper.readValue(map.get("paused"), PausedDetails.class));
      }
      execution.setKeepWaitingPipelines(Boolean.parseBoolean(map.get("keepWaitingPipelines")));
      if (map.get("spelEvaluator") != null) {
        execution.setSpelEvaluator(map.get("spelEvaluator"));
      }
      execution.setOrigin(map.get("origin"));
      if (map.get("source") != null) {
        execution.setSource(
            mapper.readValue(map.get("source"), PipelineExecution.PipelineSource.class));
      }
      execution.setTrigger(
          map.get("trigger") != null
              ? mapper.readValue(map.get("trigger"), Trigger.class)
              : NO_TRIGGER);
      if (map.get("systemNotifications") != null) {
        ((PipelineExecutionImpl) execution)
            .getSystemNotifications()
            .addAll(mapper.readValue(map.get("systemNotifications"), LIST_OF_SYSTEM_NOTIFICATIONS));
      }
    } catch (Exception e) {
      registry.counter(serializationErrorId).increment();
      throw new ExecutionSerializationException(
          String.format("Failed serializing execution json, id: %s", execution.getId()), e);
    }

    List<StageExecution> stages = new ArrayList<>();
    stageIds.forEach(
        stageId -> {
          String prefix = format("stage.%s.", stageId);
          StageExecution stage = new StageExecutionImpl();
          try {
            stage.setId(stageId);
            stage.setRefId(map.get(prefix + "refId"));
            stage.setType(map.get(prefix + "type"));
            stage.setName(map.get(prefix + "name"));
            stage.setStartTime(NumberUtils.createLong(map.get(prefix + "startTime")));
            stage.setEndTime(NumberUtils.createLong(map.get(prefix + "endTime")));
            stage.setStatus(ExecutionStatus.valueOf(map.get(prefix + "status")));
            if (map.get(prefix + "startTimeExpiry") != null) {
              stage.setStartTimeExpiry(Long.valueOf(map.get(prefix + "startTimeExpiry")));
            }
            if (map.get(prefix + "syntheticStageOwner") != null) {
              stage.setSyntheticStageOwner(
                  SyntheticStageOwner.valueOf(map.get(prefix + "syntheticStageOwner")));
            }
            stage.setParentStageId(map.get(prefix + "parentStageId"));

            String requisiteStageRefIds = map.get(prefix + "requisiteStageRefIds");
            if (StringUtils.isNotEmpty(requisiteStageRefIds)) {
              stage.setRequisiteStageRefIds(Arrays.asList(requisiteStageRefIds.split(",")));
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
            if (map.get(prefix + "lastModified") != null) {
              stage.setLastModified(
                  mapper.readValue(
                      map.get(prefix + "lastModified"), StageExecution.LastModifiedDetails.class));
            }
            stage.setExecution(execution);
            stages.add(stage);
          } catch (IOException e) {
            registry.counter(serializationErrorId).increment();
            throw new StageSerializationException(
                format(
                    "Failed serializing stage json, executionId: %s, stageId: %s",
                    execution.getId(), stageId),
                e);
          }
        });

    ExecutionRepositoryUtil.sortStagesByReference(execution, stages);

    if (execution.getType() == PIPELINE) {
      execution.setName(map.get("name"));
      execution.setPipelineConfigId(map.get("pipelineConfigId"));
      try {
        if (map.get("notifications") != null) {
          execution
              .getNotifications()
              .addAll(mapper.readValue(map.get("notifications"), List.class));
        }
        if (map.get("initialConfig") != null) {
          ((PipelineExecutionImpl) execution)
              .getInitialConfig()
              .putAll(mapper.readValue(map.get("initialConfig"), Map.class));
        }
      } catch (IOException e) {
        registry.counter(serializationErrorId).increment();
        throw new ExecutionSerializationException("Failed serializing execution json", e);
      }
    } else if (execution.getType() == ORCHESTRATION) {
      execution.setDescription(map.get("description"));
    }
    return execution;
  }

  protected Map<String, String> serializeExecution(@Nonnull PipelineExecution execution) {
    Map<String, String> map = new HashMap<>();
    try {
      map.put("application", execution.getApplication());
      map.put("canceled", String.valueOf(execution.isCanceled()));
      map.put("limitConcurrent", String.valueOf(execution.isLimitConcurrent()));
      map.put("maxConcurrentExecutions", String.valueOf(execution.getMaxConcurrentExecutions()));
      map.put(
          "buildTime",
          String.valueOf(execution.getBuildTime() != null ? execution.getBuildTime() : 0L));
      map.put(
          "startTime",
          execution.getStartTime() != null ? execution.getStartTime().toString() : null);
      map.put("endTime", execution.getEndTime() != null ? execution.getEndTime().toString() : null);
      map.put(
          "startTimeExpiry",
          execution.getStartTimeExpiry() != null
              ? String.valueOf(execution.getStartTimeExpiry())
              : null);
      map.put("status", execution.getStatus().name());
      map.put("authentication", mapper.writeValueAsString(execution.getAuthentication()));
      map.put("paused", mapper.writeValueAsString(execution.getPaused()));
      map.put("keepWaitingPipelines", String.valueOf(execution.isKeepWaitingPipelines()));
      map.put("spelEvaluator", execution.getSpelEvaluator());
      map.put("origin", execution.getOrigin());
      map.put("source", mapper.writeValueAsString(execution.getSource()));
      map.put("trigger", mapper.writeValueAsString(execution.getTrigger()));
      map.put(
          "systemNotifications",
          mapper.writeValueAsString(((PipelineExecutionImpl) execution).getSystemNotifications()));
    } catch (JsonProcessingException e) {
      throw new ExecutionSerializationException("Failed serializing execution", e);
    }

    execution.getStages().forEach(s -> map.putAll(serializeStage(s)));

    if (execution.getType() == PIPELINE) {
      try {
        map.put("name", execution.getName());
        map.put("pipelineConfigId", execution.getPipelineConfigId());
        map.put("notifications", mapper.writeValueAsString(execution.getNotifications()));
        map.put(
            "initialConfig",
            mapper.writeValueAsString(((PipelineExecutionImpl) execution).getInitialConfig()));
      } catch (JsonProcessingException e) {
        throw new ExecutionSerializationException("Failed serializing execution", e);
      }
    } else if (execution.getType() == ORCHESTRATION) {
      map.put("description", execution.getDescription());
    }
    return map;
  }

  protected Map<String, String> serializeStage(StageExecution stage) {
    String prefix = format("stage.%s.", stage.getId());
    Map<String, String> map = new HashMap<>();
    map.put(prefix + "refId", stage.getRefId());
    map.put(prefix + "type", stage.getType());
    map.put(prefix + "name", stage.getName());
    map.put(
        prefix + "startTime",
        stage.getStartTime() != null ? stage.getStartTime().toString() : null);
    map.put(prefix + "endTime", stage.getEndTime() != null ? stage.getEndTime().toString() : null);
    map.put(
        prefix + "startTimeExpiry",
        stage.getStartTimeExpiry() != null ? String.valueOf(stage.getStartTimeExpiry()) : null);
    map.put(prefix + "status", stage.getStatus().name());
    map.put(
        prefix + "syntheticStageOwner",
        stage.getSyntheticStageOwner() != null ? stage.getSyntheticStageOwner().name() : null);
    map.put(prefix + "parentStageId", stage.getParentStageId());
    if (!stage.getRequisiteStageRefIds().isEmpty()) {
      map.put(
          prefix + "requisiteStageRefIds",
          stage.getRequisiteStageRefIds().stream().collect(Collectors.joining(",")));
    }
    map.put(
        prefix + "scheduledTime",
        (stage.getScheduledTime() != null ? stage.getScheduledTime().toString() : null));
    try {
      map.put(prefix + "context", mapper.writeValueAsString(stage.getContext()));
      map.put(prefix + "outputs", mapper.writeValueAsString(stage.getOutputs()));
      map.put(prefix + "tasks", mapper.writeValueAsString(stage.getTasks()));
      map.put(
          prefix + "lastModified",
          (stage.getLastModified() != null
              ? mapper.writeValueAsString(stage.getLastModified())
              : null));
    } catch (JsonProcessingException e) {
      throw new StageSerializationException(
          format(
              "Failed converting stage to json, executionId: %s, stageId: %s",
              stage.getExecution().getId(), stage.getId()),
          e);
    }
    return map;
  }

  // Support for reading pre-stageIndex executions
  protected List<String> extractStages(Map<String, String> map) {
    Set<String> stageIds = new HashSet<>();
    Pattern pattern = Pattern.compile("^stage\\.([-\\w]+)\\.");

    map.keySet()
        .forEach(
            k -> {
              Matcher matcher = pattern.matcher(k);
              if (matcher.find()) {
                stageIds.add(matcher.group(1));
              }
            });
    return new ArrayList<>(stageIds);
  }

  private void deleteInternal(RedisClientDelegate delegate, ExecutionType type, String id) {
    delegate.withCommandsClient(
        c -> {
          String key = executionKey(type, id);
          try {
            String application = c.hget(key, "application");
            String appKey = appKey(type, application);
            c.srem(appKey, id);
            c.srem(allBufferedExecutionsKey(type), id);

            if (type == PIPELINE) {
              String pipelineConfigId = c.hget(key, "pipelineConfigId");
              c.zrem(executionsByPipelineKey(pipelineConfigId), id);
            }
          } catch (ExecutionNotFoundException ignored) {
            // do nothing
          } finally {
            c.del(key);
            c.del(key + ":stageIndex");
            c.srem(alljobsKey(type), id);
          }
        });
  }

  /**
   * Unpacks the following redis script into several roundtrips:
   *
   * <p>local pipelineConfigToExecutionsKey = 'pipeline:executions:' .. pipelineConfigId local ids =
   * redis.call('ZRANGEBYSCORE', pipelineConfigToExecutionsKey, ARGV[1], ARGV[2]) for k,id in
   * pairs(ids) do table.insert(executions, id) local executionKey = 'pipeline:' .. id local
   * execution = redis.call('HGETALL', executionKey) table.insert(executions, execution) local
   * stageIdsKey = executionKey .. ':stageIndex' local stageIds = redis.call('LRANGE', stageIdsKey,
   * 0, -1) table.insert(executions, stageIds) end
   *
   * <p>The script is intended to build a list of executions for a pipeline config id in a given
   * time boundary.
   *
   * @param delegate Redis delegate
   * @param pipelineConfigId Pipeline config Id we are looking up executions for
   * @param buildTimeStartBoundary
   * @param buildTimeEndBoundary
   * @return Stream of executions for pipelineConfigId between the time boundaries.
   */
  private Stream<PipelineExecution> getExecutionForPipelineConfigId(
      RedisClientDelegate delegate,
      String pipelineConfigId,
      Long buildTimeStartBoundary,
      Long buildTimeEndBoundary) {
    String executionsKey = executionsByPipelineKey(pipelineConfigId);
    Set<String> executionIds =
        delegate.withCommandsClient(
            c -> {
              return c.zrangeByScore(executionsKey, buildTimeStartBoundary, buildTimeEndBoundary);
            });

    return executionIds.stream()
        .map(
            executionId -> {
              String executionKey = pipelineKey(executionId);
              Map<String, String> executionMap =
                  delegate.withCommandsClient(
                      c -> {
                        return c.hgetAll(executionKey);
                      });
              String stageIdsKey = String.format("%s:stageIndex", executionKey);
              List<String> stageIds =
                  delegate.withCommandsClient(
                      c -> {
                        return c.lrange(stageIdsKey, 0, -1);
                      });
              PipelineExecution execution =
                  new PipelineExecutionImpl(PIPELINE, executionId, executionMap.get("application"));
              return buildExecution(execution, executionMap, stageIds);
            });
  }

  private List<PipelineExecution> getPipelinesForPipelineConfigIdsBetweenBuildTimeBoundaryFromRedis(
      RedisClientDelegate redisClientDelegate,
      List<String> pipelineConfigIds,
      long buildTimeStartBoundary,
      long buildTimeEndBoundary) {
    return pipelineConfigIds.stream()
        .flatMap(
            pipelineConfigId ->
                getExecutionForPipelineConfigId(
                    redisClientDelegate,
                    pipelineConfigId,
                    buildTimeStartBoundary,
                    buildTimeEndBoundary))
        .collect(Collectors.toList());
  }

  protected Observable<PipelineExecution> all(
      ExecutionType type, RedisClientDelegate redisClientDelegate) {
    return retrieveObservable(type, alljobsKey(type), queryAllScheduler, redisClientDelegate);
  }

  protected Observable<PipelineExecution> allForApplication(
      ExecutionType type, String application, RedisClientDelegate redisClientDelegate) {
    return retrieveObservable(
        type, appKey(type, application), queryByAppScheduler, redisClientDelegate);
  }

  protected Observable<PipelineExecution> retrieveObservable(
      ExecutionType type,
      String lookupKey,
      Scheduler scheduler,
      RedisClientDelegate redisClientDelegate) {

    Func0<Func1<String, Iterable<String>>> fnBuilder =
        () ->
            (String key) ->
                redisClientDelegate.withCommandsClient(
                    c -> {
                      return c.smembers(key);
                    });

    return retrieveObservable(type, lookupKey, fnBuilder.call(), scheduler, redisClientDelegate);
  }

  @SuppressWarnings("unchecked")
  protected Observable<PipelineExecution> retrieveObservable(
      ExecutionType type,
      String lookupKey,
      Func1<String, Iterable<String>> lookupKeyFetcher,
      Scheduler scheduler,
      RedisClientDelegate redisClientDelegate) {
    return Observable.just(lookupKey)
        .flatMapIterable(lookupKeyFetcher)
        .buffer(chunkSize)
        .flatMap(
            (Collection<String> ids) ->
                Observable.from(ids)
                    .flatMap(
                        (String executionId) -> {
                          try {
                            return Observable.just(
                                retrieveInternal(redisClientDelegate, type, executionId));
                          } catch (ExecutionNotFoundException ignored) {
                            log.info(
                                "Execution ({}) does not exist", value("executionId", executionId));
                            redisClientDelegate.withCommandsClient(
                                c -> {
                                  if (c.type(lookupKey).equals("zset")) {
                                    c.zrem(lookupKey, executionId);
                                  } else {
                                    c.srem(lookupKey, executionId);
                                  }
                                });
                          } catch (Exception e) {
                            log.error(
                                "Failed to retrieve execution '{}'",
                                value("executionId", executionId),
                                e);
                          }
                          return Observable.empty();
                        }))
        .subscribeOn(scheduler);
  }

  protected ImmutablePair<String, RedisClientDelegate> fetchKey(String id) {
    ImmutablePair<String, RedisClientDelegate> pair =
        redisClientDelegate.withCommandsClient(
            c -> {
              if (c.exists(pipelineKey(id))) {
                return ImmutablePair.of(pipelineKey(id), redisClientDelegate);
              } else if (c.exists(orchestrationKey(id))) {
                return ImmutablePair.of(orchestrationKey(id), redisClientDelegate);
              }
              return ImmutablePair.nullPair();
            });

    if (pair.getLeft() == null && previousRedisClientDelegate.isPresent()) {
      RedisClientDelegate delegate = previousRedisClientDelegate.get();
      pair =
          delegate.withCommandsClient(
              c -> {
                if (c.exists(pipelineKey(id))) {
                  return ImmutablePair.of(pipelineKey(id), delegate);
                } else if (c.exists(orchestrationKey(id))) {
                  return ImmutablePair.of(orchestrationKey(id), delegate);
                }
                return null;
              });
    }

    if (pair.getLeft() == null) {
      throw new ExecutionNotFoundException("No execution found with id " + id);
    }
    return pair;
  }

  protected static String alljobsKey(ExecutionType type) {
    return format("allJobs:%s", type);
  }

  protected static String appKey(ExecutionType type, String app) {
    return format("%s:app:%s", type, app);
  }

  protected static String executionsByPipelineKey(String pipelineConfigId) {
    String id = pipelineConfigId != null ? pipelineConfigId : "---";
    return format("pipeline:executions:%s", id);
  }

  protected static String allBufferedExecutionsKey(ExecutionType type) {
    if (bufferedPrefix == null || bufferedPrefix.isEmpty()) {
      return format("buffered:%s", type);
    } else {
      return format("%s:buffered:%s", bufferedPrefix, type);
    }
  }

  protected static String executionKeyPattern(@Nullable ExecutionType type) {
    final String all = "*:app:*";
    if (type == null) {
      return all;
    }
    switch (type) {
      case PIPELINE:
        return "pipeline:app:*";
      case ORCHESTRATION:
        return "orchestration:app:*";
      default:
        return all;
    }
  }

  private void storeExecutionInternal(RedisClientDelegate delegate, PipelineExecution execution) {
    String key = executionKey(execution);
    String indexKey = format("%s:stageIndex", key);
    Map<String, String> map = serializeExecution(execution);

    delegate.withCommandsClient(
        c -> {
          c.sadd(alljobsKey(execution.getType()), execution.getId());
          c.sadd(appKey(execution.getType(), execution.getApplication()), execution.getId());

          if (execution.getStatus() == BUFFERED) {
            c.sadd(allBufferedExecutionsKey(execution.getType()), execution.getId());
          } else {
            c.srem(allBufferedExecutionsKey(execution.getType()), execution.getId());
          }

          delegate.withTransaction(
              tx -> {
                tx.hdel(key, "config");
                tx.hmset(key, filterValues(map, Objects::nonNull));
                if (!execution.getStages().isEmpty()) {
                  tx.del(indexKey);
                  tx.rpush(
                      indexKey,
                      execution.getStages().stream()
                          .map(StageExecution::getId)
                          .toArray(String[]::new));
                }
                tx.exec();
              });

          if (execution.getTrigger().getCorrelationId() != null) {
            c.set(
                format("correlation:%s", execution.getTrigger().getCorrelationId()),
                execution.getId());
          }
        });
  }

  private void storeStageInternal(
      RedisClientDelegate delegate, StageExecution stage, Boolean updateIndex) {
    String key = executionKey(stage);
    String indexKey = format("%s:stageIndex", key);

    Map<String, String> serializedStage = serializeStage(stage);
    List<String> keysToRemove =
        serializedStage.entrySet().stream()
            .filter(e -> e.getValue() == null)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

    serializedStage.values().removeIf(Objects::isNull);

    delegate.withTransaction(
        tx -> {
          tx.hmset(key, serializedStage);

          if (!keysToRemove.isEmpty()) {
            tx.hdel(key, keysToRemove.toArray(new String[0]));
          }
          if (updateIndex) {
            ListPosition pos = stage.getSyntheticStageOwner() == STAGE_BEFORE ? BEFORE : AFTER;
            tx.linsert(indexKey, pos, stage.getParentStageId(), stage.getId());
          }
          tx.exec();
        });
  }

  protected PipelineExecution retrieveInternal(
      RedisClientDelegate delegate, ExecutionType type, String id)
      throws ExecutionNotFoundException {
    String key = executionKey(type, id);
    String indexKey = format("%s:stageIndex", key);

    boolean exists =
        delegate.withCommandsClient(
            c -> {
              return c.exists(key);
            });
    if (!exists) {
      throw new ExecutionNotFoundException("No " + type + " found for " + id);
    }

    final Map<String, String> map = new HashMap<>();
    final List<String> stageIds = new ArrayList<>();

    delegate.withTransaction(
        tx -> {
          Response<Map<String, String>> execResponse = tx.hgetAll(key);
          Response<List<String>> indexResponse = tx.lrange(indexKey, 0, -1);
          tx.exec();

          map.putAll(execResponse.get());

          if (!indexResponse.get().isEmpty()) {
            stageIds.addAll(indexResponse.get());
          } else {
            stageIds.addAll(extractStages(map));
          }
        });

    PipelineExecution execution = new PipelineExecutionImpl(type, id, map.get("application"));
    return buildExecution(execution, map, stageIds);
  }

  protected List<ExecutionStatus> fetchMultiExecutionStatus(
      RedisClientDelegate redisClientDelegate, List<String> keys) {
    return redisClientDelegate.withMultiKeyPipeline(
        p -> {
          List<Response<String>> responses =
              keys.stream().map(k -> p.hget(k, "status")).collect(Collectors.toList());
          p.sync();
          return responses.stream()
              .map(Response::get)
              .filter(
                  Objects
                      ::nonNull) // apparently we have some null statuses even though that makes no
              // sense
              .map(ExecutionStatus::valueOf)
              .collect(Collectors.toList());
        });
  }

  protected Collection<RedisClientDelegate> allRedisDelegates() {
    Collection<RedisClientDelegate> delegates = new ArrayList<>();
    delegates.add(redisClientDelegate);
    previousRedisClientDelegate.ifPresent(delegates::add);
    return delegates;
  }

  protected String executionKey(PipelineExecution execution) {
    return format("%s:%s", execution.getType(), execution.getId());
  }

  private String executionKey(StageExecution stage) {
    return format("%s:%s", stage.getExecution().getType(), stage.getExecution().getId());
  }

  private String executionKey(ExecutionType type, String id) {
    return format("%s:%s", type, id);
  }

  private String pipelineKey(String id) {
    return format("%s:%s", PIPELINE, id);
  }

  private String orchestrationKey(String id) {
    return format("%s:%s", ORCHESTRATION, id);
  }

  private RedisClientDelegate getRedisDelegate(PipelineExecution execution) {
    return getRedisDelegate(execution.getType(), execution.getId());
  }

  private RedisClientDelegate getRedisDelegate(StageExecution stage) {
    return getRedisDelegate(stage.getExecution().getType(), stage.getExecution().getId());
  }

  private RedisClientDelegate getRedisDelegate(ExecutionType type, String id) {
    if (StringUtils.isBlank(id) || !previousRedisClientDelegate.isPresent()) {
      return redisClientDelegate;
    }

    RedisClientDelegate delegate =
        redisClientDelegate.withCommandsClient(
            c -> {
              if (c.exists(executionKey(type, id))) {
                return redisClientDelegate;
              } else {
                return null;
              }
            });

    if (delegate == null) {
      delegate =
          previousRedisClientDelegate
              .get()
              .withCommandsClient(
                  c -> {
                    if (c.exists(executionKey(type, id))) {
                      return previousRedisClientDelegate.get();
                    } else {
                      return null;
                    }
                  });
    }

    return (delegate == null) ? redisClientDelegate : delegate;
  }

  private RedisClientDelegate getRedisDelegate(String key) {
    if (Strings.isNullOrEmpty(key) || !previousRedisClientDelegate.isPresent()) {
      return redisClientDelegate;
    }

    RedisClientDelegate delegate =
        redisClientDelegate.withCommandsClient(
            c -> {
              if (c.exists(key)) {
                return redisClientDelegate;
              } else {
                return null;
              }
            });

    if (delegate == null) {
      delegate =
          previousRedisClientDelegate
              .get()
              .withCommandsClient(
                  c -> {
                    if (c.exists(key)) {
                      return previousRedisClientDelegate.get();
                    } else {
                      return null;
                    }
                  });
    }

    return (delegate == null) ? redisClientDelegate : delegate;
  }

  private static class ImmutablePair<L, R> {

    private final L left;
    private final R right;

    public ImmutablePair(L left, R right) {
      this.left = left;
      this.right = right;
    }

    public static <T, U> ImmutablePair<T, U> of(T left, U right) {
      return new ImmutablePair<>(left, right);
    }

    public static <T, U> ImmutablePair<T, U> nullPair() {
      return new ImmutablePair<>(null, null);
    }

    public L getLeft() {
      return left;
    }

    public R getRight() {
      return right;
    }
  }
}
