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

import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.RedisClientSelector;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.BinaryClient;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;
import rx.Scheduler;
import rx.schedulers.Schedulers;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.google.common.collect.Maps.filterValues;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE;
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static redis.clients.jedis.BinaryClient.LIST_POSITION.AFTER;
import static redis.clients.jedis.BinaryClient.LIST_POSITION.BEFORE;

@Component
public class JedisExecutionRepository extends AbstractRedisExecutionRepository {

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Autowired
  public JedisExecutionRepository(
    RedisClientSelector redisClientSelector,
    @Qualifier("queryAllScheduler") Scheduler queryAllScheduler,
    @Qualifier("queryByAppScheduler") Scheduler queryByAppScheduler,
    @Value("${chunkSize.executionRepository:75}") Integer threadPoolChunkSize
  ) {
    super(redisClientSelector, queryAllScheduler, queryByAppScheduler, threadPoolChunkSize);
  }

  public JedisExecutionRepository(
    RedisClientSelector redisClientSelector,
    Integer threadPoolSize,
    Integer threadPoolChunkSize
  ) {
    super(
      redisClientSelector,
      Schedulers.from(Executors.newFixedThreadPool(10)),
      Schedulers.from(Executors.newFixedThreadPool(threadPoolSize)),
      threadPoolChunkSize
    );
  }

  @Override
  public void store(@Nonnull Execution execution) {
    getRedisDelegateForId(executionKey(execution)).withTransaction(tx -> {
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
    getRedisDelegateForId(executionKey(stage)).withTransaction(tx -> {
      storeStageInternal(tx, stage);
      tx.exec();
    });
  }

  @Override
  public void removeStage(@Nonnull Execution execution, @Nonnull String stageId) {
    String key = executionKey(execution);
    String indexKey = format("%s:stageIndex", key);
    RedisClientDelegate delegate = getRedisDelegateForId(key);

    delegate.withCommandsClient(c -> {
      List<String> stageKeys = c.hkeys(key).stream()
        .filter(k -> k.startsWith("stage." + stageId))
        .collect(Collectors.toList());

      delegate.withTransaction(tx -> {
        tx.lrem(indexKey, 0, stageId);
        tx.hdel(key, stageKeys.toArray(new String[0]));
        tx.exec();
      });
    });
  }

  @Override
  public void addStage(@Nonnull Stage stage) {
    if (stage.getSyntheticStageOwner() == null || stage.getParentStageId() == null) {
      throw new IllegalArgumentException("Only synthetic stages can be inserted ad-hoc");
    }

    String key = executionKey(stage);
    String indexKey = format("%s:stageIndex", key);
    getRedisDelegateForId(key).withTransaction(tx -> {
      storeStageInternal(tx, stage);
      BinaryClient.LIST_POSITION pos = stage.getSyntheticStageOwner() == STAGE_BEFORE ? BEFORE : AFTER;
      tx.linsert(indexKey, pos, stage.getParentStageId(), stage.getId());
      tx.exec();
    });
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
          getRedisDelegateForId(fetchKey(orchestrationId)),
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

    String key = executionKey(execution);
    String indexKey = format("%s:stageIndex", key);

    Map<String, String> map = serializeExecution(execution);
    // TODO: remove this and only use the list
    if (!execution.getStages().isEmpty()) {
      tx.del(indexKey);
      tx.rpush(indexKey, execution.getStages().stream()
        .map(Stage::getId)
        .collect(Collectors.toList())
        .toArray(new String[0])
      );
    }
    if (execution.getTrigger().getCorrelationId() != null) {
      tx.set(
        format("correlation:%s", execution.getTrigger().getCorrelationId()),
        execution.getId()
      );
    }

    tx.hdel(key, "config");
    tx.hmset(key, filterValues(map, Objects::nonNull));
  }

  private void storeStageInternal(Transaction tx, Stage stage) {
    String key = executionKey(stage);

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

  @Override
  protected Execution retrieveInternal(RedisClientDelegate redisClientDelegate, ExecutionType type, String id) throws ExecutionNotFoundException {
    String key = format("%s:%s", type, id);
    String indexKey = format("%s:stageIndex", key);

    boolean exists = redisClientDelegate.withCommandsClient(c -> {
      return c.exists(key);
    });
    if (!exists) {
      throw new ExecutionNotFoundException(format("No %s found for %s", type, id));
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
      } else {
        stageIds.addAll(extractStages(map));
      }
    });

    Execution execution = new Execution(type, id, map.get("application"));
    return buildExecution(execution, map, stageIds);
  }

  @Override
  protected List<String> fetchMultiExecutionStatus(RedisClientDelegate redisClientDelegate, List<String> keys) {
    return redisClientDelegate.withMultiKeyPipeline(p -> {
      List<Response<String>> responses = keys.stream()
        .map(k -> p.hget(k, "status"))
        .collect(Collectors.toList());
      p.sync();
      return responses.stream().map(Response::get).collect(Collectors.toList());
    });
  }

  @Override
  protected String executionKey(Execution execution) {
    return format("%s:%s", execution.getType(), execution.getId());
  }

  @Override
  protected String executionKey(Stage stage) {
    return format("%s:%s", stage.getExecution().getType(), stage.getExecution().getId());
  }

  @Override
  protected String executionKey(ExecutionType type, String id) {
    return format("%s:%s", type, id);
  }

  @Override
  protected String pipelineKey(String id) {
    return format("%s:%s", PIPELINE, id);
  }

  @Override
  protected String orchestrationKey(String id) {
    return format("%s:%s", ORCHESTRATION, id);
  }

}
