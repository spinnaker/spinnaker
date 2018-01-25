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

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Execution.PausedDetails;
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.Task;
import com.netflix.spinnaker.orca.pipeline.persistence.*;
import org.apache.commons.lang3.StringUtils;
import redis.clients.jedis.JedisCommands;
import rx.Scheduler;

import javax.annotation.Nonnull;

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE;
import static java.lang.System.currentTimeMillis;
import static java.lang.String.format;

public abstract class AbstractRedisExecutionRepository implements ExecutionRepository {

  static final TypeReference<List<Task>> LIST_OF_TASKS =
    new TypeReference<List<Task>>() {
    };
  static final TypeReference<Map<String, Object>> MAP_STRING_TO_OBJECT =
    new TypeReference<Map<String, Object>>() {
    };
  final RedisClientDelegate redisClientDelegate;
  final Optional<RedisClientDelegate> previousRedisClientDelegate;
  protected final ObjectMapper mapper = OrcaObjectMapper.newInstance();
  final int chunkSize;
  final Scheduler queryAllScheduler;
  final Scheduler queryByAppScheduler;
  private final Registry registry;

  AbstractRedisExecutionRepository(
    Registry registry,
    RedisClientDelegate redisClientDelegate,
    Optional<RedisClientDelegate> previousRedisClientDelegate,
    Scheduler queryAllScheduler,
    Scheduler queryByAppScheduler,
    Integer threadPoolChunkSize
  ) {
    this.redisClientDelegate = redisClientDelegate;
    this.previousRedisClientDelegate = previousRedisClientDelegate;
    this.queryAllScheduler = queryAllScheduler;
    this.queryByAppScheduler = queryByAppScheduler;
    this.chunkSize = threadPoolChunkSize;
    this.registry = registry;
  }

  @Override
  public void cancel(@Nonnull String id) {
    cancel(id, null, null);
  }

  @Override
  public void cancel(@Nonnull String id, String user, String reason) {
    String key = fetchKey(id);

    getRedisDelegateForId(key).withCommandsClient(c -> {
      Map<String, String> data = new HashMap<>();
      data.put("canceled", "true");
      if (StringUtils.isNotEmpty(user)) {
        data.put("canceledBy", user);
      }
      if (StringUtils.isNotEmpty(reason)) {
        data.put("cancellationReason", reason);
      }
      ExecutionStatus currentStatus = ExecutionStatus.valueOf(c.hget(key, "status"));
      if (currentStatus == ExecutionStatus.NOT_STARTED) {
        data.put("status", ExecutionStatus.CANCELED.name());
      }
      c.hmset(key, data);
    });
  }

  @Override
  public boolean isCanceled(@Nonnull String id) {
    String key = fetchKey(id);

    return getRedisDelegateForId(key).withCommandsClient(c -> {
      return Boolean.valueOf(c.hget(key, "canceled"));
    });
  }

  @Override
  public void pause(@Nonnull String id, String user) {
    String key = fetchKey(id);

    getRedisDelegateForId(key).withCommandsClient(c -> {
      ExecutionStatus currentStatus = ExecutionStatus.valueOf(c.hget(key, "status"));
      if (currentStatus != ExecutionStatus.RUNNING) {
        throw new UnpausablePipelineException(format(
          "Unable to pause pipeline that is not RUNNING (executionId: %s, currentStatus: %s)",
          id,
          currentStatus
        ));
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
      c.hmset(key, data);
    });
  }

  @Override
  public void resume(@Nonnull String id, String user) {
    resume(id, user, false);
  }

  @Override
  public void resume(@Nonnull String id, String user, boolean ignoreCurrentStatus) {
    String key = fetchKey(id);

    getRedisDelegateForId(key).withCommandsClient(c -> {
      ExecutionStatus currentStatus = ExecutionStatus.valueOf(c.hget(key, "status"));
      if (!ignoreCurrentStatus && currentStatus != ExecutionStatus.PAUSED) {
        throw new UnresumablePipelineException(format(
          "Unable to resume pipeline that is not PAUSED (executionId: %s, currentStatus: %s)",
          id,
          currentStatus
        ));
      }

      try {
        PausedDetails pausedDetails = mapper.readValue(c.hget(key, "paused"), Execution.PausedDetails.class);
        pausedDetails.setResumedBy(user);
        pausedDetails.setResumeTime(currentTimeMillis());

        Map<String, String> data = new HashMap<>();
        data.put("paused", mapper.writeValueAsString(pausedDetails));
        data.put("status", ExecutionStatus.RUNNING.toString());
        c.hmset(key, data);
      } catch (IOException e) {
        throw new ExecutionSerializationException("Failed converting pausedDetails to json", e);
      }
    });
  }

  @Override
  public void updateStatus(@Nonnull String id, @Nonnull ExecutionStatus status) {
    String key = fetchKey(id);
    getRedisDelegateForId(key).withCommandsClient(c -> {
      Map<String, String> data = new HashMap<>();
      data.put("status", status.name());
      if (status == ExecutionStatus.RUNNING) {
        data.put("canceled", "false");
        data.put("startTime", String.valueOf(currentTimeMillis()));
      } else if (status.isComplete()) {
        data.put("endTime", String.valueOf(currentTimeMillis()));
      }
      c.hmset(key, data);
    });
  }

  @Override
  public void updateStageContext(@Nonnull Stage stage) {
    Execution execution = stage.getExecution();
    String type = stage.getType();
    String key = format("%s:%s", type, execution.getId());
    String contextKey = format("stage.%s.context", stage.getId());
    getRedisDelegateForId(key).withCommandsClient(c -> {
      try {
        c.set(key, contextKey, mapper.writeValueAsString(stage.getContext()));
      } catch (JsonProcessingException e) {
        throw new StageSerializationException("Failed converting stage context to json", e);
      }
    });
  }

  @Override
  public void delete(@Nonnull ExecutionType type, @Nonnull String id) {
    String key = format("%s:%s", type, id);
    getRedisDelegateForId(key).withCommandsClient(c -> {
      deleteInternal(c, type, id);
    });
  }

  Map<String, String> serializeStage(Stage stage) {
    String prefix = format("stage.%s.", stage.getId());
    Map<String, String> map = new HashMap<>();
    map.put(prefix + "refId", stage.getRefId());
    map.put(prefix + "type", stage.getType());
    map.put(prefix + "name", stage.getName());
    map.put(prefix + "startTime", stage.getStartTime() != null ? stage.getStartTime().toString() : null);
    map.put(prefix + "endTime", stage.getEndTime() != null ? stage.getEndTime().toString() : null);
    map.put(prefix + "status", stage.getStatus().name());
    map.put(prefix + "syntheticStageOwner", stage.getSyntheticStageOwner() != null ? stage.getSyntheticStageOwner().name() : null);
    map.put(prefix + "parentStageId", stage.getParentStageId());
    if (!stage.getRequisiteStageRefIds().isEmpty()) {
      map.put(prefix + "requisiteStageRefIds", stage.getRequisiteStageRefIds().stream()
        .collect(Collectors.joining(","))
      );
    }
    map.put(prefix + "scheduledTime", (stage.getScheduledTime() != null ? stage.getScheduledTime().toString() : null));
    try {
      map.put(prefix + "context", mapper.writeValueAsString(stage.getContext()));
      map.put(prefix + "outputs", mapper.writeValueAsString(stage.getOutputs()));
      map.put(prefix + "tasks", mapper.writeValueAsString(stage.getTasks()));
      map.put(prefix + "lastModified", (stage.getLastModified() != null ? mapper.writeValueAsString(stage.getLastModified()) : null));
    } catch (JsonProcessingException e) {
      throw new StageSerializationException("Failed converting stage to json", e);
    }
    return map;
  }

  private void deleteInternal(JedisCommands c, ExecutionType type, String id) {
    String key = format("%s:%s", type, id);
    try {
      String application = c.hget(key, "application");
      String appKey = appKey(type, application);
      c.srem(appKey, id);

      if (type == PIPELINE) {
        String pipelineConfigId = c.hget(key, "pipelineConfigId");
        c.zrem(executionsByPipelineKey(pipelineConfigId), id);
      }
    } catch (ExecutionNotFoundException ignored) {
      // do nothing
    } finally {
      c.del(key);
      c.srem(alljobsKey(type), id);
    }
  }

  static String alljobsKey(ExecutionType type) {
    return format("allJobs:%s", type);
  }

  static String appKey(ExecutionType type, String app) {
    return format("%s:app:%s", type, app);
  }

  static String pipelineKey(String id) {
    return format("pipeline:%s", id);
  }

  static String orchestrationKey(String id) {
    return format("orchestration:%s", id);
  }

  protected static String executionsByPipelineKey(String pipelineConfigId) {
    String id = pipelineConfigId != null ? pipelineConfigId : "---";
    return format("pipeline:executions:%s", id);
  }

  private String fetchKey(String id) {
    String key = redisClientDelegate.withCommandsClient(c -> {
      if (c.exists(pipelineKey(id))) {
        return pipelineKey(id);
      } else if (c.exists(orchestrationKey(id))) {
        return orchestrationKey(id);
      }
      return null;
    });

    if (key == null && previousRedisClientDelegate.isPresent()) {
      key = redisClientDelegate.withCommandsClient(c -> {
        if (c.exists(pipelineKey(id))) {
          return pipelineKey(id);
        } else if (c.exists(orchestrationKey(id))) {
          return orchestrationKey(id);
        }
        return null;
      });
    }

    if (key == null) {
      throw new ExecutionNotFoundException("No execution found with id " + id);
    }
    return key;
  }

  RedisClientDelegate getRedisDelegateForId(String id) {
    if (StringUtils.isEmpty(id)) {
      return redisClientDelegate;
    }

    RedisClientDelegate delegate = redisClientDelegate.withCommandsClient(c -> {
      if (c.exists(id)) {
        return redisClientDelegate;
      } else {
        return null;
      }
    });

    if (delegate == null && previousRedisClientDelegate.isPresent()) {
      delegate = previousRedisClientDelegate.get().withCommandsClient(c -> {
        if (c.exists(id)) {
          return previousRedisClientDelegate.get();
        } else {
          return null;
        }
      });
    }

    return (delegate == null) ? redisClientDelegate : delegate;
  }

  Collection<RedisClientDelegate> allRedisDelegates() {
    Collection<RedisClientDelegate> delegates = new ArrayList<>();
    delegates.add(redisClientDelegate);
    previousRedisClientDelegate.ifPresent(delegates::add);
    return delegates;
  }
}
