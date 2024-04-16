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
import com.google.common.annotations.VisibleForTesting
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.ExecutionCompressionProperties
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.exceptions.ConfigurationException
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.routing.withPool
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.BUFFERED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.CANCELED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.PAUSED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger
import com.netflix.spinnaker.orca.api.pipeline.persistence.ExecutionRepositoryListener
import com.netflix.spinnaker.orca.interlink.Interlink
import com.netflix.spinnaker.orca.interlink.events.CancelInterlinkEvent
import com.netflix.spinnaker.orca.interlink.events.DeleteInterlinkEvent
import com.netflix.spinnaker.orca.interlink.events.InterlinkEvent
import com.netflix.spinnaker.orca.interlink.events.PatchStageInterlinkEvent
import com.netflix.spinnaker.orca.interlink.events.PauseInterlinkEvent
import com.netflix.spinnaker.orca.interlink.events.RestartStageInterlinkEvent
import com.netflix.spinnaker.orca.interlink.events.ResumeInterlinkEvent
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator.BUILD_TIME_DESC
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator.NATURAL_ASC
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator.START_TIME_OR_ID
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import com.netflix.spinnaker.orca.pipeline.persistence.ReplicationLagAwareRepository
import com.netflix.spinnaker.orca.pipeline.persistence.UnpausablePipelineException
import com.netflix.spinnaker.orca.pipeline.persistence.UnresumablePipelineException
import de.huxhorn.sulky.ulid.SpinULID
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import java.time.Duration
import org.jooq.DSLContext
import org.jooq.DatePart
import org.jooq.Field
import org.jooq.Record
import org.jooq.SQLDialect
import org.jooq.SelectConditionStep
import org.jooq.SelectConnectByStep
import org.jooq.SelectForUpdateStep
import org.jooq.SelectJoinStep
import org.jooq.SelectWhereStep
import org.jooq.Table
import org.jooq.exception.SQLDialectNotSupportedException
import org.jooq.exception.TooManyRowsException
import org.jooq.impl.DSL
import org.jooq.impl.DSL.count
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.name
import org.jooq.impl.DSL.now
import org.jooq.impl.DSL.table
import org.jooq.impl.DSL.timestampSub
import org.jooq.impl.DSL.value
import org.slf4j.LoggerFactory
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource
import rx.Observable
import java.io.ByteArrayOutputStream
import java.lang.System.currentTimeMillis
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Instant
import java.util.Optional
import java.util.stream.Collectors.toList
import javax.sql.DataSource
import kotlin.collections.Collection
import kotlin.collections.Iterable
import kotlin.collections.Iterator
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.chunked
import kotlin.collections.distinct
import kotlin.collections.firstOrNull
import kotlin.collections.forEach
import kotlin.collections.isEmpty
import kotlin.collections.isNotEmpty
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mapOf
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.plus
import kotlin.collections.set
import kotlin.collections.toList
import kotlin.collections.toMutableList
import kotlin.collections.toMutableMap
import kotlin.collections.toTypedArray

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
  internal var readPoolName: String = "read", /* internal for testing */
  private val interlink: Interlink? = null,
  private val executionRepositoryListeners: Collection<ExecutionRepositoryListener> = emptyList(),
  private val compressionProperties: ExecutionCompressionProperties,
  private val pipelineRefEnabled: Boolean,
  private val dataSource: DataSource,
  private val replicationLagAwareRepository: Optional<ReplicationLagAwareRepository>,
  private val registry: Registry
) : ExecutionRepository, ExecutionStatisticsRepository {
  companion object {
    val ulid = SpinULID(SecureRandom())
    internal val retrySupport = RetrySupport()
  }

  private val log = LoggerFactory.getLogger(javaClass)
  private val readPoolRetrieveSucceededId = registry.createId("executionRepository.sql.readPool.retrieveSucceeded")
  private val readPoolRetrieveFailedId = registry.createId("executionRepository.sql.readPool.retrieveFailed")
  private val readPoolRetrieveTotalId = registry.createId("executionRepository.sql.readPool.retrieveTotalAttempts")
  private val readPoolRetryRegistry = initializeReadPoolRetryRegistry(retryProperties)

  init {
    // If there's no read pool configured, fall back to the default pool
    if ((dataSource !is AbstractRoutingDataSource)
      || (dataSource.resolvedDataSources[readPoolName] == null)) {
      readPoolName = poolName
    }

    log.info("Creating SqlExecutionRepository with partition=$partitionName, pool=$poolName, readPool=$readPoolName")

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

  private fun initializeReadPoolRetryRegistry(retryProperties: RetryProperties): RetryRegistry {
    val exponentialMultiplier = 2.0
    val jitter = 0.2
    val retryInterval = IntervalFunction.ofExponentialRandomBackoff(retryProperties.backoffMs, exponentialMultiplier, jitter)
    val retryConfig = RetryConfig.custom<ExecutionMapperResult>()
      .maxAttempts(retryProperties.maxRetries)
      .intervalFunction(retryInterval)
      .retryOnResult { result ->
        result.resultCode == ExecutionMapperResultCode.NOT_FOUND ||
          result.resultCode == ExecutionMapperResultCode.INVALID_VERSION
      }
      .build()
    return RetryRegistry.of(retryConfig)
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
    // removeStage can be called multiple times in succession, making execution.stages.size an inaccurate
    // indicator of the actual number of stages that belong to an execution. Therefore, query the repository instead
    replicationLagAwareRepository.ifPresent { it ->
      val numStages = it.getPipelineExecutionNumStages(execution.id)
      if (numStages != null) {
        it.putPipelineExecutionNumStages(execution.id, numStages - 1)
      } else {
        it.putPipelineExecutionNumStages(execution.id, execution.stages.size - 1)
      }
    }
    withPool(poolName) {
      jooq.delete(execution.type.stagesTableName)
        .where(stageId.toWhereCondition()).execute()
    }
  }

  override fun addStage(stage: StageExecution) {
    if (stage.syntheticStageOwner == null || stage.parentStageId == null) {
      throw SyntheticStageRequired()
    }
    // addStage can be called multiple times in succession, making stage.execution.stages.size an inaccurate
    // indicator of the actual number of stages that belong to an execution. Therefore, query the repository instead
    val pipelineExecutionId = stage.execution.id
    replicationLagAwareRepository.ifPresent { it ->
      val numStages = it.getPipelineExecutionNumStages(pipelineExecutionId)
      if (numStages != null) {
        it.putPipelineExecutionNumStages(pipelineExecutionId, numStages + 1)
      } else {
        it.putPipelineExecutionNumStages(pipelineExecutionId, stage.execution.stages.size + 1)
      }
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
      } else if (!execution.status.isComplete
        && execution.stages.all { it.status.isComplete || it.status == NOT_STARTED }) {
        // In some cases, a race condition could occur between a pipeline cancellation and completion.
        // This could cause the pipeline status to be incorrectly updated to RUNNING even when there are
        // no more stages to run, resulting in the pipeline remaining in the RUNNING state indefinitely.
        // Explicitly setting the status to CANCELED here takes care of such race conditions.
        execution.status = CANCELED
      }
      storeExecutionInternal(dslContext, execution)
    }
  }

  override fun pause(type: ExecutionType, id: String, user: String?) {
    doForeignAware(PauseInterlinkEvent(type, id, user)) {
      execution, dslContext ->
      if (execution.status != RUNNING) {
        throw UnpausablePipelineException(
          "Unable to pause pipeline that is not RUNNING " +
            "(executionId: ${execution.id}, currentStatus: ${execution.status})"
        )
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
        throw UnresumablePipelineException(
          "Unable to resume pipeline that is not PAUSED " +
            "(executionId: ${execution.id}, currentStatus: ${execution.status}"
        )
      }
      execution.status = RUNNING
      execution.paused?.resumedBy = user
      execution.paused?.resumeTime = currentTimeMillis()
      storeExecutionInternal(dslContext, execution)
    }
  }

  override fun isCanceled(type: ExecutionType, id: String): Boolean {
    withPool(readPoolName) {
      return jooq.fetchExists(
        jooq.selectFrom(type.tableName)
          .where(id.toWhereCondition())
          .and(field("canceled").eq(true))
      )
    }
  }

  override fun restartStage(executionId: String, stageId: String) {
    doForeignAware(RestartStageInterlinkEvent(PIPELINE, executionId, stageId)) {
      _, _ -> log.debug("restartStage is a no-op for local executions")
    }
  }

  override fun updateStatus(execution: PipelineExecution) {
    withPool(poolName) {
      jooq.transactional {
        storeExecutionInternal(it, execution)
      }
    }
  }

  override fun updateStatus(type: ExecutionType, id: String, status: ExecutionStatus) {
    withPool(poolName) {
      jooq.transactional {
        selectExecution(it, type, id)
          ?.let { execution ->
            execution.updateStatus(status)
            updateStatus(execution)
          }
      }
    }
  }

  override fun delete(type: ExecutionType, id: String) {
    doForeignAware(DeleteInterlinkEvent(type, id)) {
      _, dslContext ->
      val (ulid, _) = mapLegacyId(dslContext, type.tableName, id)

      deleteInternal(dslContext, type, listOf(ulid))
    }

    cleanupOldDeletedExecutions()
  }

  /**
   * Deletes given executions
   * NOTE: this method explicitly does not check if the execution is foreign or not.
   */
  override fun delete(type: ExecutionType, idsToDelete: List<String>) {
    jooq.transactional { tx ->
      deleteInternal(tx, type, idsToDelete)
    }

    cleanupOldDeletedExecutions()
  }

  private fun deleteInternal(dslContext: DSLContext, type: ExecutionType, idsToDelete: List<String>) {
    val correlationField = when (type) {
      PIPELINE -> "pipeline_id"
      ORCHESTRATION -> "orchestration_id"
      else -> throw IllegalStateException("Unexpected field $type")
    }

    dslContext
      .delete(table("correlation_ids"))
      .where(field(correlationField).`in`(*idsToDelete.toTypedArray()))
      .execute()

    dslContext
      .delete(type.stagesTableName)
      .where(field("execution_id").`in`(*idsToDelete.toTypedArray()))
      .execute()

    dslContext
      .delete(type.tableName)
      .where(field("id").`in`(*idsToDelete.toTypedArray()))
      .execute()

    var insertQueryBuilder = dslContext
      .insertInto(table("deleted_executions"))
      .columns(field("execution_id"), field("execution_type"), field("deleted_at"))

    idsToDelete.forEach { id ->
      insertQueryBuilder = insertQueryBuilder
        .values(id, type.toString(), now())
    }

    insertQueryBuilder
      .execute()
  }

  private fun cleanupOldDeletedExecutions() {
    // Note: this runs as part of a delete operation but is not critical (best effort cleanup)
    // Hence it doesn't need to be in a transaction and we "eat" the exceptions here

    try {
      val idsToDelete = jooq
        .select(field("id"))
        .from(table("deleted_executions"))
        .where(field("deleted_at").lt(timestampSub(now(), 1, DatePart.DAY)))
        .fetch(field("id"), Int::class.java)

      // Perform chunked delete in the rare event that there are many executions to clean up
      idsToDelete
        .chunked(25)
        .forEach { chunk ->
          jooq
            .deleteFrom(table("deleted_executions"))
            .where(field("id").`in`(*chunk.toTypedArray()))
            .execute()
        }
    } catch (e: Exception) {
      log.error("Failed to cleanup some deleted_executions", e)
    }
  }

  // TODO rz - Refactor to not use exceptions. So weird.
  override fun retrieve(type: ExecutionType, id: String) =
    selectExecution(jooq, type, id)
      ?: throw ExecutionNotFoundException("No $type found for $id")

  override fun retrieve(type: ExecutionType, id: String, requireLatestVersion: Boolean) =
    selectExecution(jooq, type, id, requireLatestVersion)
      ?: throw ExecutionNotFoundException("No $type found for $id with requireLatestVersion: $requireLatestVersion")

  override fun retrieve(type: ExecutionType): Observable<PipelineExecution> =
    Observable.from(
      fetchExecutions { pageSize, cursor ->
        selectExecutions(type, pageSize, cursor)
      }
    )

  override fun retrieve(type: ExecutionType, criteria: ExecutionCriteria): Observable<PipelineExecution> {
    return retrieve(type, criteria, null)
  }

  private fun retrieve(type: ExecutionType, criteria: ExecutionCriteria, partition: String?): Observable<PipelineExecution> {
    withPool(readPoolName) {
      val select = jooq.selectExecutions(
        type,
        fields = selectFields(type) + field("status"),
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
        }
      )

      return Observable.from(select.fetchExecutions())
    }
  }

  override fun retrievePipelinesForApplication(application: String): Observable<PipelineExecution> =
    withPool(readPoolName) {
      Observable.from(
        fetchExecutions { pageSize, cursor ->
          selectExecutions(PIPELINE, pageSize, cursor) {
            it.where(field("application").eq(application))
          }
        }
      )
    }

  override fun retrievePipelineConfigIdsForApplication(application: String): List<String> =
    withPool(poolName) {
      return jooq.selectDistinct(field("config_id"))
        .from(PIPELINE.tableName)
        .where(field("application").eq(application))
        .fetch(0, String::class.java)
    }

  override fun retrievePipelineConfigIdsForApplicationWithCriteria(
    application: String,
    criteria: ExecutionCriteria
  ): List<String> {
    var baseQueryPredicate = field("application").eq(application)

    if (criteria.statuses.isNotEmpty() && criteria.statuses.size != ExecutionStatus.values().size) {
      val statusStrings = criteria.statuses.map { it.toString() }
      baseQueryPredicate = baseQueryPredicate
        .and(field("status").`in`(*statusStrings.toTypedArray()))
    }
    if (criteria.startTimeCutoff != null) {
      baseQueryPredicate = baseQueryPredicate
        .and(
          field("start_time").greaterThan(criteria.startTimeCutoff!!.toEpochMilli())
        )
    }

    withPool(readPoolName) {
      return jooq.selectDistinct(field("config_id"))
        .from(PIPELINE.tableName)
        .where(baseQueryPredicate)
        .groupBy(field("config_id"))
        .fetch(0, String::class.java)
    }
  }

  /**
   * this function supports the following ExecutionCriteria currently:
   * 'limit', a.k.a page size and
   * 'statuses'.
   *
   * It executes the following query to determine how many pipeline executions exist that satisfy the above
   * ExecutionCriteria. It then returns a list of all these execution ids.
   *
   * It does this by executing the following query:
   * - If the execution criteria does not contain any statuses:
   *    SELECT config_id, id
        FROM pipelines force index (`pipeline_application_idx`)
        WHERE application = "myapp"
        ORDER BY
        config_id;
   * - If the execution criteria contains statuses:
   *    SELECT config_id, id
        FROM pipelines force index (`pipeline_application_status_starttime_idx`)
        WHERE (
          application = "myapp" and
          status in ("status1", "status2)
        )
        ORDER BY
        config_id;

   * It then applies the limit execution criteria on the result set obtained above. We observed load issues in the db
   * when running a query where the limit was calculated in the query itself. Therefore, we are moving that logic to
   * the code below to ease the burden on the db in such circumstances.
   */
  override fun retrieveAndFilterPipelineExecutionIdsForApplication(
    application: String,
    pipelineConfigIds: List<String>,
    criteria: ExecutionCriteria
  ): List<String> {

    // baseQueryPredicate for the flow where there are no statuses in the execution criteria
    var baseQueryPredicate = field("application").eq(application)
      .and(field("config_id").`in`(*pipelineConfigIds.toTypedArray()))

    var table = if (jooq.dialect() == SQLDialect.MYSQL) PIPELINE.tableName.forceIndex("pipeline_application_idx")
    else PIPELINE.tableName
    // baseQueryPredicate for the flow with statuses
    if (criteria.statuses.isNotEmpty() && criteria.statuses.size != ExecutionStatus.values().size) {
      val statusStrings = criteria.statuses.map { it.toString() }
      baseQueryPredicate = baseQueryPredicate
        .and(field("status").`in`(*statusStrings.toTypedArray()))

      table = if (jooq.dialect() == SQLDialect.MYSQL) PIPELINE.tableName.forceIndex("pipeline_application_status_starttime_idx")
      else PIPELINE.tableName
    }

    val finalResult: MutableList<String> = mutableListOf()

    withPool(poolName) {
      val baseQuery = jooq.select(field("config_id"), field("id"))
        .from(table)
        .where(baseQueryPredicate)
        // ULIDs are ordered by time.  Assume id is a ULID since what gate's
        // PipelineController.triggerViaEcho provides.  Currently (4-nov-26), the
        // UI uses triggerViaEcho to invoke pipelines.  If there's no execution id
        // provided (e.g. via gate's PipelineController.trigger method), a
        // PipelineExecutionImpl constructor provides one.  If a non-ULID is
        // provided somehow, mapLegacyId in this class ensures id is a ULID.
        //
        // Order the result by id to retrieve the newest executions
         .orderBy(field("config_id"), field("id"))
         .fetch().intoGroups("config_id", "id")

        baseQuery.forEach {
          val count = it.value.size
          if (criteria.pageSize < count) {
            finalResult.addAll(it.value
              .stream()
              .skip((count - criteria.pageSize).toLong())
              .collect(toList()) as List<String>
            )
          } else {
            finalResult.addAll(it.value as List<String>)
          }
        }
    }
    return finalResult
  }

  /**
   * It executes the following query to get execution details for n executions at a time in a specific application
   *
   * SELECT id, body, compressed_body, compression_type, `partition`
       FROM pipelines
       left outer join
       pipelines_compressed_executions
       using (`id`)
       WHERE id in ('id1', 'id2', 'id3');
   *
   * it then gets all the stage information for all the executions returned from the above query.
   */
  override fun retrievePipelineExecutionDetailsForApplication(
    application: String,
    pipelineExecutions: List<String>,
    queryTimeoutSeconds: Int
  ): Collection<PipelineExecution> {
    withPool(readPoolName) {
      val selectFrom = jooq.select(selectFields(PIPELINE)).from(PIPELINE.tableName)
      if (compressionProperties.enabled) {
        selectFrom.leftOuterJoin(PIPELINE.tableName.compressedExecTable).using(field("id"))
      }
      val baseQuery = selectFrom
        .where(field("id").`in`(*pipelineExecutions.toTypedArray()))
        .queryTimeout(queryTimeoutSeconds) // add an explicit timeout so that the query doesn't run forever
        .fetch()

      log.debug("getting stage information for all the executions found so far")
      return ExecutionMapper(mapper, stageReadSize, compressionProperties, pipelineRefEnabled).map(baseQuery.intoResultSet(), jooq).executions
    }
  }

  override fun retrievePipelinesForPipelineConfigId(
    pipelineConfigId: String,
    criteria: ExecutionCriteria
  ): Observable<PipelineExecution> {
    // When not filtering by status, provide an index hint to ensure use of `pipeline_config_id_idx` which
    // fully satisfies the where clause and order by. Without, some lookups by config_id matching thousands
    // of executions triggered costly full table scans.
    withPool(readPoolName) {
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
    withPool(readPoolName) {
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
        .where(
          field("id").eq(
            field(
              jooq.select(field("c.orchestration_id"))
                .from(table("correlation_ids").`as`("c"))
                .where(field("c.id").eq(correlationId))
                .limit(1)
            ) as Any
          )
        )
        .fetchExecution()

      if (execution == null) {
        throw ExecutionNotFoundException("No Orchestration found for correlation ID $correlationId")
      }

      if (!execution.status.isComplete) {
        return execution
      }
    }

    // If we get here, there's an execution with the given correlation id, but
    // it's complete, so clean up the correlation_ids table.
    withPool(poolName) {
      jooq.deleteFrom(table("correlation_ids")).where(field("id").eq(correlationId)).execute()
    }

    // Treat a completed execution similar to not finding one at all.
    throw ExecutionNotFoundException("Complete Orchestration found for correlation ID $correlationId")
  }

  override fun retrievePipelineForCorrelationId(correlationId: String): PipelineExecution {
    withPool(poolName) {
      val execution = jooq.selectExecution(PIPELINE)
        .where(
          field("id").eq(
            field(
              jooq.select(field("c.pipeline_id"))
                .from(table("correlation_ids").`as`("c"))
                .where(field("c.id").eq(correlationId))
                .limit(1)
            ) as Any
          )
        )
        .fetchExecution()

      if (execution == null) {
        throw ExecutionNotFoundException("No Pipeline found for correlation ID $correlationId")
      }

      if (!execution.status.isComplete) {
        return execution
      }
    }

    // If we get here, there's an execution with the given correlation id, but
    // it's complete, so clean up the correlation_ids table.
    withPool(poolName) {
      jooq.deleteFrom(table("correlation_ids")).where(field("id").eq(correlationId)).execute()
    }

    throw ExecutionNotFoundException("Complete Pipeline found for correlation ID $correlationId")
  }

  override fun retrieveBufferedExecutions(): MutableList<PipelineExecution> =
    ExecutionCriteria()
      .setPageSize(100)
      .setStatuses(BUFFERED)
      .let { criteria ->
        rx.Observable.merge(
          retrieve(ORCHESTRATION, criteria, partitionName),
          retrieve(PIPELINE, criteria, partitionName)
        ).toList().toBlocking().single()
      }

  override fun retrieveAllApplicationNames(type: ExecutionType?): List<String> {
    withPool(readPoolName) {
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
    withPool(readPoolName) {
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
    withPool(readPoolName) {
      val partitionPredicate = if (partitionName != null) field(name("partition")).eq(partitionName) else value(1).eq(value(1))

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

      val record = jooq.select(orchestrationsQuery, pipelinesQuery).fetchSingle()

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
    withPool(readPoolName) {
      // The query here doesn't work with
      //
      // `pipelines_compressed_executions`.`updated_at` as `compressed_updated_at`
      //
      // from selectFields(PIPELINE) when compression is enabled.  The full error message is:
      //
      // org.jooq.exception.DataAccessException: SQL [select id, body, compressed_body, compression_type, `partition`, `pipelines`.`updated_at` as `updated_at`, `pipelines_compressed_executions`.`updated_at` as `compressed_updated_at` from pipelines join (select id, compressed_body, compression_type from pipelines left outer join pipelines_compressed_executions using (id) where (config_id in (?, ?) and build_time > ? and build_time < ?) order by build_time asc limit ? offset ?) as `alias_122785528` using (id) order by build_time asc]; Unknown column 'pipelines_compressed_executions.updated_at' in 'field list'
      // 	at app//org.jooq.impl.Tools.translate(Tools.java:2903)
      // 	at app//org.jooq.impl.DefaultExecuteContext.sqlException(DefaultExecuteContext.java:757)
      // 	at app//org.jooq.impl.AbstractQuery.execute(AbstractQuery.java:389)
      // 	at app//org.jooq.impl.AbstractResultQuery.fetch(AbstractResultQuery.java:337)
      // 	at app//org.jooq.impl.SelectImpl.fetch(SelectImpl.java:2880)
      // 	at app//com.netflix.spinnaker.orca.sql.pipeline.persistence.SqlExecutionRepository.fetchExecutions(SqlExecutionRepository.kt:1506)
      // 	at app//com.netflix.spinnaker.orca.sql.pipeline.persistence.SqlExecutionRepository.retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(SqlExecutionRepository.kt:920)
      // 	at app//com.netflix.spinnaker.orca.sql.pipeline.persistence.SqlExecutionRepository.retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(SqlExecutionRepository.kt:936)
      // 	at app//com.netflix.spinnaker.kork.telemetry.InstrumentedProxy.invoke(InstrumentedProxy.java:103)
      // 	at com.netflix.spinnaker.orca.sql.pipeline.persistence.SqlPipelineExecutionRepositorySpec.can retrieve ALL pipelines by configIds between build time boundaries(SqlPipelineExecutionRepositorySpec.groovy:665)
      // Caused by: java.sql.SQLSyntaxErrorException: Unknown column 'pipelines_compressed_executions.updated_at' in 'field list'
      // 	at com.mysql.cj.jdbc.exceptions.SQLError.createSQLException(SQLError.java:121)
      // 	at com.mysql.cj.jdbc.exceptions.SQLExceptionsMapping.translateException(SQLExceptionsMapping.java:122)
      // 	at com.mysql.cj.jdbc.ClientPreparedStatement.executeInternal(ClientPreparedStatement.java:916)
      // 	at com.mysql.cj.jdbc.ClientPreparedStatement.execute(ClientPreparedStatement.java:354)
      // 	at com.zaxxer.hikari.pool.ProxyPreparedStatement.execute(ProxyPreparedStatement.java:44)
      // 	at org.jooq.tools.jdbc.DefaultPreparedStatement.execute(DefaultPreparedStatement.java:214)
      // 	at org.jooq.impl.Tools.executeStatementAndGetFirstResultSet(Tools.java:4217)
      // 	at org.jooq.impl.AbstractResultQuery.execute(AbstractResultQuery.java:283)
      // 	at org.jooq.impl.AbstractQuery.execute(AbstractQuery.java:375)
      // 	... 7 more
      //
      //
      // So, since this is a query that uses the read pool already, and isn't concerned with replication lag, build the list of fields manually instead of calling selectFields(PIPELINE)
      val baseFields = if (compressionProperties.enabled)
        listOf(field("id"),
          field("body"),
          field("compressed_body"),
          field("compression_type"),
          field(name("partition")),
          field("updated_at"))
      else
        listOf(field("id"),
          field("body"),
          field(name("partition")),
          field("updated_at"))

      val select = jooq.select(baseFields)
        .from(PIPELINE.tableName)
        .join(
          jooq.selectExecutions(
          PIPELINE,
          fields = if (compressionProperties.enabled)
            listOf(field("id")) + field("compressed_body") + field("compression_type")
          else
            listOf(field("id")),
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
        )
        .using(field("id"))
        .orderBy(
          when (executionCriteria.sortType) {
            ExecutionComparator.BUILD_TIME_ASC -> field("build_time").asc()
            ExecutionComparator.BUILD_TIME_DESC -> field("build_time").desc()
            ExecutionComparator.START_TIME_OR_ID -> field("start_time").desc()
            ExecutionComparator.NATURAL_ASC -> field("id").desc()
            else -> field("id").asc()
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
    withPool(readPoolName) {
      return jooq.selectCount()
        .from(type.tableName)
        .where(id.toWhereCondition())
        .fetchSingle(count()) ?: 0 > 0
    }
  }

  override fun retrieveAllExecutionIds(type: ExecutionType): MutableList<String> {
    withPool(readPoolName) {
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

    withPool(readPoolName) {
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

    execution.trigger = mapper.convertValue(execution.trigger, Trigger::class.java)
    val stages = execution.stages.toMutableList().toList()
    execution.stages.clear()

    try {
      val tableName = execution.type.tableName
      val stageTableName = execution.type.stagesTableName
      val status = execution.status.toString()
      val updatedAt = Instant.now().toEpochMilli()
      val body = mapper.writeValueAsString(execution)
      val bodySize = body.length.toLong()
      execution.setSize(bodySize)
      log.debug("application ${execution.application}, pipeline name: ${execution.name}, pipeline config id ${execution.pipelineConfigId}, pipeline execution id ${execution.id}, execution size: ${bodySize}")

      val (executionId, legacyId) = mapLegacyId(ctx, tableName, execution.id, execution.startTime)

      val insertPairs = mutableMapOf(
        field("id") to executionId,
        field("legacy_id") to legacyId,
        field(name("partition")) to partitionName,
        field("status") to status,
        field("application") to execution.application,
        field("build_time") to (execution.buildTime ?: currentTimeMillis()),
        field("canceled") to execution.isCanceled,
        field("updated_at") to updatedAt,
        field("body") to body
      )

      val updatePairs = mutableMapOf(
        field("status") to status,
        field("body") to body,
        field(name("partition")) to partitionName,
        // won't have started on insert
        field("canceled") to execution.isCanceled,
        field("updated_at") to updatedAt
      )

      // Set startTime only if it is not null
      // jooq has some issues casting nulls when updating in the Postgres dialect
      val startTime = execution.startTime
      if (startTime != null) {
        insertPairs[field("start_time")] = startTime
        updatePairs[field("start_time")] = startTime
      }

      when (execution.type) {
        PIPELINE -> upsert(
          ctx,
          execution.type.tableName,
          insertPairs.plus(field("config_id") to execution.pipelineConfigId),
          updatePairs.plus(field("config_id") to execution.pipelineConfigId),
          executionId,
          compressionProperties.isWriteEnabled()
        )
        ORCHESTRATION -> upsert(
          ctx,
          execution.type.tableName,
          insertPairs,
          updatePairs,
          executionId,
          compressionProperties.isWriteEnabled()
        )
      }
      replicationLagAwareRepository.ifPresent { it -> it.putPipelineExecutionUpdate(executionId, Instant.ofEpochMilli(updatedAt)) }

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

        replicationLagAwareRepository.ifPresent { it -> it.putPipelineExecutionNumStages(executionId, stages.size) }
        stages.forEach { storeStageInternal(ctx, it, executionId) }
      }
    } finally {
      withListener { onUpsert(execution) }

      // Restore original object state.
      execution.stages.addAll(stages)
    }
  }

  private fun storeStageInternal(
    ctx: DSLContext,
    stage: StageExecution,
    executionId: String? = null,
    notifyListener: Boolean = false
  ) {
    val stageTable = stage.execution.type.stagesTableName
    val table = stage.execution.type.tableName
    val updatedAt = Instant.now().toEpochMilli()
    val body = mapper.writeValueAsString(stage)
    val bodySize = body.length.toLong()
    stage.setSize(bodySize)
    log.debug("application ${stage.execution.application}, pipeline name: ${stage.execution.name}, pipeline config id ${stage.execution.pipelineConfigId}, pipeline execution id ${stage.execution.id}, stage name: ${stage.name}, stage id: ${stage.id}, size: ${bodySize}")

    val buildTime = stage.execution.buildTime

    val executionUlid = executionId ?: mapLegacyId(ctx, table, stage.execution.id, buildTime).first
    val (stageId, legacyId) = mapLegacyId(ctx, stageTable, stage.id, buildTime)

    val insertPairs = mapOf(
      field("id") to stageId,
      field("legacy_id") to legacyId,
      field("execution_id") to executionUlid,
      field("status") to stage.status.toString(),
      field("updated_at") to updatedAt,
      field("body") to body
    )

    val updatePairs = mapOf(
      field("status") to stage.status.toString(),
      field("updated_at") to updatedAt,
      field("body") to body
    )

    upsert(ctx, stageTable, insertPairs, updatePairs, stage.id, compressionProperties.isWriteEnabled())
    replicationLagAwareRepository.ifPresent { it -> it.putStageExecutionUpdate(stageId, Instant.ofEpochMilli(updatedAt)) }

    // This method is called from [storeInternal] as well. We don't want to notify multiple times for the same
    // overall persist operation.
    if (notifyListener) {
      withListener { onUpsert(stage.execution) }
    }
  }

  private fun storeCorrelationIdInternal(ctx: DSLContext, execution: PipelineExecution) {
    if (execution.trigger.correlationId != null && !execution.status.isComplete) {
      val executionIdField = when (execution.type) {
        PIPELINE -> field("pipeline_id")
        ORCHESTRATION -> field("orchestration_id")
      }

      withPool(poolName) {
        ctx.insertInto(table("correlation_ids"))
          .columns(field("id"), executionIdField)
          .values(execution.trigger.correlationId, execution.id)
          .onConflict()
          .doNothing()
          .execute()
      }
    }
  }

  /***
   * Compresses the provided body string and returns the generated byte array
   *
   * @param id: pipelines/orchestration/stage id the body belongs to
   * @param body: body string to be compressed
   *
   * @return byte array representing the compressed string
   */
  @VisibleForTesting
  internal fun getCompressedBody(id: String, body: String): ByteArray? {

    var compressedBody: ByteArray? = null

    if (body.length <= compressionProperties.bodyCompressionThreshold) {
      log.debug("Body length ${body.length} for execution of $id does not breach " +
        "the compression threshold ${compressionProperties.bodyCompressionThreshold}. " +
        "No need to compress.")
    } else {
      log.info("Body length ${body.length} for execution of $id breaches the compression " +
        "threshold ${compressionProperties.bodyCompressionThreshold}")
      log.debug("Performing large body compression for $id.")
      val compressedBodyByteStream = ByteArrayOutputStream()
      val zipBeginTS = currentTimeMillis()
      compressionProperties.compressionType
        .getDeflator(compressedBodyByteStream)
        .bufferedWriter(StandardCharsets.UTF_8)
        .use { it.write(body, 0, body.length) }
      compressedBody = compressedBodyByteStream.toByteArray()
      val zipEndTS = currentTimeMillis()
      log.info("Compression complete for $id. Uncompressed body length: ${body.length} " +
        "Compressed body length: ${compressedBody.size} " +
        "Compression ratio: ${body.length.toDouble() / compressedBody.size} " +
        "Compression time(ms): ${zipEndTS - zipBeginTS} ")
    }

    return compressedBody
  }

  /**
   * Upserts the provided insert/update execution pairs into the provided table for the given id
   * and conditionally upserts the compressed execution into the corresponding compressed_execution
   * table
   *
   * @param ctx [DSLContext] to use for perfroming the upsertion
   * @param table [Table] to upsert into
   * @param insertPairs map of field value pairs to be inserted into the table
   * @param updatePairs map of field value pairs to be updated in the table
   * @param id id to be used for record upsertion
   * @param enableCompression flag controlling execution body compression
   */
  @VisibleForTesting
  internal fun upsert(
    ctx: DSLContext,
    table: Table<Record>,
    insertPairs: Map<Field<Any?>, Any?>,
    updatePairs: Map<Field<Any?>, Any?>,
    id: String,
    enableCompression: Boolean
  ) {

    val bodyField = field("body")
    val body = insertPairs[bodyField] as String
    val updatedInsertPairs = insertPairs.toMutableMap()
    val updatedUpdatePairs = updatePairs.toMutableMap()

    var compressedExecTablePairs: Map<Field<Any?>, Any?> = mapOf()
    var isBodyCompressed = false

    if (enableCompression) {
      // Conditionally handle body compression
      log.debug("Attempting to compress the body before upsertion into ${table.name} with id $id")
      val compressedBody = getCompressedBody(id, body)

      if (compressedBody != null) {
        // Set uncompressed body to empty string since body is not a nullable column
        updatedInsertPairs[bodyField] = ""
        updatedUpdatePairs[bodyField] = ""
        val updatedAt = insertPairs[field("updated_at")] as Long
        compressedExecTablePairs = mapOf(
          field("id") to id,
          field("compressed_body") to compressedBody,
          field("compression_type") to compressionProperties.compressionType.type,
          field("updated_at") to updatedAt
        )
        isBodyCompressed = true
      }
    }

    log.info("Upserting execution into the ${table.name} table with id $id")
    upsert(ctx,
      table,
      updatedInsertPairs,
      updatedUpdatePairs,
      id)

    if (isBodyCompressed) {
      val compressedExecTable = table.compressedExecTable
      log.info("Upserting compressed execution into the ${compressedExecTable.name} table " +
        "with id $id")
      upsert(ctx,
        table.compressedExecTable,
        compressedExecTablePairs,
        compressedExecTablePairs,
        id)
    }
  }

  @VisibleForTesting
  internal fun upsert(
    ctx: DSLContext,
    table: Table<Record>,
    insertPairs: Map<Field<Any?>, Any?>,
    updatePairs: Map<Field<Any?>, Any?>,
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
          .run {
            when (jooq.dialect()) {
              SQLDialect.POSTGRES -> {
                onConflict(DSL.field("id"))
                  .doUpdate()
                  .set(updatePairs)
                  .execute()
              }
              else -> {
                onDuplicateKeyUpdate()
                  .set(updatePairs)
                  .execute()
              }
            }
          }
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
    id: String
  ): PipelineExecution? {
    withPool(poolName) {
      val select = ctx.selectExecution(type).where(id.toWhereCondition())
      return select.fetchExecution()
    }
  }

  private fun selectExecution(
    ctx: DSLContext,
    type: ExecutionType,
    id: String,
    requireLatestVersion: Boolean
  ): PipelineExecution? {
    // Avoid collecting the readPoolRetrieve class of metrics here because it will
    // skew the number of times that retrieving using the read pool succeeded
    // on the first try
    if (!requireLatestVersion) {
      withPool(readPoolName) {
        val select = ctx.selectExecution(type).where(id.toWhereCondition())
        return select.fetchExecution()
      }
    }
    // If the read pool configuration does not enforce strict consistency, then
    // retrieve the execution from the default pool which does. This ensures that
    // the retrieved execution is up-to-date
    if (!readPoolStrictConsistencyEnforced()) {
      withPool(poolName) {
        val select = ctx.selectExecution(type).where(id.toWhereCondition())
        return select.fetchExecution()
      }
    }
    // Attempt to find an up-to-date execution from the read pool
    val readPoolRetryContext = readPoolRetryRegistry.retry("$type-$id-read-pool")
    var numberOfReadPoolQueries = 0L
    val (pipelineExecutions, resultCode) = try {
      withPool(readPoolName) {
        readPoolRetryContext.executeSupplier {
          numberOfReadPoolQueries += 1
          val select = ctx.selectExecution(type).where(id.toWhereCondition())
          select.fetchExecution(true)
        }
      }
    } catch (e: Exception) {
      // Swallow the exception and set the result code to NOT_FOUND to let the code fall back to the default pool
      log.error("Encountered an exception when fetching executionType: $type, id: $id from the read pool", e)
      ExecutionMapperResult(listOf(), ExecutionMapperResultCode.NOT_FOUND)
    }
    registry.counter(readPoolRetrieveTotalId).increment()
    // Determine the result and perform additional tasks if necessary
    when (resultCode) {
      ExecutionMapperResultCode.SUCCESS -> {
        registry.counter(readPoolRetrieveSucceededId.withTag(
          "numAttempts", numberOfReadPoolQueries.toString()
        )).increment()
        return pipelineExecutions.first()
      }
      ExecutionMapperResultCode.NOT_FOUND, ExecutionMapperResultCode.INVALID_VERSION -> {
        registry.counter(readPoolRetrieveFailedId).increment()
        withPool(poolName) {
          val select = ctx.selectExecution(type).where(id.toWhereCondition())
          return select.fetchExecution()
        }
      }
      // If an execution is missing from the ReplicationLagAwareRepository, retrieve
      // the execution using the default pool. If successful, also repopulate the
      // ReplicationLagAwareRepository with the execution metadata
      ExecutionMapperResultCode.MISSING_FROM_REPLICATION_LAG_REPOSITORY -> {
        registry.counter(readPoolRetrieveFailedId).increment()
        val pipelineExecution = withPool(poolName) {
          val select = ctx.selectExecution(type).where(id.toWhereCondition())
          select.fetchExecution()
        } ?: return null

        replicationLagAwareRepository.ifPresent { replLagAwareRepo ->
          replLagAwareRepo.putPipelineExecutionUpdate(
            pipelineExecution.id,
            Instant.ofEpochMilli(pipelineExecution.updatedAt)
          )
          replLagAwareRepo.putPipelineExecutionNumStages(
            pipelineExecution.id,
            pipelineExecution.stages.size
          )
          pipelineExecution.stages.forEach {
            replLagAwareRepo.putStageExecutionUpdate(it.id, Instant.ofEpochMilli(it.updatedAt))
          }
        }
        return pipelineExecution
      }
    }
  }

  private fun selectExecutions(
    type: ExecutionType,
    limit: Int,
    cursor: String?,
    where: ((SelectJoinStep<Record>) -> SelectConditionStep<Record>)? = null
  ): Collection<PipelineExecution> {
    withPool(readPoolName) {
      val select = jooq.selectExecutions(
        type,
        conditions = {
          if (cursor == null) {
            if (where == null) {
              it.where("1=1")
            } else {
              where(it)
            }
          } else {
            if (where == null) {
              it.where(field("id").lt(cursor))
            } else {
              where(it).and(field("id").lt(cursor))
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
    retrySupport.retry(
      {
        transaction { ctx ->
          fn(DSL.using(ctx))
        }
      },
      retryProperties.maxRetries, Duration.ofMillis(retryProperties.backoffMs), false
    )
  }

  private fun DSLContext.selectExecutions(
    type: ExecutionType,
    fields: List<Field<Any>> = selectFields(type),
    conditions: (SelectJoinStep<Record>) -> SelectConnectByStep<out Record>,
    seek: (SelectConnectByStep<out Record>) -> SelectForUpdateStep<out Record>
  ): SelectForUpdateStep<out Record> {
     val selectFrom = select(fields).from(type.tableName)

    if (compressionProperties.enabled) {
      selectFrom.leftJoin(type.tableName.compressedExecTable).using(field("id"))
    }

    return selectFrom
      .let { conditions(it) }
      .let { seek(it) }
  }

  private fun DSLContext.selectExecutions(
    type: ExecutionType,
    fields: List<Field<Any>> = selectFields(type),
    usingIndex: String,
    conditions: (SelectJoinStep<Record>) -> SelectConnectByStep<out Record>,
    seek: (SelectConnectByStep<out Record>) -> SelectForUpdateStep<out Record>
  ): SelectForUpdateStep<out Record> {
     val selectFrom = select(fields).from(if (jooq.dialect() == SQLDialect.MYSQL) type.tableName.forceIndex(usingIndex) else type.tableName)

    if (compressionProperties.enabled) {
      selectFrom.leftJoin(type.tableName.compressedExecTable).using(field("id"))
    }

    return selectFrom
      .let { conditions(it) }
      .let { seek(it) }
  }

  private fun DSLContext.selectExecution(type: ExecutionType, fields: List<Field<Any>> = selectFields(type)): SelectJoinStep<Record> {
    val selectFrom = select(fields).from(type.tableName)

    if (compressionProperties.enabled) {
      selectFrom.leftJoin(type.tableName.compressedExecTable).using(field("id"))
    }

    return selectFrom
  }

  /**
   * The fields used in a SELECT executions query.
   */
  private fun selectFields(executionType: ExecutionType): List<Field<Any>> {
    if (compressionProperties.enabled) {
      return listOf(field("id"),
        field("body"),
        field("compressed_body"),
        field("compression_type"),
        field(name("partition")),
        field(name(executionType.tableName.name, "updated_at")).`as`("updated_at"),
        field(name(executionType.tableName.compressedExecTable.name, "updated_at")).`as`("compressed_updated_at")
      )
    }

    return listOf(field("id"),
        field("body"),
        field(name("partition")),
        field("updated_at")
    )
  }

  private fun SelectForUpdateStep<out Record>.fetchExecutions() =
    ExecutionMapper(mapper, stageReadSize, compressionProperties, pipelineRefEnabled).map(fetch().intoResultSet(), jooq).executions

  private fun SelectForUpdateStep<out Record>.fetchExecution() =
    fetchExecutions().firstOrNull()

  private fun SelectForUpdateStep<out Record>.fetchExecutions(requireLatestVersion: Boolean) =
    ExecutionMapper(mapper, stageReadSize, compressionProperties, pipelineRefEnabled, requireLatestVersion, replicationLagAwareRepository).map(fetch().intoResultSet(), jooq)

  private fun SelectForUpdateStep<out Record>.fetchExecution(requireLatestVersion: Boolean) =
    fetchExecutions(requireLatestVersion)

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
      isForeign(execution, shouldThrow)
    } catch (_: ExecutionNotFoundException) {
      // Execution not found, we can proceed since the rest is likely a noop anyway
      false
    }
  }

  override fun getPartition(): String? {
    return partitionName
  }

  private fun validateHandledPartitionOrThrow(execution: PipelineExecution): Boolean =
    isForeign(execution, true)

  private fun withListener(callback: ExecutionRepositoryListener.() -> Unit) {
    executionRepositoryListeners.forEach {
      try {
        callback(it)
      } catch (e: Exception) {
        log.warn("Listener '${it.javaClass.simpleName}' encountered an error", e)
      }
    }
  }

  private fun readPoolStrictConsistencyEnforced(): Boolean {
    return readPoolName != poolName && replicationLagAwareRepository.isPresent()
  }

  class SyntheticStageRequired : IllegalArgumentException("Only synthetic stages can be inserted ad-hoc")
}
