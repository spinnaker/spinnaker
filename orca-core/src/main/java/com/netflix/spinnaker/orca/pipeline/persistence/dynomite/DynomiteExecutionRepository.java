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

package com.netflix.spinnaker.orca.pipeline.persistence.dynomite;

import com.netflix.dyno.jedis.DynoJedisPipeline;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.RedisClientSelector;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.AbstractRedisExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import redis.clients.jedis.Response;
import rx.Scheduler;
import rx.schedulers.Schedulers;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.google.common.collect.Maps.filterValues;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;

/* Experimental: not for production use.
 *
 * Dynomoite (https://github.com/Netflix/dynomite) is a dynamo inspired AP multi-region
 * store using Redis as a storage engine. It provides a subset of the Redis command set, primarily
 * as Dynomite does not support transactions. Pipelined operations are also constrained;
 * all operations must be over the same key, or keys containing the same {hash set}.
 *
 * This ExecutionRepository implementation is transactionless. $type:$id:stageIndex keys
 * are now an unsorted set instead of a sorted list and the stageIndex key within
 * $type:$id execution hashes has been removed.
 *
 * As a result, this implementation cannot be used against an existing Redis
 * ExecutionRepository without a migration.
 */

public class DynomiteExecutionRepository extends AbstractRedisExecutionRepository {

  private final Logger log = LoggerFactory.getLogger(getClass());

  public DynomiteExecutionRepository(
    RedisClientSelector redisClientSelector,
    @Qualifier("queryAllScheduler") Scheduler queryAllScheduler,
    @Qualifier("queryByAppScheduler") Scheduler queryByAppScheduler,
    @Value("${chunkSize.executionRepository:75}") Integer threadPoolChunkSize
  ) {
    super(redisClientSelector, queryAllScheduler, queryByAppScheduler, threadPoolChunkSize);
  }

  public DynomiteExecutionRepository(
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
    RedisClientDelegate delegate = getRedisDelegateForId(executionKey(execution));
    storeExecutionInternal(delegate, execution);
    if (execution.getType() == PIPELINE) {
      delegate.withCommandsClient(c -> {
        c.zadd(executionsByPipelineKey(execution.getPipelineConfigId()),
          execution.getBuildTime() != null ? execution.getBuildTime() : currentTimeMillis(),
          execution.getId()
        );
      });
    }
  }

  @Override
  public void storeStage(@Nonnull Stage stage) {
    storeStageInternal(getRedisDelegateForId(executionKey(stage)), stage, false);
  }

  @Override
  public void removeStage(@Nonnull Execution execution, @Nonnull String stageId) {
    String key = executionKey(execution);
    String indexKey = format("%s:stageIndex", key);
    RedisClientDelegate delegate = getRedisDelegateForId(key.substring(1, key.length() - 1));

    List<String> stageKeys = delegate.withCommandsClient(c -> {
      return c.hkeys(key).stream()
        .filter(k -> k.startsWith("stage." + stageId))
        .collect(Collectors.toList());
    });

    delegate.withPipeline(pipeline -> {
      DynoJedisPipeline p = (DynoJedisPipeline) pipeline;
      p.srem(indexKey, stageId);
      p.hdel(key, stageKeys.toArray(new String[0]));
      p.sync();
    });
  }

  @Override
  public void addStage(@Nonnull Stage stage) {
    if (stage.getSyntheticStageOwner() == null || stage.getParentStageId() == null) {
      throw new IllegalArgumentException("Only synthetic stages can be inserted ad-hoc");
    }

    storeStageInternal(getRedisDelegateForId(executionKey(stage)), stage, true);
  }

  private void storeExecutionInternal(RedisClientDelegate delegate, Execution execution) {
    String key = executionKey(execution);
    String indexKey = format("%s:stageIndex", key);
    Map<String, String> map = serializeExecution(execution);

    delegate.withCommandsClient(c -> {
      c.sadd(alljobsKey(execution.getType()), execution.getId());
      c.sadd(appKey(execution.getType(), execution.getApplication()), execution.getId());

      delegate.withPipeline(pipeline -> {
        DynoJedisPipeline p = (DynoJedisPipeline) pipeline;
        p.hdel(key, "config");
        p.hmset(key, filterValues(map, Objects::nonNull));
        if (!execution.getStages().isEmpty()) {
          p.sadd(indexKey, execution.getStages().stream()
            .map(Stage::getId)
            .collect(Collectors.toList())
            .toArray(new String[0])
          );
        }
        p.sync();
      });

      if (execution.getTrigger().getCorrelationId() != null) {
        c.set(
          format("correlation:%s", execution.getTrigger().getCorrelationId()),
          execution.getId()
        );
      }
    });
  }

  private void storeStageInternal(RedisClientDelegate delegate, Stage stage, Boolean updateIndex) {
    String key = executionKey(stage);
    String indexKey = format("%s:stageIndex", key);

    Map<String, String> serializedStage = serializeStage(stage);
    List<String> keysToRemove = serializedStage.entrySet().stream()
      .filter(e -> e.getValue() == null)
      .map(Map.Entry::getKey)
      .collect(Collectors.toList());

    serializedStage.values().removeIf(Objects::isNull);

    delegate.withPipeline(pipeline -> {
      DynoJedisPipeline p = (DynoJedisPipeline) pipeline;
      p.hmset(key, serializedStage);

      if (!keysToRemove.isEmpty()) {
        p.hdel(key, keysToRemove.toArray(new String[0]));
      }
      if (updateIndex) {
        p.sadd(indexKey, stage.getId());
      }
      p.sync();
    });
  }

  @Override
  protected Execution retrieveInternal(RedisClientDelegate redisClientDelegate, ExecutionType type, String id) throws ExecutionNotFoundException {
    String key = executionKey(type, id);
    String indexKey = format("%s:stageIndex", key);

    boolean exists = redisClientDelegate.withCommandsClient(c -> {
      return c.exists(key);
    });
    if (!exists) {
      throw new ExecutionNotFoundException("No ${type} found for $id");
    }

    final Map<String, String> map = new HashMap<>();
    final List<String> stageIds = new ArrayList<>();

    redisClientDelegate.withPipeline(pipeline -> {
      DynoJedisPipeline p = (DynoJedisPipeline) pipeline;
      Response<Map<String, String>> execResponse = p.hgetAll(key);
      Response<Set<String>> indexResponse = p.smembers(indexKey);
      try {
        p.sync();
      } catch (Exception e) {
        log.error(e.getMessage(), e);
      }

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
    List<String> statuses = new ArrayList<>();
    redisClientDelegate.withCommandsClient(c -> {
      keys.forEach(k -> statuses.add(c.hget(k, "status")));
    });
    return statuses;
  }

  @Override
  protected String executionKey(Execution execution) {
    return format("{%s:%s}", execution.getType(), execution.getId());
  }

  @Override
  protected String executionKey(Stage stage) {
    return format("{%s:%s}", stage.getExecution().getType(), stage.getExecution().getId());
  }

  @Override
  protected String executionKey(ExecutionType type, String id) {
    return format("{%s:%s}", type, id);
  }

  @Override
  protected String pipelineKey(String id) {
    return format("{%s:%s}", PIPELINE, id);
  }

  @Override
  protected String orchestrationKey(String id) {
    return format("{%s:%s}", ORCHESTRATION, id);
  }
}
