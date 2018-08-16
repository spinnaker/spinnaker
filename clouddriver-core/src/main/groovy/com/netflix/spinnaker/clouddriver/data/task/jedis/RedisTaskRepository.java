/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.data.task.jedis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.dyno.connectionpool.exception.DynoException;
import com.netflix.spinnaker.clouddriver.core.ClouddriverHostname;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTaskStatus;
import com.netflix.spinnaker.clouddriver.data.task.Status;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskDisplayStatus;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.data.task.TaskState;
import com.netflix.spinnaker.kork.dynomite.DynomiteClientDelegate.ClientDelegateException;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.function.CheckedConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.exceptions.JedisException;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class RedisTaskRepository implements TaskRepository {
  private static final Logger log = LoggerFactory.getLogger(RedisTaskRepository.class);

  private static final String RUNNING_TASK_KEY = "kato:tasks";
  private static final String TASK_KEY_MAP = "kato:taskmap";
  private static final TypeReference<Map<String, String>> HISTORY_TYPE = new TypeReference<Map<String, String>>() {};

  private static final int TASK_TTL = (int) TimeUnit.HOURS.toSeconds(12);

  private static final RetryPolicy REDIS_RETRY_POLICY = new RetryPolicy()
    .retryOn(Arrays.asList(JedisException.class, DynoException.class, ClientDelegateException.class))
    .withDelay(500, TimeUnit.MILLISECONDS)
    .withMaxRetries(3);

  private final RedisClientDelegate redisClientDelegate;
  private final Optional<RedisClientDelegate> redisClientDelegatePrevious;
  private final ObjectMapper mapper = new ObjectMapper();

  public RedisTaskRepository(RedisClientDelegate redisClientDelegate, Optional<RedisClientDelegate> redisClientDelegatePrevious) {
    this.redisClientDelegate = redisClientDelegate;
    this.redisClientDelegatePrevious = redisClientDelegatePrevious;
  }

  @Override
  public Task create(String phase, String status) {
    return create(phase, status, UUID.randomUUID().toString());
  }

  @Override
  public Task create(String phase, String status, String clientRequestId) {
    String taskKey = getClientRequestKey(clientRequestId);

    String taskId = UUID.randomUUID().toString();

    JedisTask task = new JedisTask(taskId, System.currentTimeMillis(), this, ClouddriverHostname.ID, false);
    addToHistory(DefaultTaskStatus.create(phase, status, TaskState.STARTED), task);
    set(taskId, task);
    Long newTask = retry(() -> redisClientDelegate.withCommandsClient(client -> {
      return client.setnx(taskKey, taskId);
    }), "Registering task with index");
    if (newTask != 0) {
      return task;
    }

    // There's an existing taskId for this key, clean up what we just created and get the existing task
    addToHistory(DefaultTaskStatus.create(phase, "Duplicate of " + clientRequestId, TaskState.FAILED), task);
    return getByClientRequestId(clientRequestId);
  }

  @Override
  public Task get(String id) {
    Map<String, String> taskMap = retry(() -> redisClientDelegate.withCommandsClient(client -> {
      return client.hgetAll("task:" + id);
    }), format("Getting task ID %s", id));
    boolean oldTask = redisClientDelegatePrevious.isPresent() && (taskMap == null || taskMap.isEmpty());
    if (oldTask) {
      try {
        taskMap = redisClientDelegatePrevious.get().withCommandsClient(client -> {
          return client.hgetAll("task:" + id);
        });
      } catch (Exception e) {
        // Failed to hit old redis, let's not blow up on that
        return null;
      }
    }
    if (taskMap.containsKey("id") && taskMap.containsKey("startTimeMs")) {
      return new JedisTask(
        taskMap.get("id"),
        Long.parseLong(taskMap.get("startTimeMs")),
        this,
        taskMap.get("ownerId"),
        oldTask
      );
    }
    return null;
  }

  @Override
  public Task getByClientRequestId(String clientRequestId) {
    final String clientRequestKey = getClientRequestKey(clientRequestId);
    String existingTask = retry(() -> redisClientDelegate.withCommandsClient(client -> {
      return client.get(clientRequestKey);
    }), format("Getting task by client request ID %s", clientRequestId));
    if (existingTask == null) {
      if (redisClientDelegatePrevious.isPresent()) {
        try {
          existingTask = redisClientDelegatePrevious.get().withCommandsClient(client -> {
            return client.get(clientRequestKey);
          });
        } catch (Exception e) {
          // Failed to hit old redis, let's not blow up on that
          existingTask = null;
        }
      }
    }
    if (existingTask != null) {
      return get(existingTask);
    }
    return null;
  }

  @Override
  public List<Task> list() {
    return retry(() -> redisClientDelegate.withCommandsClient(client -> {
      return client.smembers(RUNNING_TASK_KEY).stream().map(this::get).collect(Collectors.toList());
    }), "Getting all running tasks");
  }

  @Override
  public List<Task> listByThisInstance() {
    return list().stream()
        .filter(t -> ClouddriverHostname.ID.equals(t.getOwnerId()))
        .collect(Collectors.toList());
  }

  public void set(String id, JedisTask task) {
    String taskId = "task:" + task.getId();
    Map<String, String> data = new HashMap<>();
    data.put("id", task.getId());
    data.put("startTimeMs", Long.toString(task.getStartTimeMs()));
    data.put("ownerId", task.getOwnerId());
    retry(() -> redisClientDelegate.withCommandsClient(client -> {
      client.hmset(taskId, data);
      client.expire(taskId, TASK_TTL);
      client.sadd(RUNNING_TASK_KEY, id);
    }), format("Writing task %s", id));
  }

  public void addToHistory(DefaultTaskStatus status, JedisTask task) {
    String historyId = "taskHistory:" + task.getId();

    Map<String, String> data = new HashMap<>();
    data.put("phase", status.getPhase());
    data.put("status", status.getStatus());
    data.put("state", status.getState().toString());

    String hist;
    try {
      hist = mapper.writeValueAsString(data);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed converting task history to json", e);
    }

    retry(() -> redisClientDelegate.withCommandsClient(client -> {
      client.rpush(historyId, hist);
      client.expire(historyId, TASK_TTL);
      if (status.isCompleted()) {
        client.srem(RUNNING_TASK_KEY, task.getId());
      }
    }), format("Adding status history to task %s: %s", task.getId(), status));
  }

  public List<Status> getHistory(JedisTask task) {
    String historyId = "taskHistory:" + task.getId();

    RedisClientDelegate client = clientForTask(task);
    return retry(() -> client.withCommandsClient(c -> {
      return c.lrange(historyId, 0, -1);
    }), format("Getting history for task %s", task.getId()))
      .stream()
      .map(h -> {
        Map<String, String> history;
        try {
          history = mapper.readValue(h, HISTORY_TYPE);
        } catch (IOException e) {
          throw new RuntimeException("Could not convert history json to type", e);
        }
        return TaskDisplayStatus.create(DefaultTaskStatus.create(history.get("phase"), history.get("status"), TaskState.valueOf(history.get("state"))));
      })
      .collect(Collectors.toList());
  }

  public DefaultTaskStatus currentState(JedisTask task) {
    String historyId = "taskHistory:" + task.getId();

    RedisClientDelegate client = clientForTask(task);
    String state = retry(() -> client.withCommandsClient(c -> {
      return c.lindex(historyId, -1);
    }), format("Getting current state for task %s", task.getId()));

    Map<String, String> history;
    try {
      history = mapper.readValue(state, HISTORY_TYPE);
    } catch (IOException e) {
      throw new RuntimeException("Failed converting task history json to object", e);
    }
    return DefaultTaskStatus.create(history.get("phase"), history.get("status"), TaskState.valueOf(history.get("state")));
  }

  public void addResultObjects(List<Object> objects, JedisTask task) {
    String resultId = "taskResult:" + task.getId();
    String[] values = objects.stream()
      .map(o -> {
        try {
          return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
          throw new RuntimeException("Failed to convert object to string", e);
        }
      })
      .collect(Collectors.toList())
      .toArray(new String[objects.size()]);

    log.debug("Adding results to task {} (results: {})", task.getId(), values);
    retry(() -> redisClientDelegate.withCommandsClient(client -> {
      client.rpush(resultId, values);
      client.expire(resultId, TASK_TTL);
    }), format("Adding results to task %s", task.getId()));
  }

  public List<Object> getResultObjects(JedisTask task) {
    String resultId = "taskResult:" + task.getId();

    return retry(() -> clientForTask(task).withCommandsClient(client -> {
      return client.lrange(resultId, 0, -1);
    }), format("Getting results for task %s", task.getId()))
      .stream()
      .map(o -> {
        try {
          return mapper.readValue(o, Map.class);
        } catch (IOException e) {
          throw new RuntimeException("Failed to convert result object to map", e);
        }
      })
      .collect(Collectors.toList());
  }

  private String getClientRequestKey(String clientRequestId) {
    return TASK_KEY_MAP + ":" + clientRequestId;
  }

  private RedisClientDelegate clientForTask(JedisTask task) {
    if (task.getPreviousRedis() && redisClientDelegatePrevious.isPresent()) {
      return redisClientDelegatePrevious.get();
    }
    return redisClientDelegate;
  }

  private <T> T retry(Supplier<T> f, String onRetriesExceededMessage) {
    return retry(f, failure -> { throw new ExcessiveRedisFailureRetries(onRetriesExceededMessage, failure); });
  }

  private <T> T retry(Supplier<T> f, CheckedConsumer<? extends Throwable> retryExceededListener) {
    return Failsafe
      .with(REDIS_RETRY_POLICY)
      .onRetriesExceeded(retryExceededListener)
      .get(f::get);
  }

  private void retry(Runnable f, String onRetriesExceededMessage) {
    retry(f, failure -> { throw new ExcessiveRedisFailureRetries(onRetriesExceededMessage, failure); });
  }

  private void retry(Runnable f, CheckedConsumer<? extends Throwable> retryExceededListener) {
    Failsafe
      .with(REDIS_RETRY_POLICY)
      .onRetriesExceeded(retryExceededListener)
      .run(f::run);
  }

  private static class ExcessiveRedisFailureRetries extends RuntimeException {
    ExcessiveRedisFailureRetries(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
