package com.netflix.spinnaker.keel.notifications.slack.handlers

import com.netflix.spinnaker.keel.api.NotificationDisplay
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.notifications.slack.SlackNotificationEvent

/**
 * Implement this interface to send different types of slack notifications. Each notification is being construct by [sendMessage]
 * See: [PinnedNotificationHandler] for example
 */

interface SlackNotificationHandler<T : SlackNotificationEvent> {
  val supportedTypes: List<NotificationType>

  fun sendMessage(notification: T, channel: String, notificationDisplay: NotificationDisplay)

}

fun <T : SlackNotificationEvent> Collection<SlackNotificationHandler<*>>.supporting(
  type: NotificationType
): SlackNotificationHandler<T>? =
  this.find { it.supportedTypes.contains(type) } as? SlackNotificationHandler<T>
