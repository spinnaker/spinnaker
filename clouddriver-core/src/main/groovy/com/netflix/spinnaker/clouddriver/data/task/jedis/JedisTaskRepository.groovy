/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.data.task.jedis

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.data.task.DefaultTaskStatus
import com.netflix.spinnaker.clouddriver.data.task.Status
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskDisplayStatus
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.data.task.TaskState
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import groovy.util.logging.Slf4j
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

import java.util.concurrent.TimeUnit

@Slf4j
class JedisTaskRepository implements TaskRepository {
  private static final String RUNNING_TASK_KEY = "kato:tasks"
  private static final String TASK_KEY_MAP = "kato:taskmap"
  private static final TypeReference<Map<String, String>> HISTORY_TYPE = new TypeReference<Map<String, String>>() {}

  private static final int TASK_TTL = (int) TimeUnit.HOURS.toSeconds(12);

  private final JedisPool jedisPool
  private final Optional<JedisPool> jedisPoolPrevious
  private final ObjectMapper mapper = new ObjectMapper()

  JedisTaskRepository(JedisPool jedisPool, Optional<JedisPool> jedisPoolPrevious) {
    this.jedisPool = jedisPool
    this.jedisPoolPrevious = jedisPoolPrevious
  }

  @Override
  Task create(String phase, String status) {
    create(phase, status, UUID.randomUUID().toString())
  }

  @Override
  Task create(String phase, String status, String clientRequestId) {
    String taskKey = getClientRequestKey(clientRequestId)
    String taskId = jedis { it.incr('taskCounter') }
    def task = new JedisTask(taskId, System.currentTimeMillis(), this, false)
    addToHistory(new DefaultTaskStatus(phase, status, TaskState.STARTED), task)
    set(taskId, task)
    Long newTask = jedis { it.setnx(taskKey, taskId) }
    if (newTask) {
      return task
    }
    //there's an existing taskId for this key, clean up what we just created and get the existing task:
    addToHistory(new DefaultTaskStatus(phase, "Duplicate of $clientRequestId", TaskState.FAILED), task)
    return getByClientRequestId(clientRequestId)
  }

  @Override
  Task getByClientRequestId(String clientRequestId) {
    final String clientRequestKey = getClientRequestKey(clientRequestId)
    def lookupByClientRequestKey = { Jedis jedis -> jedis.get(clientRequestKey) }
    String existingTask = jedis(lookupByClientRequestKey)
    if (!existingTask) {
      if (jedisPoolPrevious.isPresent()) {
        try {
          existingTask = jedisWithPool(jedisPoolPrevious.get(), lookupByClientRequestKey)
        } catch (Exception e) {
          //failed to hit old jedis, lets not blow up on that
          existingTask = null
        }
      }
    }
    if (existingTask) {
      return get(existingTask)
    }
    return null
  }

  private String getClientRequestKey(String clientRequestId) {
    TASK_KEY_MAP + ":" + clientRequestId
  }

  @Override
  Task get(String id) {
    def getTask = { Jedis jedis -> jedis.hgetAll("task:${id}") }
    Map<String, String> taskMap = jedis getTask
    boolean oldTask = jedisPoolPrevious.isPresent() && (taskMap == null || taskMap.isEmpty())
    if (oldTask) {
      try {
        taskMap = jedisWithPool(jedisPoolPrevious.get(), getTask)
      } catch (Exception e) {
        //failed to hit old jedis, lets not blow up on that
        return null
      }
    }
    if (taskMap.id && taskMap.startTimeMs) {
      return new JedisTask(
        taskMap.id,
        Long.parseLong(taskMap.startTimeMs),
        this,
        oldTask
      )
    }
    return null
  }

  @Override
  List<Task> list() {
    jedis { it.smembers(RUNNING_TASK_KEY) }.collect { key ->
      get(key)
    }
  }

  void set(String id, JedisTask task) {
    String taskId = "task:${task.id}"
    jedis {
      def pipe = it.pipelined()
      pipe.hmset(taskId, [id: task.id, startTimeMs: task.startTimeMs as String])
      pipe.expire(taskId, TASK_TTL)
      pipe.sync()
    }
    jedis { it.sadd(RUNNING_TASK_KEY, id) }
  }

  void addResultObjects(List<Object> objects, JedisTask task) {
    String resultId = "taskResult:${task.id}"
    String[] values = objects.collect { mapper.writeValueAsString(it) }
    jedis {
      def pipe = it.pipelined()
      pipe.rpush(resultId, values)
      pipe.expire(resultId, TASK_TTL)
      pipe.sync()
    }

    log.debug("addResultObjects (resultId: ${resultId}, values: ${values})")
  }

  List<Object> getResultObjects(JedisTask task) {
    String resultId = "taskResult:${task.id}"
    def pool = poolForTask(task)
    jedisWithPool(pool) { it.lrange(resultId, 0, -1) }.collect { mapper.readValue(it, Map) }
  }

  DefaultTaskStatus currentState(JedisTask task) {
    String historyId = "taskHistory:${task.id}"
    def pool = poolForTask(task)

    Map<String, String> history = mapper.readValue(jedisWithPool(pool) { it.lindex(historyId, -1) }, HISTORY_TYPE)
    new DefaultTaskStatus(history.phase, history.status, TaskState.valueOf(history.state))
  }

  void addToHistory(DefaultTaskStatus status, JedisTask task) {
    String historyId = "taskHistory:${task.id}"
    def hist = mapper.writeValueAsString([phase: status.phase, status: status.status, state: status.state.toString()])
    jedis {
      def pipe = it.pipelined()
      pipe.rpush(historyId, hist)
      pipe.expire(historyId, TASK_TTL)
      pipe.sync()
    }
    if (status.isCompleted()) {
      jedis { it.srem(RUNNING_TASK_KEY, task.id) }
    }
  }

  List<Status> getHistory(JedisTask task) {
    String historyId = "taskHistory:${task.id}"
    def pool = poolForTask(task)
    jedisWithPool(pool) { it.lrange(historyId, 0, -1) }.collect {
      Map<String, String> history = mapper.readValue(it, HISTORY_TYPE)
      new TaskDisplayStatus(new DefaultTaskStatus(history.phase, history.status, TaskState.valueOf(history.state)))
    }
  }

  private <T> T jedis(@ClosureParams(value = SimpleType,
    options = ['redis.clients.jedis.Jedis']) Closure<T> withJedis) {
    return jedisWithPool(jedisPool, withJedis)
  }

  private JedisPool poolForTask(JedisTask task) {
    if (task.previousRedis && jedisPoolPrevious.isPresent()) {
      return jedisPoolPrevious.get()
    }
    return jedisPool
  }

  private static <T> T jedisWithPool(JedisPool pool,
                                     @ClosureParams(value = SimpleType,
                                       options = ['redis.clients.jedis.Jedis']) Closure<T> withJedis) {
    pool.getResource().withCloseable {
      Jedis jedis = (Jedis) it
      return withJedis.call(jedis)
    }
  }
}
