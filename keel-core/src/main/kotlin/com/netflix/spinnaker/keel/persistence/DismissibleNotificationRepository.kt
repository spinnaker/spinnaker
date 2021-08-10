package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.UID
import com.netflix.spinnaker.keel.events.EventLevel
import com.netflix.spinnaker.keel.notifications.DismissibleNotification
import java.time.Instant

/**
 * Repository for [DismissibleNotification].
 */
interface DismissibleNotificationRepository {
  companion object {
    const val DEFAULT_MAX_NOTIFICATIONS = 10
  }

  /**
   * Records a [DismissibleNotification].
   *
   * @return the [UID] of the newly created notification.
   */
  fun storeNotification(notification: DismissibleNotification): UID

  /**
   * Retrieves the history of [DismissibleNotification]s for [application], newest to oldest.
   *
   * @param application the name of the application.
   * @param onlyActive whether to filter for only notifications that are active.
   * @param levels optional filter for event levels. Defaults to all.
   * @param limit the maximum number of notifications to return.
   */
  fun notificationHistory(
    application: String,
    onlyActive: Boolean = false,
    levels: Set<EventLevel> = emptySet(),
    limit: Int = DEFAULT_MAX_NOTIFICATIONS
  ): List<DismissibleNotification>

  /**
   * Sets [DismissibleNotification.isActive] to false in the corresponding database record.
   */
  fun dismissNotificationById(application: String, notificationUid: UID, user: String): Boolean

  /**
   * Sets [isActive] of a given [type] of [DismissibleNotification] to false for a given [application].
   */
  fun <T: DismissibleNotification> dismissNotification(type: Class<T>, application: String, branch: String, user: String? = null): Boolean
}
