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
package com.netflix.spinnaker.orca.sql.pipeline.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import java.sql.ResultSet

/**
 * Converts a SQL [ResultSet] into an Execution.
 *
 * When retrieving an Execution from SQL, we lazily load its stages on-demand
 * in this mapper as well.
 */
class ExecutionMapper(
  private val mapper: ObjectMapper,
  private val stageBatchSize: Int
) {

  private val log = LoggerFactory.getLogger(javaClass)

  fun map(rs: ResultSet, context: DSLContext): Collection<Execution> {
    val results = mutableListOf<Execution>()
    val executionMap = mutableMapOf<String, Execution>()
    val legacyMap = mutableMapOf<String, String>()

    while (rs.next()) {
      mapper.readValue<Execution>(rs.getString("body"))
        .also {
          execution -> results.add(execution)

          if (rs.getString("id") != execution.id) {
            // Map legacyId executions to their current ULID
            legacyMap[execution.id] = rs.getString("id")
            executionMap[rs.getString("id")] = execution
          } else {
            executionMap[execution.id] = execution
          }
        }
    }

    if (results.isNotEmpty()) {
      val type = results[0].type

      results.chunked(stageBatchSize) { executions ->
        val executionIds: List<String> = executions.map {
          if (legacyMap.containsKey(it.id)) {
            legacyMap[it.id]!!
          } else {
            it.id
          }
        }

        context.selectExecutionStages(type, executionIds).let { stageResultSet ->
          while (stageResultSet.next()) {
            mapStage(stageResultSet, executionMap)
          }
        }

        executions.forEach { execution ->
          execution.stages.sortBy { it.refId }
        }
      }
    }

    return results
  }

  private fun mapStage(rs: ResultSet, executions: Map<String, Execution>) {
    val executionId = rs.getString("execution_id")
    executions.getValue(executionId)
      .stages
      .add(
        mapper.readValue<Stage>(rs.getString("body"))
          .apply {
            execution = executions.getValue(executionId)
          }
      )
  }
}
