package com.netflix.spinnaker.migrations

import com.netflix.spinnaker.keel.info.InstanceIdSupplier
import com.netflix.spinnaker.keel.persistence.AgentLockRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.CLUSTER_LOCK
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.EVENT
import de.huxhorn.sulky.ulid.ULID
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Result
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener

class EventUidAssigner(
  private val jooq: DSLContext,
  private val agentLockRepository: AgentLockRepository,
  instanceIdSupplier: InstanceIdSupplier,
  private val clock: Clock = Clock.systemUTC()
) : CoroutineScope {

  private val idGenerator = ULID()
  private val instanceId = instanceIdSupplier.get()
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @EventListener(ApplicationReadyEvent::class)
  fun onApplicationReady() {
    launch {
      var done = false
      while (!done) {
        withLock {
          var count = 0
          runCatching {
            jooq.fetchEventBatch()
              .also {
                done = it.isEmpty()
              }
              .forEach { (ts) ->
                runCatching { jooq.assignUID(ts) }
                  .onSuccess { count += it }
                  .onFailure { ex ->
                    log.error("Error assigning uid to event with timestamp $ts", ex)
                  }
              }
          }
            .onFailure { ex ->
              log.error("Error selecting event batch to assign uids", ex)
            }
          log.info("Assigned uids to $count events...")
        }
      }
      log.info("All events have uids assigned")
    }
  }

  private suspend fun withLock(block: () -> Unit) {
    if (acquireLock()) {
      block()
    } else {
      delay(Duration.ofSeconds(Random.nextLong(30, 90)).toMillis())
    }
  }

  private fun acquireLock(duration: Duration = Duration.ofMinutes(1)): Boolean {
    val lockId = javaClass.name
    val now = clock.instant()
    var acquired = jooq.insertInto(CLUSTER_LOCK)
      .set(CLUSTER_LOCK.ID, lockId)
      .set(CLUSTER_LOCK.HELD_BY, instanceId)
      .set(CLUSTER_LOCK.EXPIRES_AT, now.plus(duration).toEpochMilli())
      .onDuplicateKeyIgnore()
      .execute() == 1

    if (!acquired) {
      acquired = jooq.update(CLUSTER_LOCK)
        .set(CLUSTER_LOCK.HELD_BY, instanceId)
        .set(CLUSTER_LOCK.EXPIRES_AT, now.plus(duration).toEpochMilli())
        .where(CLUSTER_LOCK.HELD_BY.eq(instanceId).or(CLUSTER_LOCK.EXPIRES_AT.lt(now.toEpochMilli())))
        .and(CLUSTER_LOCK.ID.eq(lockId))
        .execute() == 1
    }

    if (acquired) {
      log.debug("Acquired lock for assigning event uids")
    } else {
      log.debug("Did not acquire lock for assigning event uids")
    }
    return acquired
  }

  private fun DSLContext.fetchEventBatch(batchSize: Int = 10): Result<Record1<LocalDateTime>> =
    select(EVENT.TIMESTAMP)
      .from(EVENT)
      .where(EVENT.UID.isNull)
      .limit(batchSize)
      .fetch()

  private fun DSLContext.assignUID(timestamp: LocalDateTime): Int =
    update(EVENT)
      .set(EVENT.UID, timestamp.nextULID())
      // this might actually not update the same row, but 🤷‍
      .where(EVENT.TIMESTAMP.eq(timestamp))
      .and(EVENT.UID.isNull)
      .limit(1)
      .execute()

  private fun LocalDateTime.nextULID(): String =
    toInstant(UTC)
      .toEpochMilli()
      .let { idGenerator.nextULID(it) }

  override val coroutineContext = Dispatchers.IO
}
