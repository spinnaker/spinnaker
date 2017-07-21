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
import com.netflix.spinnaker.cats.redis.RedisClientDelegate;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTaskStatus;
import com.netflix.spinnaker.clouddriver.data.task.Status;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskDisplayStatus;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.data.task.TaskState;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RedisTaskRepository implements TaskRepository {

  private static final String RUNNING_TASK_KEY = "kato:tasks";
  private static final String TASK_KEY_MAP = "kato:taskmap";
  private static final TypeReference<Map<String, String>> HISTORY_TYPE = new TypeReference<Map<String, String>>() {};

  private static final int TASK_TTL = (int) TimeUnit.HOURS.toSeconds(12);

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
    String taskId = redisClientDelegate.withCommandsClient(client -> {
      return client.incr("taskCounter").toString();
    });
    JedisTask task = new JedisTask(taskId, System.currentTimeMillis(), this, false);
    addToHistory(DefaultTaskStatus.create(phase, status, TaskState.STARTED), task);
    set(taskId, task);
    Long newTask = redisClientDelegate.withCommandsClient(client -> {
      return client.setnx(taskKey, taskId);
    });
    if (newTask != 0) {
      return task;
    }

    // There's an existing taskId for this key, clean up what we just created and get the existing task
    addToHistory(DefaultTaskStatus.create(phase, "Duplicate of " + clientRequestId, TaskState.FAILED), task);
    return getByClientRequestId(clientRequestId);
  }

  @Override
  public Task get(String id) {
    Map<String, String> taskMap = redisClientDelegate.withCommandsClient(client -> {
      return client.hgetAll("task:" + id);
    });
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
        oldTask
      );
    }
    return null;
  }

  @Override
  public Task getByClientRequestId(String clientRequestId) {
    final String clientRequestKey = getClientRequestKey(clientRequestId);
    String existingTask = redisClientDelegate.withCommandsClient(client -> {
      return client.get(clientRequestKey);
    });
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
    return redisClientDelegate.withCommandsClient(client -> {
      return client.smembers(RUNNING_TASK_KEY).stream().map(this::get).collect(Collectors.toList());
    });
  }

  public void set(String id, JedisTask task) {
    String taskId = "task:" + task.getId();
    Map<String, String> data = new HashMap<>();
    data.put("id", task.getId());
    data.put("startTimeMs", Long.toString(task.getStartTimeMs()));
    redisClientDelegate.withCommandsClient(client -> {
      client.hmset(taskId, data);
      client.expire(taskId, TASK_TTL);
      client.sadd(RUNNING_TASK_KEY, id);
    });
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

    redisClientDelegate.withCommandsClient(client -> {
      client.rpush(historyId, hist);
      client.expire(historyId, TASK_TTL);
      if (status.isCompleted()) {
        client.srem(RUNNING_TASK_KEY, task.getId());
      }
    });
  }

  public List<Status> getHistory(JedisTask task) {
    String historyId = "taskHistory:" + task.getId();
    return clientForTask(task).withCommandsClient(client -> {
      return client.lrange(historyId, 0, -1);
    }).stream()
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
    String state = redisClientDelegate.withCommandsClient(client -> {
      return client.lindex(historyId, -1);
    });
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
    redisClientDelegate.withCommandsClient(client -> {
      client.rpush(resultId, values);
      client.expire(resultId, TASK_TTL);
    });
  }

  public List<Object> getResultObjects(JedisTask task) {
    String resultId = "taskResult:" + task.getId();
    return redisClientDelegate.withCommandsClient(client -> {
      return client.lrange(resultId, 0, -1);
    }).stream()
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
}
