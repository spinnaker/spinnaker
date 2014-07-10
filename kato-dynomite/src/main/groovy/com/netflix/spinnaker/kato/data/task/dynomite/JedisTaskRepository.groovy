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

package com.netflix.spinnaker.kato.data.task.dynomite

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kato.data.task.DefaultTask
import com.netflix.spinnaker.kato.data.task.Status
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import org.springframework.beans.factory.annotation.Autowired
import redis.clients.jedis.JedisCommands

class JedisTaskRepository implements TaskRepository {

  @Autowired
  JedisCommands jedis

  ObjectMapper mapper = new ObjectMapper()

  @Override
  Task create(String phase, String status) {
    String taskId = jedis.incr('task_counter')
    new JedisTask(taskId, phase, status, this)
  }

  @Override
  Task get(String id) {
    Map<String, String> taskMap = jedis.hgetAll("task:${id}")
    JedisTask task = new JedisTask(
      taskMap.id,
      taskMap.phase,
      taskMap.status,
      Long.parseLong(taskMap.startTimeMs),
      taskMap.complete == 'true',
      taskMap.failed == 'true'
    )
    task.repository = this
    task
  }

  @Override
  List<Task> list() {
    jedis.keys('task:*').collect { key ->
      get(key - 'task:')
    }
  }

  void set(String id, JedisTask task) {
    String taskId = "task:${task.id}"
    Status status = task.status
    jedis.hset(taskId, 'id', task.id)
    jedis.hset(taskId, 'phase', status.phase)
    jedis.hset(taskId, 'status', status.status)
    jedis.hset(taskId, 'complete', status.isCompleted() as String)
    jedis.hset(taskId, 'failed', status.isFailed() as String)
    jedis.hset(taskId, 'startTimeMs', task.startTimeMs as String)
  }

  void addResultObject(Object o, JedisTask task) {
    String resultId = "taskResult:${task.id}:${jedis.incr('taskResultCounter')}"
    jedis.hset(resultId, 'type', o.class.canonicalName)
    jedis.hset(resultId, 'value', mapper.writeValueAsString(o))
  }

  List<Object> getResultObjects(JedisTask task) {
    List<Object> list = []
    jedis.keys("taskResult:${task.id}:*").each { key ->
      Map<String, String> results = jedis.hgetAll(key)
      list << mapper.readValue(results.value, Class.forName(results.type))
    }
    list
  }

  void addToHistory(String phase, String status, JedisTask task) {
    String historyId = "taskHistory:${task.id}:${jedis.incr('taskHistoryCounter')}"
    jedis.hset(historyId, 'phase', phase)
    jedis.hset(historyId, 'status', status)
  }

  List<Status> getHistory(JedisTask task) {
    List<Status> history = []
    jedis.keys("taskHistory:${task.id}:*").each { key ->
      Map<String, String> entry = jedis.hgetAll(key)
      history << new DefaultTask.TaskDisplayStatus(entry.phase, entry.status)
    }
    history
  }

}
