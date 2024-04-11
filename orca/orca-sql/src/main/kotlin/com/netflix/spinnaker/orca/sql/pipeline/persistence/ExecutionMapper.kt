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
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.config.CompressionType
import com.netflix.spinnaker.config.ExecutionCompressionProperties
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.pipeline.persistence.ReplicationLagAwareRepository
import com.netflix.spinnaker.orca.pipeline.persistence.NoopReplicationLagAwareRepository
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.sql.ResultSet
import java.time.Instant

/**
 * Converts a SQL [ResultSet] into an Execution.
 *
 * When retrieving an Execution from SQL, we lazily load its stages on-demand
 * in this mapper as well.
 *
 * Optionally, the mapper can accept the requireLatestVersion and
 * replicationLagAwareRepository parameters, which work together as a unit.
 * If requireLatestVersion is true, the mapper will verify that all executions
 * and compressed executions comply with the requirements set by replicationLagAwareRepository.
 * If any part of the ResultSet fails to meet the requirements, the mapper returns an empty list of executions
 */
class ExecutionMapper(
  private val mapper: ObjectMapper,
  private val stageBatchSize: Int,
  private val compressionProperties: ExecutionCompressionProperties,
  private val pipelineRefEnabled: Boolean,
  private val requireLatestVersion: Boolean,
  private val replicationLagAwareRepository: ReplicationLagAwareRepository
) {
  constructor(mapper: ObjectMapper, stageBatchSize: Int, executionCompressionProperties: ExecutionCompressionProperties, pipelineRefEnabled: Boolean) :
    this(mapper, stageBatchSize, executionCompressionProperties, pipelineRefEnabled, false, NoopReplicationLagAwareRepository())
  private val log = LoggerFactory.getLogger(javaClass)

  /**
   * Conditionally decompresses a compressed execution body. if present, and provides the
   * execution body content as a string
   *
   * @param rs [ResultSet] to pull the body from
   *
   * @return the decompressed execution body content
   */
  @VisibleForTesting
  fun getDecompressedBody(rs: ResultSet): String {
    val body: String? = rs.getString("body")

    // If compression is disabled, rs doesn't contain compression-related
    // fields, so don't try to access them.
    return if (compressionProperties.enabled && body.isNullOrEmpty()) {
      val compressionType = CompressionType.valueOf(rs.getString("compression_type"))
      val compressedBody = rs.getBytes("compressed_body")
      compressionType.getInflator(compressedBody.inputStream())
        .bufferedReader(StandardCharsets.UTF_8)
        .use { it.readText() }
    } else {
      body ?: ""
    }
  }

  /**
   * Determines whether the execution represented by the ResultSet is an up-to-date version.
   * The ResultSet is expected to contain an `updated_at` column which represents the update time of the execution
   * and a `compressed_updated_at` column which represents the update time of the compressed
   * execution, if it exists.
   *
   * @param rs [ResultSet] representing the execution
   *
   * @return true if the execution is up-to-date, false otherwise
   */
  @VisibleForTesting
  fun isUpToDateVersion(rs: ResultSet, oldestAllowedUpdate: Instant): Boolean {
    if (rs.getLong("updated_at") < oldestAllowedUpdate.toEpochMilli()) {
      return false
    }
    // If the execution is compressed, then confirm that the compressed execution is up-to-date
    val body: String? = rs.getString("body")
    return if (compressionProperties.enabled && body.isNullOrEmpty()) {
      rs.getLong("compressed_updated_at") >= oldestAllowedUpdate.toEpochMilli()
    } else {
      true
    }
  }

  /**
   * Maps a given ResultSet to a Collection<PipelineExecution> and returns the result
   * in an [ExecutionMapperResult]. If the collection is empty,
   * [ExecutionMapperResultCode] communicates the reason and the caller can use this information
   * to perform additional processing as needed.
   *
   * Return an ExecutionMapperResult instead of throwing different exception classes because
   * this function can return an empty collection as part of its normal behavior. In other words,
   * returning an empty collection is not considered "truly exceptional" or "unexpected" behavior.
   */
  fun map(rs: ResultSet, context: DSLContext): ExecutionMapperResult {
    val results = mutableListOf<PipelineExecution>()
    val executionMap = mutableMapOf<String, PipelineExecution>()
    val legacyMap = mutableMapOf<String, String>()

    while (rs.next()) {
      if (requireLatestVersion) {
        val executionId = rs.getString("id")
        val oldestAllowedUpdate = replicationLagAwareRepository.getPipelineExecutionUpdate(executionId)
          ?: return ExecutionMapperResult(mutableListOf(), ExecutionMapperResultCode.MISSING_FROM_REPLICATION_LAG_REPOSITORY)
        if (!isUpToDateVersion(rs, oldestAllowedUpdate)) {
          return ExecutionMapperResult(mutableListOf(),ExecutionMapperResultCode.INVALID_VERSION)
        }
      }
      val body = getDecompressedBody(rs)
      if (body.isNotEmpty()) {
        mapper.readValue<PipelineExecution>(body)
          .also {
            execution ->
            convertPipelineRefTrigger(execution, context)
            execution.setSize(body.length.toLong())
            execution.updatedAt = rs.getLong("updated_at")
            results.add(execution)
            execution.partition = rs.getString("partition")

            if (rs.getString("id") != execution.id) {
              // Map legacyId executions to their current ULID
              legacyMap[execution.id] = rs.getString("id")
              executionMap[rs.getString("id")] = execution
            } else {
              executionMap[execution.id] = execution
            }
          }
      }
    }

    if (results.isNotEmpty()) {
      val type = results[0].type

      var invalidVersion = false
      var missingFromReplicationLagRepository = false
      results.chunked(stageBatchSize) { executions ->
        val executionIds: List<String> = executions.map {
          if (legacyMap.containsKey(it.id)) {
            legacyMap[it.id]!!
          } else {
            it.id
          }
        }

        context.selectExecutionStages(type, executionIds, compressionProperties).let { stageResultSet ->
          while (stageResultSet.next()) {
            if (requireLatestVersion) {
              val stageId = stageResultSet.getString("id")
              val oldestAllowedUpdate = replicationLagAwareRepository.getStageExecutionUpdate(stageId)
              if (oldestAllowedUpdate == null) {
                missingFromReplicationLagRepository = true
                return@chunked
              }
              if (!isUpToDateVersion(stageResultSet, oldestAllowedUpdate)) {
                invalidVersion = true
                return@chunked
              }
            }
            mapStage(stageResultSet, executionMap)
          }
        }

        if (requireLatestVersion) {
          executions.forEach { execution ->
            val expectedNumberOfStages = replicationLagAwareRepository.getPipelineExecutionNumStages(execution.id)
            if (expectedNumberOfStages == null) {
              missingFromReplicationLagRepository = true
              return@chunked
            }
            if (execution.stages.size != expectedNumberOfStages) {
              invalidVersion = true
              return@chunked
            }
          }
        }

        executions.forEach { execution ->
          execution.stages.sortBy { it.refId }
        }
      }

      // A result where the execution is missing from the ReplicationLagAwareRepository has a higher
      // precedence than a result where the version is invalid since MISSING_FROM_REPLICATION_LAG_REPOSITORY
      // is a more specific case of INVALID_VERSION
      if (missingFromReplicationLagRepository) {
        return ExecutionMapperResult(mutableListOf(), ExecutionMapperResultCode.MISSING_FROM_REPLICATION_LAG_REPOSITORY)
      }
      if (invalidVersion) {
        return ExecutionMapperResult(mutableListOf(), ExecutionMapperResultCode.INVALID_VERSION)
      }
    }

    return if (results.isNotEmpty()) {
      ExecutionMapperResult(results, ExecutionMapperResultCode.SUCCESS)
    } else {
      ExecutionMapperResult(results, ExecutionMapperResultCode.NOT_FOUND)
    }
  }

  private fun mapStage(rs: ResultSet, executions: Map<String, PipelineExecution>) {
    val executionId = rs.getString("execution_id")
    val body = getDecompressedBody(rs)
    executions.getValue(executionId)
      .stages
      .add(
        mapper.readValue<StageExecution>(body)
          .apply {
            execution = executions.getValue(executionId)
            setSize(body.length.toLong())
            updatedAt = rs.getLong("updated_at")
          }
      )
  }

  @VisibleForTesting
  fun convertPipelineRefTrigger(execution: PipelineExecution, context: DSLContext) {
    val trigger = execution.trigger
    if (trigger is PipelineRefTrigger) {
      val parentExecution = fetchParentExecution(execution.type, trigger, context)

      if (parentExecution == null) {
        // If someone deletes the parent execution, we'll be unable to load the full, valid child pipeline. Rather than
        // throw an exception, we'll continue to load the execution with [PipelineRefTrigger] and let downstream
        // consumers throw exceptions if they need to. We don't want to throw here as it would break pipeline list
        // operations, etc.
        log.warn("Attempted to load parent execution for '${execution.id}', but it no longer exists: ${trigger.parentExecutionId}")
        return
      }

      execution.trigger = trigger.toPipelineTrigger(parentExecution)
    }
  }

  @VisibleForTesting
  fun fetchParentExecution(type: ExecutionType, trigger: PipelineRefTrigger, context: DSLContext): PipelineExecution? {
    return context
      .selectExecution(type, compressionProperties)
      .where(field("id").eq(trigger.parentExecutionId))
      .fetchExecutions(mapper, 200, compressionProperties, context, pipelineRefEnabled)
      .firstOrNull()
  }
}
