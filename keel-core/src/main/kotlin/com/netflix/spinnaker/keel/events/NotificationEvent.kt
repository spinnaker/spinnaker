package com.netflix.spinnaker.keel.events

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.PinnedEnvironment
import com.netflix.spinnaker.keel.notifications.Notification
import com.netflix.spinnaker.keel.notifications.NotificationScope
import com.netflix.spinnaker.keel.notifications.NotificationType

abstract class NotificationEvent{
  abstract val scope: NotificationScope
  abstract val type: NotificationType
}

//This class will be used when we need to store repeated notifications in [NotificationRepository]
abstract class RepeatedNotificationEvent {
  abstract val scope: NotificationScope
  abstract val ref: String
  abstract val message: Notification
  abstract val type: NotificationType
}

data class UnhealthyNotification(
  override val ref: String,
  override val message: Notification
  ) :RepeatedNotificationEvent() {
    override val type = NotificationType.UNHEALTHY_RESOURCE
    override val scope = NotificationScope.RESOURCE
  }

data class PinnedNotification(
  val config: DeliveryConfig,
  val pin: EnvironmentArtifactPin
): NotificationEvent() {
  override val type = NotificationType.PINNED_ARTIFACT
  override val scope = NotificationScope.ARTIFACT
}

data class UnpinnedNotification(
  val config: DeliveryConfig,
  val pinnedEnvironment: PinnedEnvironment?,
  val targetEnvironment: String,
  val user: String
): NotificationEvent() {
  override val type = NotificationType.UNPINNED_ARTIFACT
  override val scope = NotificationScope.ARTIFACT
}
