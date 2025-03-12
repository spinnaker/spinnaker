package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.notifications.NotificationScope
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.persistence.NotificationRepository
import com.netflix.spinnaker.keel.persistence.metamodel.tables.Notification.NOTIFICATION
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import org.jooq.DSLContext
import java.time.Clock
import java.time.Duration

class SqlNotificationRepository(
  override val clock: Clock,
  private val jooq: DSLContext,
  private val sqlRetry: SqlRetry
) : NotificationRepository(clock) {
  override fun addNotification(scope: NotificationScope, ref: String, type: NotificationType): Boolean {
    sqlRetry.withRetry(READ) {
      jooq.select(NOTIFICATION.NOTIFY_AT)
        .from(NOTIFICATION)
        .where(NOTIFICATION.SCOPE.eq(scope))
        .and(NOTIFICATION.REF.eq(ref))
        .and(NOTIFICATION.TYPE.eq(type))
        .fetchOne(NOTIFICATION.NOTIFY_AT)
    }?.let { notificationTime ->
      // if record exists already, return whether or not to notify
      return notificationTime < clock.millis()
    }

    sqlRetry.withRetry(WRITE) {
      jooq.insertInto(NOTIFICATION)
        .set(NOTIFICATION.SCOPE, scope)
        .set(NOTIFICATION.REF, ref)
        .set(NOTIFICATION.TYPE, type)
        .set(NOTIFICATION.TIME_DETECTED, clock.millis())
        .set(NOTIFICATION.NOTIFY_AT, clock.millis())
        .onDuplicateKeyIgnore()
        .execute()
    }
    return true
  }

  override fun clearNotification(scope: NotificationScope, ref: String, type: NotificationType) {
    sqlRetry.withRetry(WRITE) {
      jooq.deleteFrom(NOTIFICATION)
        .where(NOTIFICATION.SCOPE.eq(scope))
        .and(NOTIFICATION.REF.eq(ref))
        .and(NOTIFICATION.TYPE.eq(type))
        .execute()
    }
  }

  override fun dueForNotification(scope: NotificationScope, ref: String, type: NotificationType): Boolean {
    sqlRetry.withRetry(READ) {
      jooq.select(NOTIFICATION.NOTIFY_AT)
        .from(NOTIFICATION)
        .where(NOTIFICATION.SCOPE.eq(scope))
        .and(NOTIFICATION.REF.eq(ref))
        .and(NOTIFICATION.TYPE.eq(type))
        .fetchOne(NOTIFICATION.NOTIFY_AT)
    }?.let { notificationTime ->
      return notificationTime < clock.millis()
    }
    // if record doesn't exist, don't notify
    return false
  }

  override fun markSent(scope: NotificationScope, ref: String, type: NotificationType) {
    val waitingMillis = Duration.parse(waitingDuration).toMillis()
    sqlRetry.withRetry(WRITE) {
      jooq.update(NOTIFICATION)
        .set(NOTIFICATION.NOTIFY_AT, clock.millis().plus(waitingMillis))
        .where(
          NOTIFICATION.SCOPE.eq(scope),
          NOTIFICATION.REF.eq(ref),
          NOTIFICATION.TYPE.eq(type)
        )
        .execute()
    }
  }
}
