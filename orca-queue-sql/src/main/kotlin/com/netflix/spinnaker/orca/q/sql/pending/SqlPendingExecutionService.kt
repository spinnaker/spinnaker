package com.netflix.spinnaker.orca.q.sql.pending

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.histogram.PercentileTimer
import com.netflix.spinnaker.config.TransactionRetryProperties
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.StartExecution
import com.netflix.spinnaker.orca.q.StartWaitingExecutions
import com.netflix.spinnaker.orca.q.pending.PendingExecutionService
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.Queue
import de.huxhorn.sulky.ulid.ULID
import org.jooq.DSLContext
import org.jooq.SortField
import org.jooq.SortOrder
import org.jooq.impl.DSL
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit

class SqlPendingExecutionService(
  private val shard: String?,
  private val jooq: DSLContext,
  private val queue: Queue,
  private val repository: ExecutionRepository,
  private val mapper: ObjectMapper,
  private val clock: Clock,
  private val registry: Registry,
  private val retryProperties: TransactionRetryProperties,
  private val maxDepth: Int
) : PendingExecutionService {

  companion object {
    private val pendingTable = DSL.table("pending_executions")
    private val configField = DSL.field("config_id")
    private val idField = DSL.field("id")
    private val messageField = DSL.field("message")
    private val shardField = DSL.field("shard")

    private val retrySupport = RetrySupport()
    private val log: Logger = LoggerFactory.getLogger(SqlPendingExecutionService::class.java)
  }

  private val enqueueId = registry.createId("queue.pending.enqueue")
  private val cancelId = registry.createId("queue.pending.cancelled")
  private val popId = registry.createId("queue.pending.pop")

  private var shardCondition = if (shard.isNullOrBlank()) {
    shardField.isNull
  } else {
    shardField.eq(shard)
  }

  override fun enqueue(pipelineConfigId: String, message: Message) {
    PercentileTimer.get(registry, enqueueId)
      .record {
        doEnqueue(pipelineConfigId, message)
      }
  }

  private fun doEnqueue(pipelineConfigId: String, message: Message) {
    try {
      val queued = depth(pipelineConfigId)
      if (queued >= maxDepth) {
        /**
         * If dropping a StartExecution message, actively cancel the execution so it won't be left in
         * NOT_STARTED without a message on the active or pending queue.
         *
         * Other message types can be safely dropped.
         */
        if (message is StartExecution) {
          log.warn("Canceling execution ${message.executionId} for pipeline $pipelineConfigId due to pending " +
            "depth of $queued executions")
          registry.counter(cancelId).increment()

          try {
            val execution = repository.retrieve(PIPELINE, message.executionId)
              .apply {
                isCanceled = true
                canceledBy = "spinnaker"
                cancellationReason = "Too many pending executions ($queued) for pipelineId"
                status = ExecutionStatus.CANCELED
              }
            repository.store(execution)
          } catch (e: ExecutionNotFoundException) {
            log.error("Failed to retrieve execution ${message.executionId} for pipeline $pipelineConfigId")
          }
        } else {
          log.warn("Dropping pending message for pipeline $pipelineConfigId due to pending execution depth of $queued")
        }
        return
      }

      withRetry {
        jooq.insertInto(pendingTable)
          .columns(idField, configField, shardField, messageField)
          .values(ULID().nextValue().toString(), pipelineConfigId, shard, mapper.writeValueAsString(message))
          .execute()
      }
    } catch (e: Exception) {
      log.error("Failed to enqueue pending execution for pipeline $pipelineConfigId")
      throw e // back to StartExecutionHandler, we may want to requeue the StartExecution message
    }
  }

  override fun popOldest(pipelineConfigId: String): Message? {
    val start = clock.millis()
    val message = pop(pipelineConfigId, idField.asc())

    PercentileTimer.get(registry, popId.withTag("purge", "false"))
      .record(clock.millis() - start, TimeUnit.MILLISECONDS)

    return message
  }

  override fun popNewest(pipelineConfigId: String): Message? {
    val start = clock.millis()
    val message = pop(pipelineConfigId, idField.desc())

    PercentileTimer.get(registry, popId.withTag("purge", "true"))
      .record(clock.millis() - start, TimeUnit.MILLISECONDS)

    return message
  }

  private fun pop(pipelineConfigId: String, sortField: SortField<Any>): Message? {
    try {
      return withRetry {
        jooq.transactionResult { configuration ->
          val txn = DSL.using(configuration)
          txn.select(idField, messageField)
            .from(pendingTable)
            .where(
              configField.eq(pipelineConfigId),
              shardCondition
            )
            .orderBy(sortField)
            .limit(1)
            .forUpdate()
            .fetchOne()
            ?.into(MessageContainer::class.java)
            ?.let {
              txn.deleteFrom(pendingTable)
                .where(idField.eq(it.id))
                .execute()

              return@transactionResult mapper.readValue(it.message, Message::class.java)
            }
        }
      }
    } catch (e: Exception) {
      log.error("Failed popping pending execution for pipeline $pipelineConfigId, attempting to requeue " +
        "StartWaitingExecutions message", e)

      val purge = (sortField.order == SortOrder.DESC)
      queue.push(StartWaitingExecutions(pipelineConfigId, purge), Duration.ofSeconds(10))

      return null
    }
  }

  override fun purge(pipelineConfigId: String, callback: (Message) -> Unit) {
    do {
      val oldest = popOldest(pipelineConfigId)
      oldest?.let(callback)
    } while (oldest != null)
  }

  override fun depth(pipelineConfigId: String): Int =
    withRetry {
      jooq.selectCount()
        .from(pendingTable)
        .where(
          configField.eq(pipelineConfigId),
          shardCondition
        )
        .fetchOne(0, Int::class.java)
    }

  private data class MessageContainer(
    val id: String,
    val message: String
  )

  private fun <T> withRetry(fn: (Any) -> T): T {
    return retrySupport.retry({
      fn(this)
    }, retryProperties.maxRetries, retryProperties.backoffMs, false)
  }
}
