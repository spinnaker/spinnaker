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
package com.netflix.spinnaker.clouddriver.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.data.task.DefaultTaskStatus
import com.netflix.spinnaker.clouddriver.data.task.Status
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskState
import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.String.format
import java.sql.ResultSet

class TaskMapper(
  private val sqlTaskRepository: SqlTaskRepository,
  private val mapper: ObjectMapper
) {

  companion object {
    private val log = LoggerFactory.getLogger(TaskMapper::class.java)
  }

  fun map(rs: ResultSet): Collection<Task> {
    val tasks = mutableMapOf<String, SqlTask>()
    val results = mutableMapOf<String, MutableList<Any>>()
    val history = mutableMapOf<String, MutableList<Status>>()

    while (rs.next()) {
      when {
        rs.getString("owner_id") != null -> SqlTask(
          rs.getString("task_id"),
          rs.getString("owner_id"),
          rs.getString("request_id"),
          rs.getLong("created_at"),
          sqlTaskRepository
        ).let {
          tasks[it.id] = it
        }
        rs.getString("body") != null -> {
          try {
            if (!results.containsKey(rs.getString("task_id"))) {
              results[rs.getString("task_id")] = mutableListOf()
            }
            results[rs.getString("task_id")]!!.add(mapper.readValue(rs.getString("body"), Map::class.java))
          } catch (e: IOException) {
            val id = rs.getString("id")
            val taskId = rs.getString("task_id")
            throw RuntimeException(
              format("Failed to convert result object body to map (id: %s, taskId: %s)", id, taskId),
              e
            )
          }
        }
        rs.getString("state") != null -> {
          if (!history.containsKey(rs.getString("task_id"))) {
            history[rs.getString("task_id")] = mutableListOf()
          }
          history[rs.getString("task_id")]!!.add(DefaultTaskStatus.create(
            rs.getString("phase"),
            rs.getString("status"),
            TaskState.valueOf(rs.getString("state"))
          ))
        }
      }
    }

    return tasks.values.map { task ->
      task.hydrateResultObjects(results.getOrDefault(task.id, mutableListOf()))
      task.hydrateHistory(history.getOrDefault(task.id, mutableListOf()))
      task
    }
  }
}
