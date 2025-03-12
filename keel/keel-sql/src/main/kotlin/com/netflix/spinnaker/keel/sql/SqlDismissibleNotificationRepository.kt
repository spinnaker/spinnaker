package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.UID
import com.netflix.spinnaker.keel.events.EventLevel
import com.netflix.spinnaker.keel.notifications.DismissibleNotification
import com.netflix.spinnaker.keel.persistence.DismissibleNotificationRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DISMISSIBLE_NOTIFICATION
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import de.huxhorn.sulky.ulid.ULID
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import java.time.Clock

/**
 * SQL implementation of [DismissibleNotificationRepository].
 */
class SqlDismissibleNotificationRepository(
  private val jooq: DSLContext,
  private val sqlRetry: SqlRetry,
  private val objectMapper: ObjectMapper,
  private val clock: Clock
) : DismissibleNotificationRepository {

  companion object {
    private val log by lazy { LoggerFactory.getLogger(SqlDismissibleNotificationRepository::class.java) }
  }

  @EventListener
  fun storeNotificationFromEvent(notification: DismissibleNotification) {
    log.debug("Storing notification from event: $notification")
    storeNotification(notification)
  }

  override fun storeNotification(notification: DismissibleNotification): UID {
    return sqlRetry.withRetry(WRITE) {
      val uid = ULID().nextULID()
      jooq
        .insertInto(DISMISSIBLE_NOTIFICATION)
        .set(DISMISSIBLE_NOTIFICATION.UID, uid.toString())
        .set(DISMISSIBLE_NOTIFICATION.JSON, notification)
        .execute()
      ULID.parseULID(uid)
    }
  }

  override fun notificationHistory(
    application: String,
    onlyActive: Boolean,
    levels: Set<EventLevel>,
    limit: Int
  ): List<DismissibleNotification> {
    return sqlRetry.withRetry(READ) {
      jooq
        .select(DISMISSIBLE_NOTIFICATION.UID, DISMISSIBLE_NOTIFICATION.JSON)
        .from(DISMISSIBLE_NOTIFICATION)
        .where(DISMISSIBLE_NOTIFICATION.APPLICATION.eq(application))
        .apply {
          if (onlyActive) {
            and(DISMISSIBLE_NOTIFICATION.IS_ACTIVE)
          }
          if (levels.isNotEmpty()) {
            and(DISMISSIBLE_NOTIFICATION.LEVEL.`in`(*levels.map { it.name }.toTypedArray()))
          }
        }
        .orderBy(DISMISSIBLE_NOTIFICATION.TRIGGERED_AT.desc())
        .limit(limit)
        .fetch { (uid, notification) ->
          notification.uid = ULID.parseULID(uid)
          notification
        }
    }
  }

  private fun generateDismissalJson(user: String? = null): Field<DismissibleNotification> {
    val dismissedAt: String = objectMapper.convertValue(clock.instant())
    return field<DismissibleNotification>(
      "json_set(json, '$.isActive', 'false', '$.dismissedBy', '$user', '$.dismissedAt', '$dismissedAt')"
    )
  }

  override fun dismissNotificationById(application: String, notificationUid: UID, user: String): Boolean {
    val updatedJson = generateDismissalJson(user)
    return sqlRetry.withRetry(WRITE) {
      jooq
        .update(DISMISSIBLE_NOTIFICATION)
        .set(DISMISSIBLE_NOTIFICATION.JSON, updatedJson)
        .where(DISMISSIBLE_NOTIFICATION.UID.eq(notificationUid.toString()))
        .and(DISMISSIBLE_NOTIFICATION.APPLICATION.eq(application))
        .execute()
    } > 0
  }

  override fun <T: DismissibleNotification> dismissNotification(type: Class<T>, application: String, branch: String, user: String?): Boolean {
    val updatedJson = generateDismissalJson(user)
    return sqlRetry.withRetry(WRITE) {
      jooq
        .update(DISMISSIBLE_NOTIFICATION)
        .set(DISMISSIBLE_NOTIFICATION.JSON, updatedJson)
        .where(DISMISSIBLE_NOTIFICATION.APPLICATION.eq(application))
        .and(DISMISSIBLE_NOTIFICATION.TYPE.eq(type.simpleName))
        .apply {
          if (branch.isNullOrEmpty()) {
            and(DISMISSIBLE_NOTIFICATION.BRANCH.isNull)
          } else {
            and(DISMISSIBLE_NOTIFICATION.BRANCH.eq(branch))
          }
        }
        .execute()
    } > 0
  }
}
