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

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.data.task.DefaultTaskStatus
import com.netflix.spinnaker.clouddriver.data.task.SagaId
import com.netflix.spinnaker.clouddriver.data.task.Status
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskDisplayOutput
import com.netflix.spinnaker.clouddriver.data.task.TaskOutput
import com.netflix.spinnaker.clouddriver.data.task.TaskState
import java.io.IOException
import java.lang.String.format
import java.sql.ResultSet
import org.slf4j.LoggerFactory

class TaskMapper(
  private val sqlTaskRepository: SqlTaskRepository,
  private val mapper: ObjectMapper
) {

  companion object {
    private val log = LoggerFactory.getLogger(TaskMapper::class.java)

    private val SAGA_IDS_TYPE = object : TypeReference<MutableSet<SagaId>>() {}
  }

  fun map(rs: ResultSet): Collection<Task> {
    val tasks = mutableMapOf<String, SqlTask>()
    val results = mutableMapOf<String, MutableList<Pair<RowOrder, Any>>>()
    val history = mutableMapOf<String, MutableList<Pair<RowOrder, Status>>>()
    val taskOutputs = mutableMapOf<String, MutableList<Pair<RowOrder, TaskOutput>>>()

    while (rs.next()) {
      when {
        rs.getString("owner_id") != null ->
          SqlTask(
            rs.getString("task_id"),
            rs.getString("owner_id"),
            rs.getString("request_id"),
            rs.getLong("created_at"),
            sagaIds(rs.getString("saga_ids")),
            sqlTaskRepository
          ).let {
            tasks[it.id] = it
          }
        rs.getString("body") != null -> {
          try {
            if (!results.containsKey(rs.getString("task_id"))) {
              results[rs.getString("task_id")] = mutableListOf()
            }
            results[rs.getString("task_id")]!!.add(RowOrder.of(rs) to mapper.readValue(rs.getString("body"), Map::class.java))
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
          history[rs.getString("task_id")]!!.add(
            RowOrder.of(rs) to DefaultTaskStatus.create(
              rs.getString("phase"),
              rs.getString("status"),
              TaskState.valueOf(rs.getString("state"))
            )
          )
        }
        rs.getString("manifest") != null -> {
          if (!taskOutputs.containsKey(rs.getString("task_id"))) {
            taskOutputs[rs.getString("task_id")] = mutableListOf()
          }
          taskOutputs[rs.getString("task_id")]!!.add(
            RowOrder.of(rs) to TaskDisplayOutput(
              rs.getString("manifest"),
              rs.getString("phase"),
              rs.getString("std_out"),
              rs.getString("std_error")
            )
          )
        }
      }
    }

    return tasks.values.map { task ->
      task.hydrateResultObjects(results.inOrder(task.id))
      task.hydrateHistory(history.inOrder(task.id))
      task.hydrateTaskOutputs(taskOutputs.inOrder(task.id))
      task
    }
  }

  private fun <T> Map<String, List<Pair<RowOrder, T>>>.inOrder(taskId: String): MutableList<T> =
    get(taskId).orEmpty().sortedBy { it.first }.map { it.second }.toMutableList()

  /**
   * Where a task's state, result or output row belongs in its history: rows written before the `seq` column
   * existed come first, in their original `(created_at, id)` order, then everything else by `seq`.
   */
  private data class RowOrder(val seq: Long?, val createdAt: Long?, val rowId: String?) : Comparable<RowOrder> {
    override fun compareTo(other: RowOrder): Int = ORDER.compare(this, other)

    companion object {
      private val ORDER = compareBy<RowOrder>({ it.seq != null }, { it.seq }, { it.createdAt }, { it.rowId })

      fun of(rs: ResultSet) = RowOrder(
        (rs.getObject("seq") as Number?)?.toLong(),
        (rs.getObject("sort_created_at") as Number?)?.toLong(),
        rs.getString("row_id")
      )
    }
  }

  private fun sagaIds(sagaIdsValue: String?): MutableSet<SagaId> {
    if (sagaIdsValue == null) {
      return mutableSetOf()
    }
    return mapper.readValue(sagaIdsValue, SAGA_IDS_TYPE)
  }
}
