package com.netflix.spinnaker.orca.peering

import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.sql.routing.withPool
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import kotlin.math.min
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table

/**
 * Provides raw access to various tables in the orca SQL
 * TODO(mvulfson): Need an extension point to deal with cases such as OCA
 */
open class MySqlRawAccess(
  private val jooq: DSLContext,
  private val poolName: String,
  chunkSize: Int
) : SqlRawAccess(chunkSize) {

  private var maxPacketSize: Long = 0

  init {
    maxPacketSize = withPool(poolName) {
      jooq
        .resultQuery("SHOW VARIABLES WHERE Variable_name='max_allowed_packet'")
        .fetchOne(field("Value"), Long::class.java)
    }

    log.info("Initialized MySqlRawAccess with pool=$poolName and maxPacketSize=$maxPacketSize")

    // Hack: we don't count the full SQL statement length, so subtract a reasonable buffer for the boiler plate statement
    maxPacketSize -= 8192
  }

  override fun getCompletedExecutionIds(executionType: ExecutionType, partitionName: String?, updatedAfter: Long): List<ExecutionDiffKey> {
    val partitionConstraint = if (partitionName == null) {
      field("`partition`").isNull
    } else {
      field("`partition`").eq(partitionName)
    }

    return withPool(poolName) {
      jooq
        .select(field("id"), field("updated_at"))
        .from(getExecutionTable(executionType))
        .where(field("status").`in`(*completedStatuses.toTypedArray())
          .and(field("updated_at").gt(updatedAfter))
          .and(partitionConstraint))
        .fetchInto(ExecutionDiffKey::class.java)
    }
  }

  override fun getActiveExecutionIds(executionType: ExecutionType, partitionName: String?): List<String> {
    val partitionConstraint = if (partitionName == null) {
      field("`partition`").isNull
    } else {
      field("`partition`").eq(partitionName)
    }

    return withPool(poolName) {
      jooq
        .select(field("id"))
        .from(getExecutionTable(executionType))
        .where(field("status").`in`(*activeStatuses.toTypedArray())
          .and(partitionConstraint))
        .fetch(field("id"), String::class.java)
    }
  }

  override fun getDeletedExecutions(sinceCursor: Int): List<DeletedExecution> {
    return withPool(poolName) {
      jooq
        .select(field("id"), field("execution_id"), field("execution_type"))
        .from(table("deleted_executions"))
        .where(field("id").gt(sinceCursor))
        .fetchInto(DeletedExecution::class.java)
    }
  }

  override fun getStageIdsForExecutions(executionType: ExecutionType, executionIds: List<String>): List<ExecutionDiffKey> {
    return withPool(poolName) {
      jooq
        .select(field("id"), field("updated_at"))
        .from(getStagesTable(executionType))
        .where(field("execution_id").`in`(*executionIds.toTypedArray()))
        .fetchInto(ExecutionDiffKey::class.java)
    }
  }

  override fun getExecutions(executionType: ExecutionType, ids: List<String>): org.jooq.Result<Record> {
    return withPool(poolName) {
      jooq.select(DSL.asterisk())
        .from(getExecutionTable(executionType))
        .where(field("id").`in`(*ids.toTypedArray()))
        .fetch()
    }
  }

  override fun getStages(executionType: ExecutionType, stageIds: List<String>): org.jooq.Result<Record> {
    return withPool(poolName) {
      jooq.select(DSL.asterisk())
        .from(getStagesTable(executionType))
        .where(field("id").`in`(*stageIds.toTypedArray()))
        .fetch()
    }
  }

  override fun deleteStages(executionType: ExecutionType, stageIdsToDelete: List<String>) {
    withPool(poolName) {
      for (chunk in stageIdsToDelete.chunked(chunkSize)) {
        jooq
          .deleteFrom(getStagesTable(executionType))
          .where(field("id").`in`(*chunk.toTypedArray()))
          .execute()
      }
    }
  }

  override fun deleteExecutions(executionType: ExecutionType, pipelineIdsToDelete: List<String>): Int {
    var countDeleted = 0

    if (pipelineIdsToDelete.isEmpty()) {
      return countDeleted
    }

    // We have a bunch of indexes and deletes, esp on the *stages tables, are pretty nasty, so chunk the deletes smaller
    // Ideally, we put this on a separate thread that chugs along in the background - for another day
    val deleteChunkSize = min(chunkSize, 5)
    withPool(poolName) {
      for (chunk in pipelineIdsToDelete.chunked(chunkSize)) {
        jooq
          .deleteFrom(getStagesTable(executionType))
          .where(field("execution_id").`in`(*chunk.toTypedArray()))
          .execute()

        countDeleted += jooq
          .deleteFrom(getExecutionTable(executionType))
          .where(field("id").`in`(*chunk.toTypedArray()))
          .execute()
      }
    }

    return countDeleted
  }

  override fun loadRecords(tableName: String, records: org.jooq.Result<Record>): Int {
    if (records.isEmpty()) {
      return 0
    }

    val allFields = records[0].fields().toList()
    var persisted = 0

    withPool(poolName) {
      val updateSet = allFields.map {
        it to field("VALUES({0})", it.dataType, it)
      }.toMap()

      var cumulativeSize = 0
      var batchQuery = jooq
        .insertInto(table(tableName))
        .columns(allFields)

      records.forEach { it ->
        val values = it.intoList()
        val totalRecordSize = (3 * values.size) + values.sumBy { value -> (value?.toString()?.length ?: 4) }

        if (cumulativeSize + totalRecordSize > maxPacketSize) {
          if (cumulativeSize == 0) {
            throw SystemException("Can't persist a single row for table $tableName due to maxPacketSize restriction. Row size = $totalRecordSize")
          }

          // Dump it to the DB
          batchQuery
            .onDuplicateKeyUpdate()
            .set(updateSet)
            .execute()

          batchQuery = jooq
            .insertInto(table(tableName))
            .columns(allFields)
            .values(values)
          cumulativeSize = 0
        } else {
          batchQuery = batchQuery
            .values(values)
        }

        persisted++
        cumulativeSize += totalRecordSize
      }

      if (cumulativeSize > 0) {
        // Dump the last bit to the DB
        batchQuery
          .onDuplicateKeyUpdate()
          .set(updateSet)
          .execute()
      }
    }

    return persisted
  }
}
