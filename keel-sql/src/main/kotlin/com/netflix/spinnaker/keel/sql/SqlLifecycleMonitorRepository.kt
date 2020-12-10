package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.lifecycle.LifecycleEvent
import com.netflix.spinnaker.keel.lifecycle.LifecycleMonitorRepository
import com.netflix.spinnaker.keel.lifecycle.MonitoredTask
import com.netflix.spinnaker.keel.lifecycle.StartMonitoringEvent
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.LIFECYCLE_EVENT
import com.netflix.spinnaker.keel.persistence.metamodel.tables.LifecycleMonitor.LIFECYCLE_MONITOR
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import de.huxhorn.sulky.ulid.ULID
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

class SqlLifecycleMonitorRepository(
  private val jooq: DSLContext,
  private val clock: Clock,
  private val objectMapper: ObjectMapper,
  private val sqlRetry: SqlRetry
) : LifecycleMonitorRepository {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun tasksDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<MonitoredTask> {
    val now = clock.instant()
    val cutoff = now.minus(minTimeSinceLastCheck)
    return sqlRetry.withRetry(WRITE) {
      jooq.inTransaction {
        select(LIFECYCLE_MONITOR.UID, LIFECYCLE_MONITOR.TYPE, LIFECYCLE_MONITOR.LINK, LIFECYCLE_MONITOR.NUM_FAILURES, LIFECYCLE_MONITOR.TRIGGERING_EVENT_UID)
          .from(LIFECYCLE_MONITOR)
          .where(LIFECYCLE_MONITOR.LAST_CHECKED.lessOrEqual(cutoff))
          .and(LIFECYCLE_MONITOR.IGNORE.notEqual(true))
          .orderBy(LIFECYCLE_MONITOR.LAST_CHECKED)
          .limit(limit)
          .forUpdate()
          .fetch()
          .onEach { (uid, _, _, _, _) ->
            update(LIFECYCLE_MONITOR)
              .set(LIFECYCLE_MONITOR.LAST_CHECKED, now)
              .where(LIFECYCLE_MONITOR.UID.eq(uid))
              .execute()
          }
      }
        .map { (uid, type, link, numFailures, triggeringEventUid ) ->
          try {
            MonitoredTask(
              type = type,
              triggeringEvent = fetchEvent(triggeringEventUid),
              numFailures = numFailures,
              link = link,
              triggeringEventUid = triggeringEventUid
            )
          } catch (e: Exception) {
            // if we can't serialize the event, ignore it so it doesn't block future things
            jooq.update(LIFECYCLE_MONITOR)
              .set(LIFECYCLE_MONITOR.IGNORE, true)
              .where(LIFECYCLE_MONITOR.UID.eq(uid))
              .execute()
            throw e
          }
        }
    }
  }

  private fun fetchEvent(uid: String): LifecycleEvent =
   sqlRetry.withRetry(READ) {
      jooq.select(LIFECYCLE_EVENT.TIMESTAMP, LIFECYCLE_EVENT.JSON)
        .from(LIFECYCLE_EVENT)
        .where(LIFECYCLE_EVENT.UID.eq(uid))
        .fetchOne { (timestamp, json) ->
          val event = objectMapper.readValue<LifecycleEvent>(json)
          event.copy(timestamp = timestamp)
        }
    }

  override fun save(event: StartMonitoringEvent) {
    sqlRetry.withRetry(WRITE) {
      jooq.insertInto(LIFECYCLE_MONITOR)
        .set(LIFECYCLE_MONITOR.UID, ULID().nextULID(clock.millis()))
        .set(LIFECYCLE_MONITOR.TYPE, event.triggeringEvent.type)
        .set(LIFECYCLE_MONITOR.LINK, event.triggeringEvent.link)
        .set(LIFECYCLE_MONITOR.LAST_CHECKED, Instant.EPOCH.plusSeconds(1))
        .set(LIFECYCLE_MONITOR.TRIGGERING_EVENT_UID, event.triggeringEventUid)
        .execute()
    }
  }

  override fun delete(task: MonitoredTask) {
    sqlRetry.withRetry(WRITE) {
      jooq.deleteFrom(LIFECYCLE_MONITOR)
        .where(LIFECYCLE_MONITOR.TYPE.eq(task.type))
        .and(LIFECYCLE_MONITOR.TRIGGERING_EVENT_UID.eq(task.triggeringEventUid))
        .execute()
    }
  }

  override fun markFailureGettingStatus(task: MonitoredTask) {
    sqlRetry.withRetry(WRITE) {
      jooq.update(LIFECYCLE_MONITOR)
        .set(LIFECYCLE_MONITOR.NUM_FAILURES,LIFECYCLE_MONITOR.NUM_FAILURES.plus(1))
        .where(LIFECYCLE_MONITOR.TYPE.eq(task.type))
        .and(LIFECYCLE_MONITOR.TRIGGERING_EVENT_UID.eq(task.triggeringEventUid))
        .execute()
    }
  }

  override fun clearFailuresGettingStatus(task: MonitoredTask) {
    sqlRetry.withRetry(WRITE) {
      jooq.update(LIFECYCLE_MONITOR)
        .set(LIFECYCLE_MONITOR.NUM_FAILURES, 0)
        .where(LIFECYCLE_MONITOR.TYPE.eq(task.type))
        .and(LIFECYCLE_MONITOR.TRIGGERING_EVENT_UID.eq(task.triggeringEventUid))
        .execute()
    }
  }

  override fun numTasksMonitoring(): Int =
    sqlRetry.withRetry(READ) {
      jooq.selectCount()
        .from(LIFECYCLE_MONITOR)
        .fetchOne()
        .value1()
    }
}
