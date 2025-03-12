package com.netflix.spinnaker.keel.events

import com.netflix.spinnaker.keel.notifications.NotificationScope
import com.netflix.spinnaker.keel.notifications.NotificationType

/**
 * An event to indicate that a notification should be removed
 */
data class ClearNotificationEvent(
  val scope: NotificationScope,
  val ref: String,
  val type: NotificationType
)
