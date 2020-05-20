package com.netflix.spinnaker.migrations

import com.netflix.spinnaker.keel.persistence.metamodel.Tables.EVENT
import com.netflix.spinnaker.keel.sql.inTransaction
import de.huxhorn.sulky.ulid.ULID
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jooq.DSLContext
import org.jooq.Record1
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener

class EventUidAssigner(private val jooq: DSLContext) : CoroutineScope {

  private val idGenerator = ULID()
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @EventListener(ApplicationReadyEvent::class)
  fun onApplicationReady() {
    launch {
      while (countEventsWithNoUID() > 0) {
        var count = 0
        jooq.inTransaction {
          select(EVENT.TIMESTAMP)
            .from(EVENT)
            .where(EVENT.UID.isNull)
            .limit(25)
            .forShare()
            .fetch()
            .forEach { ts ->
              runCatching {
                update(EVENT)
                  .set(EVENT.UID, ts.nextULID())
                  // this might actually not update the same row, but 🤷‍
                  .where(EVENT.TIMESTAMP.eq(ts.value1()))
                  .and(EVENT.UID.isNull)
                  .limit(1)
                  .execute()
              }
                .onSuccess { count += it }
                .onFailure { ex ->
                  log.error("Error assigning uid to event with timestamp ${ts.value1()}", ex)
                }
            }
        }
        log.info("Assigned uids to $count events...")
      }
    }
  }

  private fun countEventsWithNoUID(): Int =
    jooq
      .selectCount()
      .from(EVENT)
      .where(EVENT.UID.isNull)
      .fetchOne()
      .value1()
      .also {
        log.info("$it events still need uids...")
      }

  private fun Record1<LocalDateTime>.nextULID(): String =
    value1()
      .toInstant(UTC)
      .toEpochMilli()
      .let { idGenerator.nextULID(it) }

  override val coroutineContext = Dispatchers.IO
}
