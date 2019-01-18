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
  private val mapper: ObjectMapper
) {

  private val log = LoggerFactory.getLogger(javaClass)

  fun map(rs: ResultSet, context: DSLContext): Collection<Execution> {
    val results = mutableListOf<Execution>()

    while (rs.next()) {
      mapper.readValue<Execution>(rs.getString("body"))
        .also { execution ->
          context.selectExecutionStages(execution.type, rs.getString("id")).let { stageResultSet ->
            while (stageResultSet.next()) {
              mapStage(stageResultSet, execution)
            }
          }
          execution.stages.sortBy { it.refId }
        }
        .also {
          if (!results.any { r -> r.id == it.id }) {
            results.add(it)
          } else {
            log.warn("Duplicate execution for ${it.id} found in sql result")
          }
        }
    }

    return results
  }

  private fun mapStage(rs: ResultSet, execution: Execution) {
    execution.stages.add(mapper.readValue<Stage>(rs.getString("body")).apply {
      setExecution(execution)
    })
  }
}
