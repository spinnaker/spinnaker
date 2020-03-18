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
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.exceptions.ConfigurationException
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.routing.withPool
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.BUFFERED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.CANCELED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.PAUSED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.interlink.Interlink
import com.netflix.spinnaker.orca.interlink.events.PauseInterlinkEvent
import com.netflix.spinnaker.orca.interlink.events.CancelInterlinkEvent
import com.netflix.spinnaker.orca.interlink.events.DeleteInterlinkEvent
import com.netflix.spinnaker.orca.interlink.events.InterlinkEvent
import com.netflix.spinnaker.orca.interlink.events.ResumeInterlinkEvent
import com.netflix.spinnaker.orca.interlink.events.PatchStageInterlinkEvent
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator.BUILD_TIME_DESC
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator.NATURAL_ASC
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator.START_TIME_OR_ID
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import com.netflix.spinnaker.orca.pipeline.persistence.UnpausablePipelineException
import com.netflix.spinnaker.orca.pipeline.persistence.UnresumablePipelineException
import de.huxhorn.sulky.ulid.SpinULID
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.SelectConditionStep
import org.jooq.SelectConnectByStep
import org.jooq.SelectForUpdateStep
import org.jooq.SelectJoinStep
import org.jooq.SelectWhereStep
import org.jooq.Table
import org.jooq.exception.SQLDialectNotSupportedException
import org.jooq.impl.DSL
import org.jooq.impl.DSL.count
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.name
import org.jooq.impl.DSL.table
import org.jooq.impl.DSL.value
import org.slf4j.LoggerFactory
import rx.Observable
import java.lang.System.currentTimeMillis
import java.security.SecureRandom
import org.jooq.exception.TooManyRowsException

/**
 * A generic SQL [ExecutionRepository].
 *
 * There is a small amount of MySQL/PostgreSQL specific commands inside, but
 * all of which safely fallback to common SQL if the dialect does not match.
 */
class SqlExecutionRepository(
  private val partitionName: String?,
  private val jooq: DSLContext,
  private val mapper: ObjectMapper,
  private val retryProperties: RetryProperties,
  private val batchReadSize: Int = 10,
  private val stageReadSize: Int = 200,
  private val poolName: String = "default",
  private val interlink: Interlink? = null
) : ExecutionRepository, ExecutionStatisticsRepository {
  companion object {
    val ulid = SpinULID(SecureRandom())
    internal val retrySupport = RetrySupport()
  }

  private val log = LoggerFactory.getLogger(javaClass)

  init {
    log.info("Creating SqlExecutionRepository with partition=$partitionName and pool=$poolName")

    try {
      withPool(poolName) {
        jooq.transactional {
          val record: Record? = jooq.fetchOne(table("partition_name"))

          if (record == null) {
            if (partitionName != null) {
              jooq
                .insertInto(table("partition_name"))
                .values(1, partitionName)
                .execute()
            }
          } else {
            val dbPartitionName = record.get("name")?.toString()

            if (partitionName != dbPartitionName) {
              throw ConfigurationException("Invalid configuration detected: Can't change partition name to $partitionName on the database once it has been set to $dbPartitionName")
            }
          }
        }
      }
    } catch (e: TooManyRowsException) {
      throw SystemException("The partition_name table should have zero or one rows but multiple rows were found", e)
    }
  }

  override fun store(execution: PipelineExecution) {
    withPool(poolName) {
      jooq.transactional { storeExecutionInternal(it, execution, true) }
    }
  }

  override fun storeStage(stage: StageExecution) {
    doForeignAware(PatchStageInterlinkEvent(stage.execution.type, stage.execution.id, stage.id, mapper.writeValueAsString(stage))) {
      _, dslContext ->
      jooq.transactional { storeStageInternal(dslContext, stage) }
    }
  }

  override fun updateStageContext(stage: StageExecution) {
    storeStage(stage)
  }

  override fun removeStage(execution: PipelineExecution, stageId: String) {
    validateHandledPartitionOrThrow(execution)

    withPool(poolName) {
      jooq.transactional {
        it.delete(execution.type.stagesTableName)
          .where(stageId.toWhereCondition()).execute()
      }
    }
  }

  override fun addStage(stage: StageExecution) {
    if (stage.syntheticStageOwner == null || stage.parentStageId == null) {
      throw SyntheticStageRequired()
    }
    storeStage(stage)
  }

  override fun cancel(type: ExecutionType, id: String) {
    cancel(type, id, null, null)
  }

  fun doForeignAware(
    event: InterlinkEvent,
    block: (execution: PipelineExecution, dslContext: DSLContext) -> Unit
  ) {
    withPool(poolName) {
      jooq.transactional { dslContext ->
        selectExecution(dslContext, event.executionType, event.executionId)
          ?.let { execution ->
            if (isForeign(execution)) {
              interlink?.publish(event.withPartition(execution.partition))
                ?: throw ForeignExecutionException(event.executionId, execution.partition, partitionName)
            } else {
              block(execution, dslContext)
            }
          }
      }
    }
  }

  override fun cancel(type: ExecutionType, id: String, user: String?, reason: String?) {
    doForeignAware(CancelInterlinkEvent(type, id, user, reason)) {
      execution: PipelineExecution, dslContext: DSLContext ->
      execution.isCanceled = true
      if (user != null) {
        execution.canceledBy = user
      }
      if (reason != null && reason.isNotEmpty()) {
        execution.cancellationReason = reason
      }
      if (execution.status == NOT_STARTED) {
        execution.status = ExecutionStatus.CANCELED
      }

      storeExecutionInternal(dslContext, execution)
    }
  }

  override fun pause(type: ExecutionType, id: String, user: String?) {
    doForeignAware(PauseInterlinkEvent(type, id, user)) {
      execution, dslContext ->
      if (execution.status != RUNNING) {
        throw UnpausablePipelineException("Unable to pause pipeline that is not RUNNING " +
          "(executionId: ${execution.id}, currentStatus: ${execution.status})")
      }
      execution.status = PAUSED
      execution.paused = PipelineExecution.PausedDetails().apply {
        pausedBy = user
        pauseTime = currentTimeMillis()
      }

      storeExecutionInternal(dslContext, execution)
    }
  }

  override fun resume(type: ExecutionType, id: String, user: String?) {
    resume(type, id, user, false)
  }

  override fun resume(type: ExecutionType, id: String, user: String?, ignoreCurrentStatus: Boolean) {
    doForeignAware(ResumeInterlinkEvent(type, id, user, ignoreCurrentStatus)) {
      execution, dslContext ->
      if (!ignoreCurrentStatus && execution.status != PAUSED) {
        throw UnresumablePipelineException("Unable to resume pipeline that is not PAUSED " +
          "(executionId: ${execution.id}, currentStatus: ${execution.status}")
      }
      execution.status = RUNNING
      execution.paused?.resumedBy = user
      execution.paused?.resumeTime = currentTimeMillis()
      storeExecutionInternal(dslContext, execution)
    }
  }

  override fun isCanceled(type: ExecutionType, id: String): Boolean {
    withPool(poolName) {
      return jooq.fetchExists(
        jooq.selectFrom(type.tableName)
          .where(id.toWhereCondition())
          .and(field("canceled").eq(true))
      )
    }
  }

  override fun updateStatus(type: ExecutionType, id: String, status: ExecutionStatus) {
    // this is an internal operation, we don't expect to send interlink events to update the status of an execution
    validateHandledPartitionOrThrow(type, id)

    withPool(poolName) {
      jooq.transactional {
        selectExecution(it, type, id)
          ?.let { execution ->
            execution.status = status
            if (status == RUNNING) {
              execution.isCanceled = false
              execution.startTime = currentTimeMillis()
            } else if (status.isComplete && execution.startTime != null) {
              execution.endTime = currentTimeMillis()
            }
            storeExecutionInternal(it, execution)
          }
      }
    }
  }

  override fun delete(type: ExecutionType, id: String) {
    doForeignAware(DeleteInterlinkEvent(type, id)) {
      execution, dslContext ->
      val correlationField = if (type == PIPELINE) "pipeline_id" else "orchestration_id"
      val (ulid, _) = mapLegacyId(jooq, type.tableName, id)

      val correlationId = jooq.select(field("id")).from("correlation_ids")
        .where(field(correlationField).eq(ulid))
        .limit(1)
        .fetchOne()
        ?.into(String::class.java)
      val stageIds = jooq.select(field("id")).from(type.stagesTableName)
        .where(field("execution_id").eq(ulid))
        .fetch()
        ?.into(String::class.java)?.toTypedArray()

      if (correlationId != null) {
        dslContext.delete(table("correlation_ids")).where(field("id").eq(correlationId)).execute()
      }
      if (stageIds != null) {
        dslContext.delete(type.stagesTableName).where(field("id").`in`(*stageIds)).execute()
      }
      dslContext.delete(type.tableName).where(field("id").eq(ulid)).execute()
    }
  }

  // TODO rz - Refactor to not use exceptions. So weird.
  override fun retrieve(type: ExecutionType, id: String) =
    selectExecution(jooq, type, id)
      ?: throw ExecutionNotFoundException("No $type found for $id")

  override fun retrieve(type: ExecutionType): Observable<PipelineExecution> =
    Observable.from(fetchExecutions { pageSize, cursor ->
      selectExecutions(type, pageSize, cursor)
    })

  override fun retrieve(type: ExecutionType, criteria: ExecutionCriteria): Observable<PipelineExecution> {
    return retrieve(type, criteria, null)
  }

  private fun retrieve(type: ExecutionType, criteria: ExecutionCriteria, partition: String?): Observable<PipelineExecution> {
    withPool(poolName) {
      val select = jooq.selectExecutions(
        type,
        fields = selectFields() + field("status"),
        conditions = {
          if (partition.isNullOrEmpty()) {
            it.statusIn(criteria.statuses)
          } else {
            it.where(field(name("partition")).eq(partition))
              .statusIn(criteria.statuses)
          }
        },
        seek = {
          it.orderBy(field("id").desc())
            .run {
              if (criteria.pageSize > 0) {
                limit(criteria.pageSize)
              } else {
                this
              }
            }
        })

      return Observable.from(select.fetchExecutions())
    }
  }

  override fun retrievePipelinesForApplication(application: String): Observable<PipelineExecution> =
    withPool(poolName) {
      Observable.from(fetchExecutions { pageSize, cursor ->
        selectExecutions(PIPELINE, pageSize, cursor) {
          it.where(field("application").eq(application))
        }
      })
    }

  override fun retrievePipelinesForPipelineConfigId(
    pipelineConfigId: String,
    criteria: ExecutionCriteria
  ): Observable<PipelineExecution> {
    // When not filtering by status, provide an index hint to ensure use of `pipeline_config_id_idx` which
    // fully satisfies the where clause and order by. Without, some lookups by config_id matching thousands
    // of executions triggered costly full table scans.
    withPool(poolName) {
      val select = if (criteria.statuses.isEmpty() || criteria.statuses.size == ExecutionStatus.values().size) {
        jooq.selectExecutions(
          PIPELINE,
          usingIndex = "pipeline_config_id_idx",
          conditions = {
            it.where(field("config_id").eq(pipelineConfigId))
              .statusIn(criteria.statuses)
          },
          seek = {
            it.orderBy(field("id").desc()).limit(criteria.pageSize)
          }
        )
      } else {
        // When filtering by status, the above index hint isn't ideal. In this case, `pipeline_config_status_idx`
        // appears to be used reliably without hinting.
        jooq.selectExecutions(
          PIPELINE,
          conditions = {
            it.where(field("config_id").eq(pipelineConfigId))
              .statusIn(criteria.statuses)
          },
          seek = {
            it.orderBy(field("id").desc()).limit(criteria.pageSize)
          }
        )
      }

      return Observable.from(select.fetchExecutions())
    }
  }

  override fun retrieveOrchestrationsForApplication(
    application: String,
    criteria: ExecutionCriteria
  ): Observable<PipelineExecution> {
    return Observable.from(retrieveOrchestrationsForApplication(application, criteria, NATURAL_ASC))
  }

  override fun retrieveOrchestrationsForApplication(
    application: String,
    criteria: ExecutionCriteria,
    sorter: ExecutionComparator?
  ): MutableList<PipelineExecution> {
    withPool(poolName) {
      return jooq.selectExecutions(
        ORCHESTRATION,
        conditions = {
          val where = it.where(field("application").eq(application))

          val startTime = criteria.startTimeCutoff
          if (startTime != null) {
            where
              .and(
                field("start_time").greaterThan(startTime.toEpochMilli())
                  .or(field("start_time").isNull)
              )
              .statusIn(criteria.statuses)
          } else {
            where.statusIn(criteria.statuses)
          }
        },
        seek = {
          val ordered = when (sorter) {
            START_TIME_OR_ID -> it.orderBy(field("start_time").desc().nullsFirst(), field("id").desc())
            BUILD_TIME_DESC -> it.orderBy(field("build_time").asc(), field("id").asc())
            else -> it.orderBy(field("id").desc())
          }

          ordered.offset((criteria.page - 1) * criteria.pageSize).limit(criteria.pageSize)
        }
      ).fetchExecutions().toMutableList()
    }
  }

  override fun retrieveByCorrelationId(executionType: ExecutionType, correlationId: String) =
    when (executionType) {
      PIPELINE -> retrievePipelineForCorrelationId(correlationId)
      ORCHESTRATION -> retrieveOrchestrationForCorrelationId(correlationId)
    }

  override fun retrieveOrchestrationForCorrelationId(correlationId: String): PipelineExecution {
    withPool(poolName) {
      val execution = jooq.selectExecution(ORCHESTRATION)
        .where(field("id").eq(
          field(
            jooq.select(field("c.orchestration_id"))
              .from(table("correlation_ids").`as`("c"))
              .where(field("c.id").eq(correlationId))
              .limit(1)
          ) as Any
        ))
        .fetchExecution()

      if (execution != null) {
        if (!execution.status.isComplete) {
          return execution
        }
        jooq.transactional {
          it.deleteFrom(table("correlation_ids")).where(field("id").eq(correlationId)).execute()
        }
      }

      throw ExecutionNotFoundException("No Orchestration found for correlation ID $correlationId")
    }
  }

  override fun retrievePipelineForCorrelationId(correlationId: String): PipelineExecution {
    withPool(poolName) {
      val execution = jooq.selectExecution(PIPELINE)
        .where(field("id").eq(
          field(
            jooq.select(field("c.pipeline_id"))
              .from(table("correlation_ids").`as`("c"))
              .where(field("c.id").eq(correlationId))
              .limit(1)
          ) as Any
        ))
        .fetchExecution()

      if (execution != null) {
        if (!execution.status.isComplete) {
          return execution
        }
        jooq.transactional {
          it.deleteFrom(table("correlation_ids")).where(field("id").eq(correlationId)).execute()
        }
      }

      throw ExecutionNotFoundException("No Pipeline found for correlation ID $correlationId")
    }
  }

  override fun retrieveBufferedExecutions(): MutableList<PipelineExecution> =
    ExecutionCriteria().setStatuses(BUFFERED)
      .let { criteria ->
        rx.Observable.merge(
          retrieve(ORCHESTRATION, criteria, partitionName),
          retrieve(PIPELINE, criteria, partitionName)
        ).toList().toBlocking().single()
      }

  override fun retrieveAllApplicationNames(type: ExecutionType?): List<String> {
    withPool(poolName) {
      return if (type == null) {
        jooq.select(field("application"))
          .from(PIPELINE.tableName)
          .groupBy(field("application"))
          .unionAll(
            jooq.select(field("application"))
              .from(ORCHESTRATION.tableName)
              .groupBy(field("application"))
          )
          .fetch(0, String::class.java)
          .distinct()
      } else {
        jooq.select(field("application"))
          .from(type.tableName)
          .groupBy(field("application"))
          .fetch(0, String::class.java)
          .distinct()
      }
    }
  }

  override fun retrieveAllApplicationNames(type: ExecutionType?, minExecutions: Int): List<String> {
    withPool(poolName) {
      return if (type == null) {
        jooq.select(field("application"))
          .from(PIPELINE.tableName)
          .groupBy(field("application"))
          .having(count().ge(minExecutions))
          .unionAll(
            jooq.select(field("application"))
              .from(ORCHESTRATION.tableName)
              .groupBy(field("application"))
              .having(count().ge(minExecutions))
          )
          .fetch(0, String::class.java)
          .distinct()
      } else {
        jooq.select(field("application"))
          .from(type.tableName)
          .groupBy(field("application"))
          .having(count().ge(minExecutions))
          .fetch(0, String::class.java)
          .distinct()
      }
    }
  }

  override fun countActiveExecutions(): ActiveExecutionsReport {
    withPool(poolName) {
      val partitionPredicate = if (partitionName != null) field("`partition`").eq(partitionName) else value(1).eq(value(1))

      val orchestrationsQuery = jooq.selectCount()
        .from(ORCHESTRATION.tableName)
        .where(field("status").eq(RUNNING.toString()))
        .and(partitionPredicate)
        .asField<Int>("orchestrations")
      val pipelinesQuery = jooq.selectCount()
        .from(PIPELINE.tableName)
        .where(field("status").eq(RUNNING.toString()))
        .and(partitionPredicate)
        .asField<Int>("pipelines")

      val record = jooq.select(orchestrationsQuery, pipelinesQuery).fetchOne()

      return ActiveExecutionsReport(
        record.get(0, Int::class.java),
        record.get(1, Int::class.java)
      )
    }
  }

  override fun retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
    pipelineConfigIds: List<String>,
    buildTimeStartBoundary: Long,
    buildTimeEndBoundary: Long,
    executionCriteria: ExecutionCriteria
  ): List<PipelineExecution> {
    withPool(poolName) {
      val select = jooq.selectExecutions(
        PIPELINE,
        conditions = {
          var conditions = it.where(
            field("config_id").`in`(*pipelineConfigIds.toTypedArray())
              .and(field("build_time").gt(buildTimeStartBoundary))
              .and(field("build_time").lt(buildTimeEndBoundary))
          )

          if (executionCriteria.statuses.isNotEmpty()) {
            val statusStrings = executionCriteria.statuses.map { it.toString() }
            conditions = conditions.and(field("status").`in`(*statusStrings.toTypedArray()))
          }

          conditions
        },
        seek = {
          val seek = when (executionCriteria.sortType) {
            ExecutionComparator.BUILD_TIME_ASC -> it.orderBy(field("build_time").asc())
            ExecutionComparator.BUILD_TIME_DESC -> it.orderBy(field("build_time").desc())
            ExecutionComparator.START_TIME_OR_ID -> it.orderBy(field("start_time").desc())
            ExecutionComparator.NATURAL_ASC -> it.orderBy(field("id").desc())
            else -> it.orderBy(field("id").asc())
          }
          seek
            .limit(executionCriteria.pageSize)
            .offset((executionCriteria.page - 1) * executionCriteria.pageSize)
        }
      )

      return select.fetchExecutions().toList()
    }
  }

  override fun retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
    pipelineConfigIds: List<String>,
    buildTimeStartBoundary: Long,
    buildTimeEndBoundary: Long,
    executionCriteria: ExecutionCriteria
  ): List<PipelineExecution> {
    val allExecutions = mutableListOf<PipelineExecution>()
    var page = 1
    val pageSize = executionCriteria.pageSize
    var moreResults = true

    while (moreResults) {
      val results = retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
        pipelineConfigIds,
        buildTimeStartBoundary,
        buildTimeEndBoundary,
        executionCriteria.setPage(page)
      )
      moreResults = results.size >= pageSize
      page += 1

      allExecutions.addAll(results)
    }

    return allExecutions
  }

  override fun hasExecution(type: ExecutionType, id: String): Boolean {
    withPool(poolName) {
      return jooq.selectCount()
        .from(type.tableName)
        .where(id.toWhereCondition())
        .fetchOne(count()) > 0
    }
  }

  override fun retrieveAllExecutionIds(type: ExecutionType): MutableList<String> {
    withPool(poolName) {
      return jooq.select(field("id")).from(type.tableName).fetch("id", String::class.java)
    }
  }

  /**
   * Given an id, returns a tuple of [ULID, LegacyID]
   *
   * - When the provided id a ULID, returns: [id, null]
   * - When id is not a ULID but exists in the table, fetches ulid and returns: [fetched_ulid, id]
   * - When id is not a ULID and does not exist, creates new_ulid and returns: [new_ulid, id]
   */
  private fun mapLegacyId(
    ctx: DSLContext,
    table: Table<Record>,
    id: String,
    timestamp: Long? = null
  ): Pair<String, String?> {
    if (isULID(id)) return Pair(id, null)

    withPool(poolName) {
      val ts = (timestamp ?: System.currentTimeMillis())
      val row = ctx.select(field("id"))
        .from(table)
        .where(field("legacy_id").eq(id))
        .limit(1)
        .fetchOne()
      val ulid = row?.let { it.getValue(field("id")) as String } ?: ulid.nextULID(ts)

      return Pair(ulid, id)
    }
  }

  private fun storeExecutionInternal(ctx: DSLContext, execution: PipelineExecution, storeStages: Boolean = false) {
    validateHandledPartitionOrThrow(execution)

    val stages = execution.stages.toMutableList().toList()
    execution.stages.clear()

    try {
      val tableName = execution.type.tableName
      val stageTableName = execution.type.stagesTableName
      val status = execution.status.toString()
      val body = mapper.writeValueAsString(execution)

      val (executionId, legacyId) = mapLegacyId(ctx, tableName, execution.id, execution.startTime)

      val insertPairs = mapOf(
        field("id") to executionId,
        field("legacy_id") to legacyId,
        field(name("partition")) to partitionName,
        field("status") to status,
        field("application") to execution.application,
        field("build_time") to (execution.buildTime ?: currentTimeMillis()),
        field("start_time") to execution.startTime,
        field("canceled") to execution.isCanceled,
        field("updated_at") to currentTimeMillis(),
        field("body") to body
      )

      val updatePairs = mapOf(
        field("status") to status,
        field("body") to body,
        // won't have started on insert
        field("start_time") to execution.startTime,
        field("canceled") to execution.isCanceled,
        field("updated_at") to currentTimeMillis()
      )

      when (execution.type) {
        PIPELINE -> upsert(
          ctx,
          execution.type.tableName,
          insertPairs.plus(field("config_id") to execution.pipelineConfigId),
          updatePairs.plus(field("config_id") to execution.pipelineConfigId),
          executionId
        )
        ORCHESTRATION -> upsert(
          ctx,
          execution.type.tableName,
          insertPairs,
          updatePairs,
          executionId
        )
      }

      storeCorrelationIdInternal(ctx, execution)

      if (storeStages) {
        val stageIds = stages.map { it.id }.toTypedArray()

        // Remove stages that are no longer part of the execution. This
        // would happen while restarting a completed or paused execution
        // and synthetics need to be cleaned up.
        withPool(poolName) {
          ctx.deleteFrom(stageTableName)
            .where(field("execution_id").eq(executionId))
            .apply {
              if (!stageIds.isEmpty()) {
                and(field("id").notIn(*stageIds))
                  .and(field("legacy_id").notIn(*stageIds).or(field("legacy_id").isNull))
              }
            }.execute()
        }

        stages.forEach { storeStageInternal(ctx, it, executionId) }
      }
    } finally {
      execution.stages.addAll(stages)
    }
  }

  private fun storeStageInternal(ctx: DSLContext, stage: StageExecution, executionId: String? = null) {
    val stageTable = stage.execution.type.stagesTableName
    val table = stage.execution.type.tableName
    val body = mapper.writeValueAsString(stage)
    val buildTime = stage.execution.buildTime

    val executionUlid = executionId ?: mapLegacyId(ctx, table, stage.execution.id, buildTime).first
    val (stageId, legacyId) = mapLegacyId(ctx, stageTable, stage.id, buildTime)

    val insertPairs = mapOf(
      field("id") to stageId,
      field("legacy_id") to legacyId,
      field("execution_id") to executionUlid,
      field("status") to stage.status.toString(),
      field("updated_at") to currentTimeMillis(),
      field("body") to body
    )

    val updatePairs = mapOf(
      field("status") to stage.status.toString(),
      field("updated_at") to currentTimeMillis(),
      field("body") to body
    )

    upsert(ctx, stageTable, insertPairs, updatePairs, stage.id)
  }

  private fun storeCorrelationIdInternal(ctx: DSLContext, execution: PipelineExecution) {
    if (execution.trigger.correlationId != null && !execution.status.isComplete) {
      val executionIdField = when (execution.type) {
        PIPELINE -> field("pipeline_id")
        ORCHESTRATION -> field("orchestration_id")
      }

      withPool(poolName) {
        val exists = ctx.fetchExists(
          ctx.select()
            .from("correlation_ids")
            .where(field("id").eq(execution.trigger.correlationId))
            .and(executionIdField.eq(execution.id))
        )
        if (!exists) {
          ctx.insertInto(table("correlation_ids"))
            .columns(field("id"), executionIdField)
            .values(execution.trigger.correlationId, execution.id)
            .execute()
        }
      }
    }
  }

  private fun upsert(
    ctx: DSLContext,
    table: Table<Record>,
    insertPairs: Map<Field<Any?>, Any?>,
    updatePairs: Map<Field<Any>, Any?>,
    updateId: String
  ) {
    // MySQL & PG support upsert concepts. A nice little efficiency here, we
    // can avoid a network call if the dialect supports it, otherwise we need
    // to do a select for update first.
    // TODO rz - Unfortunately, this seems to come at the cost of try/catching
    // to fallback to the simpler behavior.
    withPool(poolName) {
      try {
        ctx.insertInto(table, *insertPairs.keys.toTypedArray())
          .values(insertPairs.values)
          .onDuplicateKeyUpdate()
          .set(updatePairs)
          .execute()
      } catch (e: SQLDialectNotSupportedException) {
        log.debug("Falling back to primitive upsert logic: ${e.message}")
        val exists = ctx.fetchExists(ctx.select().from(table).where(field("id").eq(updateId)).forUpdate())
        if (exists) {
          ctx.update(table).set(updatePairs).where(field("id").eq(updateId)).execute()
        } else {
          ctx.insertInto(table).columns(insertPairs.keys).values(insertPairs.values).execute()
        }
      }
    }
  }

  private fun SelectConnectByStep<out Record>.statusIn(
    statuses: Collection<ExecutionStatus>
  ): SelectConnectByStep<out Record> {
    if (statuses.isEmpty() || statuses.size == ExecutionStatus.values().size) {
      return this
    }

    var statusStrings = statuses.map { it.toString() }
    val clause = DSL.field("status").`in`(*statusStrings.toTypedArray())

    return run {
      when (this) {
        is SelectWhereStep<*> -> where(clause)
        is SelectConditionStep<*> -> and(clause)
        else -> this
      }
    }
  }

  private fun selectExecution(
    ctx: DSLContext,
    type: ExecutionType,
    id: String,
    forUpdate: Boolean = false
  ): PipelineExecution? {
    withPool(poolName) {
      val select = ctx.selectExecution(type).where(id.toWhereCondition())
      if (forUpdate) {
        select.forUpdate()
      }
      return select.fetchExecution()
    }
  }

  private fun selectExecutions(
    type: ExecutionType,
    limit: Int,
    cursor: String?,
    where: ((SelectJoinStep<Record>) -> SelectConditionStep<Record>)? = null
  ): Collection<PipelineExecution> {
    withPool(poolName) {
      val select = jooq.selectExecutions(
        type,
        conditions = {
          if (cursor == null) {
            it.where("1=1")
          } else {
            if (where == null) {
              it.where(field("id").gt(cursor))
            } else {
              where(it).and(field("id").gt(cursor))
            }
          }
        },
        seek = {
          it.orderBy(field("id").desc())
            .limit(limit)
        }
      )

      return select.fetchExecutions()
    }
  }

  /**
   * Run the provided [fn] in a transaction.
   */
  private fun DSLContext.transactional(fn: (DSLContext) -> Unit) {
    retrySupport.retry({
      transaction { ctx ->
        fn(DSL.using(ctx))
      }
    }, retryProperties.maxRetries, retryProperties.backoffMs, false)
  }

  private fun DSLContext.selectExecutions(
    type: ExecutionType,
    fields: List<Field<Any>> = selectFields(),
    conditions: (SelectJoinStep<Record>) -> SelectConnectByStep<out Record>,
    seek: (SelectConnectByStep<out Record>) -> SelectForUpdateStep<out Record>
  ) =
    select(fields)
      .from(type.tableName)
      .let { conditions(it) }
      .let { seek(it) }

  private fun DSLContext.selectExecutions(
    type: ExecutionType,
    fields: List<Field<Any>> = selectFields(),
    usingIndex: String,
    conditions: (SelectJoinStep<Record>) -> SelectConnectByStep<out Record>,
    seek: (SelectConnectByStep<out Record>) -> SelectForUpdateStep<out Record>
  ) =
    select(fields)
      .from(type.tableName.forceIndex(usingIndex))
      .let { conditions(it) }
      .let { seek(it) }

  private fun DSLContext.selectExecution(type: ExecutionType, fields: List<Field<Any>> = selectFields()) =
    select(fields)
      .from(type.tableName)

  private fun selectFields() =
    listOf(field("id"), field("body"), field("`partition`"))

  private fun SelectForUpdateStep<out Record>.fetchExecutions() =
    ExecutionMapper(mapper, stageReadSize).map(fetch().intoResultSet(), jooq)

  private fun SelectForUpdateStep<out Record>.fetchExecution() =
    fetchExecutions().firstOrNull()

  private fun fetchExecutions(nextPage: (Int, String?) -> Iterable<PipelineExecution>) =
    object : Iterable<PipelineExecution> {
      override fun iterator(): Iterator<PipelineExecution> =
        PagedIterator(batchReadSize, PipelineExecution::getId, nextPage)
    }

  private fun isForeign(execution: PipelineExecution, shouldThrow: Boolean = false): Boolean {
    val partition = execution.partition
    val foreign = !handlesPartition(partition)
    if (foreign && shouldThrow) {
      throw ForeignExecutionException(execution.id, partition, partitionName)
    }
    return foreign
  }

  private fun isForeign(executionType: ExecutionType, id: String, shouldThrow: Boolean = false): Boolean {
    // Short circuit if we are handling all partitions
    if (partitionName == null) {
      return false
    }

    return try {
      val execution = retrieve(executionType, id)
      isForeign(execution)
    } catch (_: ExecutionNotFoundException) {
      // Execution not found, we can proceed since the rest is likely a noop anyway
      false
    }
  }

  override fun getPartition(): String? {
    return partitionName
  }

  private fun validateHandledPartitionOrThrow(executionType: ExecutionType, id: String): Boolean =
    isForeign(executionType, id, true)

  private fun validateHandledPartitionOrThrow(execution: PipelineExecution): Boolean =
    isForeign(execution, true)

  class SyntheticStageRequired : IllegalArgumentException("Only synthetic stages can be inserted ad-hoc")
}
