package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.notifications.NotificationScope
import com.netflix.spinnaker.keel.notifications.NotificationType
import org.springframework.beans.factory.annotation.Value
import java.time.Clock

/**
 * A repository for storing a list of ongoing notifications, and calculating whether
 * they should be resent.
 *
 * Note, this repository does not store the notification message.
 */
abstract class NotificationRepository(
  open val clock: Clock,
  @Value("notify.waiting-duration") var waitingDuration: String = "P1D"
) {

  /**
   * Adds a notification to the list of ongoing notifications.
   * Assumption: each notifier sends only one type of message
   * @return true if we should notify right now
   */
  abstract fun addNotification(scope: NotificationScope, ref: String, type: NotificationType): Boolean

  /**
   * Clears a notification from the list of ongoing notifications.
   * Does nothing if notification does not exist.
   */
  abstract fun clearNotification(scope: NotificationScope, ref: String, type: NotificationType)

  /**
   * @return true if the notification should be sent
   */
  abstract fun dueForNotification(scope: NotificationScope, ref: String, type: NotificationType): Boolean

  /**
   * Marks notification as sent at the current time
   */
  abstract fun markSent(scope: NotificationScope, ref: String, type: NotificationType)
}
