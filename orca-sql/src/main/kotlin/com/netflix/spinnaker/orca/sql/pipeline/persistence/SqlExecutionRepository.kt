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
import com.netflix.spinnaker.config.TransactionRetryProperties
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.ExecutionStatus.BUFFERED
import com.netflix.spinnaker.orca.ExecutionStatus.CANCELED
import com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.ExecutionStatus.PAUSED
import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.model.Execution.PausedDetails
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator.NATURAL
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator.REVERSE_BUILD_TIME
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
import org.jooq.impl.DSL.*
import org.slf4j.LoggerFactory
import rx.Observable
import java.lang.System.currentTimeMillis
import java.security.SecureRandom

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
  private val transactionRetryProperties: TransactionRetryProperties,
  private val batchReadSize: Int = 10
) : ExecutionRepository, ExecutionStatisticsRepository {
  companion object {
    val ulid = SpinULID(SecureRandom())
    internal val retrySupport = RetrySupport()
  }

  private val log = LoggerFactory.getLogger(javaClass)

  override fun store(execution: Execution) {
    jooq.transactional { storeExecutionInternal(it, execution, true) }
  }

  override fun storeStage(stage: Stage) {
    jooq.transactional { storeStageInternal(it, stage) }
  }

  override fun updateStageContext(stage: Stage) {
    storeStage(stage)
  }

  override fun removeStage(execution: Execution, stageId: String) {
    jooq.transactional {
      it.delete(execution.type.stagesTableName)
        .where(stageId.toWhereCondition()).execute()
    }
  }

  override fun addStage(stage: Stage) {
    if (stage.syntheticStageOwner == null || stage.parentStageId == null) {
      throw SyntheticStageRequired()
    }
    storeStage(stage)
  }

  override fun cancel(type: ExecutionType, id: String) {
    cancel(type, id, null, null)
  }

  override fun cancel(type: ExecutionType, id: String, user: String?, reason: String?) {
    jooq.transactional {
      selectExecution(it, type, id)
        ?.let { execution ->
          execution.isCanceled = true
          if (user != null) {
            execution.canceledBy = user
          }
          if (reason != null && reason.isNotEmpty()) {
            execution.cancellationReason = reason
          }
          if (execution.status == NOT_STARTED) {
            execution.status = CANCELED
          }
          storeExecutionInternal(it, execution)
        }
    }
  }

  override fun pause(type: ExecutionType, id: String, user: String?) {
    jooq.transactional {
      selectExecution(it, type, id)
        ?.let { execution ->
          if (execution.status != RUNNING) {
            throw UnpausablePipelineException("Unable to pause pipeline that is not RUNNING " +
              "(executionId: $id, currentStatus: ${execution.status})")
          }
          execution.status = PAUSED
          execution.paused = PausedDetails().apply {
            pausedBy = user
            pauseTime = currentTimeMillis()
          }
          storeExecutionInternal(it, execution)
        }
    }
  }

  override fun resume(type: ExecutionType, id: String, user: String?) {
    resume(type, id, user, false)
  }

  override fun resume(type: ExecutionType, id: String, user: String?, ignoreCurrentStatus: Boolean) {
    jooq.transactional {
      selectExecution(it, type, id)
        ?.let { execution ->
          if (!ignoreCurrentStatus && execution.status != PAUSED) {
            throw UnresumablePipelineException("Unable to resume pipeline that is not PAUSED " +
              "(executionId: $id, currentStatus: ${execution.status}")
          }
          execution.status = RUNNING
          execution.paused?.resumedBy = user
          execution.paused?.resumeTime = currentTimeMillis()
          storeExecutionInternal(it, execution)
        }
    }
  }

  override fun isCanceled(type: ExecutionType, id: String): Boolean {
    return jooq.fetchExists(
      jooq.selectFrom(type.tableName)
        .where(id.toWhereCondition())
        .and(field("canceled").eq(true))
    )
  }

  override fun updateStatus(type: ExecutionType, id: String, status: ExecutionStatus) {
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

  override fun delete(type: ExecutionType, id: String) {
    jooq.transactional {
      val correlationField = if (type == PIPELINE) "pipeline_id" else "orchestration_id"
      val (ulid, _) = mapLegacyId(it, type.tableName, id)
      it.delete(table("correlation_ids")).where(field(correlationField).eq(ulid)).execute()
      it.delete(type.stagesTableName).where(field("execution_id").eq(ulid)).execute()
      it.delete(type.tableName).where(field("id").eq(ulid)).execute()
    }
  }

  // TODO rz - Refactor to not use exceptions. So weird.
  override fun retrieve(type: ExecutionType, id: String) =
    selectExecution(jooq, type, id)
      ?: throw ExecutionNotFoundException("No $type found for $id")

  override fun retrieve(type: ExecutionType): Observable<Execution> =
    Observable.from(fetchExecutions { pageSize, cursor ->
      selectExecutions(type, pageSize, cursor)
    })

  override fun retrieve(type: ExecutionType, criteria: ExecutionCriteria): Observable<Execution> {
    return retrieve(type, criteria, null)
  }

  private fun retrieve(type: ExecutionType, criteria: ExecutionCriteria, partition: String?): Observable<Execution> {
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
            if (criteria.limit > 0) {
              limit(criteria.limit)
            } else {
              this
            }
          }
      })

    return Observable.from(select.fetchExecutions())
  }

  override fun retrievePipelinesForApplication(application: String): Observable<Execution> =
    Observable.from(fetchExecutions { pageSize, cursor ->
      selectExecutions(PIPELINE, pageSize, cursor) {
        it.where(field("application").eq(application))
      }
    })

  override fun retrievePipelinesForPipelineConfigId(
    pipelineConfigId: String,
    criteria: ExecutionCriteria
  ): Observable<Execution> {
    val select = jooq.selectExecutions(
      PIPELINE,
      conditions = {
        it.where(field("config_id").eq(pipelineConfigId))
          .statusIn(criteria.statuses)
      },
      seek = {
        it.orderBy(field("id").desc()).limit(criteria.limit)
      }
    )

    return Observable.from(select.fetchExecutions())
  }

  override fun retrieveOrchestrationsForApplication(
    application: String,
    criteria: ExecutionCriteria
  ): Observable<Execution> {
    return Observable.from(retrieveOrchestrationsForApplication(application, criteria, NATURAL))
  }

  override fun retrieveOrchestrationsForApplication(
    application: String,
    criteria: ExecutionCriteria,
    sorter: ExecutionComparator?
  ): MutableList<Execution> {
    return jooq.selectExecutions(
      ORCHESTRATION,
      conditions = {
        val where = it.where(field("application").eq(application))

        val startTime = criteria.startTimeCutoff
        if (startTime != null) {
          // This may look like a bug, but it isn't. Start time isn't always set (NOT_STARTED status). We
          // don't want to exclude Executions that haven't started, but we also want to still reduce the result set.
          where
            .and(field("build_time").greaterThan(startTime.toEpochMilli()))
            .statusIn(criteria.statuses)
        } else {
          where.statusIn(criteria.statuses)
        }
      },
      seek = {
        val ordered = when (sorter) {
          START_TIME_OR_ID -> it.orderBy(field("start_time").desc(), field("id").desc())
          REVERSE_BUILD_TIME -> it.orderBy(field("build_time").asc(), field("id").asc())
          else -> it.orderBy(field("id").desc())
        }

        ordered.offset((criteria.page - 1) * criteria.limit).limit(criteria.limit)
      }
    ).fetchExecutions().toMutableList()
  }

  // TODO rz - Refactor to not use exceptions
  // TODO rz - Refactor to allow different ExecutionTypes
  override fun retrieveOrchestrationForCorrelationId(correlationId: String): Execution {
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

  override fun retrieveBufferedExecutions(): MutableList<Execution> =
    ExecutionCriteria().setStatuses(BUFFERED)
      .let { criteria ->
        rx.Observable.merge(
          retrieve(ORCHESTRATION, criteria, partitionName),
          retrieve(PIPELINE, criteria, partitionName)
        ).toList().toBlocking().single()
      }

  override fun retrieveAllApplicationNames(type: ExecutionType?): List<String> {
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

  override fun retrieveAllApplicationNames(type: ExecutionType?, minExecutions: Int): List<String> {
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

  override fun countActiveExecutions(): ActiveExecutionsReport {
    val orchestrationsQuery = jooq.selectCount()
      .from(ORCHESTRATION.tableName)
      .where(field("status").eq(RUNNING.toString()))
      .asField<Int>("orchestrations")
    val pipelinesQuery = jooq.selectCount()
      .from(PIPELINE.tableName)
      .where(field("status").eq(RUNNING.toString()))
      .asField<Int>("pipelines")

    val record = jooq.select(orchestrationsQuery, pipelinesQuery).fetchOne()

    return ActiveExecutionsReport(
      record.get(0, Int::class.java),
      record.get(1, Int::class.java)
    )
  }

  override fun retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(pipelineConfigIds: MutableList<String>,
                                                                             buildTimeStartBoundary: Long,
                                                                             buildTimeEndBoundary: Long,
                                                                             limit: Int): Observable<Execution> {
    val select = jooq.selectExecutions(
      PIPELINE,
      conditions = {
        val inClause = "config_id IN (${pipelineConfigIds.joinToString(",") { "'$it'" }})"
        it.where(inClause)
          .and(field("build_time").gt(buildTimeStartBoundary))
          .and(field("build_time").lt(buildTimeEndBoundary))
      },
      seek = {
        it.orderBy(field("id").desc())
        it.limit(limit)
      }
    )
    return Observable.from(select.fetchExecutions())
  }

  override fun hasExecution(type: ExecutionType, id: String): Boolean {
    return jooq.selectCount()
      .from(type.tableName)
      .where(id.toWhereCondition())
      .fetchOne(count()) > 0
  }

  override fun retrieveAllExecutionIds(type: ExecutionType): MutableList<String> {
    return jooq.select(field("id")).from(type.tableName).fetch("id", String::class.java)
  }

  /**
   * Given an id, returns a tuple of [ULID, LegacyID]
   *
   * - When the provided id a ULID, returns: [id, null]
   * - When id is not a ULID but exists in the table, fetches ulid and returns: [fetched_ulid, id]
   * - When id is not a ULID and does not exist, creates new_ulid and returns: [new_ulid, id]
   */
  private fun mapLegacyId(ctx: DSLContext,
                          table: Table<Record>,
                          id: String,
                          timestamp: Long? = null): Pair<String, String?> {
    if (isULID(id)) return Pair(id, null)

    val ts = (timestamp ?: System.currentTimeMillis())
    val row = ctx.select(field("id"))
      .from(table)
      .where(field("legacy_id").eq(id))
      .limit(1)
      .fetchOne()
    val ulid = row?.let { it.getValue(field("id")) as String } ?: ulid.nextULID(ts)

    return Pair(ulid, id)
  }

  private fun storeExecutionInternal(ctx: DSLContext, execution: Execution, storeStages: Boolean = false) {
    // TODO rz - Nasty little hack here to save the execution without any of its stages.
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
        ctx.deleteFrom(stageTableName)
          .where(field("execution_id").eq(executionId))
          .apply {
            if (!stageIds.isEmpty()) {
              and(field("id").notIn(*stageIds))
                .and(field("legacy_id").notIn(*stageIds).or(field("legacy_id").isNull))
            }
          }.execute()

        stages.forEach { storeStageInternal(ctx, it, executionId) }
      }
    } finally {
      execution.stages.addAll(stages)
    }
  }

  private fun storeStageInternal(ctx: DSLContext, stage: Stage, executionId: String? = null) {
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

  private fun storeCorrelationIdInternal(ctx: DSLContext, execution: Execution) {
    if (execution.trigger.correlationId != null && !execution.status.isComplete) {
      val executionIdField = when (execution.type) {
        PIPELINE -> field("pipeline_id")
        ORCHESTRATION -> field("orchestration_id")
      }

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

  private fun upsert(ctx: DSLContext,
                     table: Table<Record>,
                     insertPairs: Map<Field<Any?>, Any?>,
                     updatePairs: Map<Field<Any>, Any?>,
                     updateId: String) {
    // MySQL & PG support upsert concepts. A nice little efficiency here, we
    // can avoid a network call if the dialect supports it, otherwise we need
    // to do a select for update first.
    // TODO rz - Unfortunately, this seems to come at the cost of try/catching
    // to fallback to the simpler behavior.
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

  private fun SelectConnectByStep<out Record>.statusIn(
    statuses: Collection<ExecutionStatus>
  ): SelectConnectByStep<out Record> {
    // jOOQ doesn't seem to play well with Kotlin here. Using the vararg interface for `in` doesn't construct the
    // SQL clause correctly, and I can't seem to get Kotlin to use the Collection<T> interface. We can manually
    // build this clause and it remain reasonably safe.
    if (statuses.isEmpty() || statuses.size == ExecutionStatus.values().size) {
      return this
    }
    val clause = "status IN (${statuses.joinToString(",") { "'$it'" }})"

    return run {
      when (this) {
        is SelectWhereStep<*> -> where(clause)
        is SelectConditionStep<*> -> and(clause)
        else -> this
      }
    }
  }

  private fun selectExecution(ctx: DSLContext,
                              type: ExecutionType,
                              id: String,
                              forUpdate: Boolean = false): Execution? {
    val select = ctx.selectExecution(type).where(id.toWhereCondition())
    if (forUpdate) {
      select.forUpdate()
    }
    return select.fetchExecution()
  }

  private fun selectExecutions(
    type: ExecutionType,
    limit: Int,
    cursor: String?,
    where: ((SelectJoinStep<Record>) -> SelectConditionStep<Record>)? = null
  ): Collection<Execution> {
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

  /**
   * Run the provided [fn] in a transaction.
   */
  private fun DSLContext.transactional(fn: (DSLContext) -> Unit) {
    retrySupport.retry({
      transaction { ctx ->
        fn(DSL.using(ctx))
      }
    }, transactionRetryProperties.maxRetries, transactionRetryProperties.backoffMs, false)
  }

  private fun DSLContext.selectExecutions(type: ExecutionType,
                                          fields: List<Field<Any>> = selectFields(),
                                          conditions: (SelectJoinStep<Record>) -> SelectConnectByStep<out Record>,
                                          seek: (SelectConnectByStep<out Record>) -> SelectForUpdateStep<out Record>) =
    select(fields)
      .from(type.tableName)
      .let { conditions(it) }
      .let { seek(it) }

  private fun DSLContext.selectExecution(type: ExecutionType, fields: List<Field<Any>> = selectFields()) =
    select(fields)
      .from(type.tableName)

  private fun selectFields() =
    listOf(field("id"), field("body"))

  private fun SelectForUpdateStep<out Record>.fetchExecutions() =
    ExecutionMapper(mapper).map(fetch().intoResultSet(), jooq)

  private fun SelectForUpdateStep<out Record>.fetchExecution() =
    fetchExecutions().firstOrNull()

  private fun fetchExecutions(nextPage: (Int, String?) -> Iterable<Execution>) =
    object : Iterable<Execution> {
      override fun iterator(): Iterator<Execution> =
        PagedIterator(batchReadSize, Execution::getId, nextPage)
    }

  class SyntheticStageRequired : IllegalArgumentException("Only synthetic stages can be inserted ad-hoc")
}
