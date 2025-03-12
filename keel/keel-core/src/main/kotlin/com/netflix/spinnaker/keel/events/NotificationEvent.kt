package com.netflix.spinnaker.keel.events

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.events.ConstraintStateChanged
import com.netflix.spinnaker.keel.api.plugins.PluginNotificationConfig
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.core.api.PinnedEnvironment
import com.netflix.spinnaker.keel.notifications.Notification
import com.netflix.spinnaker.keel.notifications.NotificationScope
import com.netflix.spinnaker.keel.notifications.NotificationScope.APPLICATION
import com.netflix.spinnaker.keel.notifications.NotificationScope.ARTIFACT
import com.netflix.spinnaker.keel.notifications.NotificationScope.ENVIRONMENT
import com.netflix.spinnaker.keel.notifications.NotificationScope.RESOURCE
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.notifications.NotificationType.ARTIFACT_DEPLOYMENT_SUCCEEDED
import com.netflix.spinnaker.keel.notifications.NotificationType.ARTIFACT_MARK_AS_BAD
import com.netflix.spinnaker.keel.notifications.NotificationType.ARTIFACT_PINNED
import com.netflix.spinnaker.keel.notifications.NotificationType.ARTIFACT_UNPINNED
import com.netflix.spinnaker.keel.notifications.NotificationType.DELIVERY_CONFIG_CHANGED
import com.netflix.spinnaker.keel.notifications.NotificationType.PLUGIN_NOTIFICATION
import com.netflix.spinnaker.keel.notifications.NotificationType.RESOURCE_UNHEALTHY

abstract class NotificationEvent {
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
) : RepeatedNotificationEvent() {
  override val type = RESOURCE_UNHEALTHY
  override val scope = RESOURCE
}

data class PinnedNotification(
  val config: DeliveryConfig,
  val pin: EnvironmentArtifactPin
) : NotificationEvent() {
  override val type = ARTIFACT_PINNED
  override val scope = ARTIFACT
}

data class UnpinnedNotification(
  val config: DeliveryConfig,
  val pinnedEnvironment: PinnedEnvironment?,
  val targetEnvironment: String,
  val user: String
) : NotificationEvent() {
  override val type = ARTIFACT_UNPINNED
  override val scope = ARTIFACT
}

data class MarkAsBadNotification(
  val config: DeliveryConfig,
  val user: String,
  val veto: EnvironmentArtifactVeto
) : NotificationEvent() {
  override val type = ARTIFACT_MARK_AS_BAD
  override val scope = ARTIFACT
}

data class ArtifactDeployedNotification(
  val config: DeliveryConfig,
  val artifactVersion: String,
  val deliveryArtifact: DeliveryArtifact,
  val targetEnvironment: Environment
) : NotificationEvent() {
  override val type = ARTIFACT_DEPLOYMENT_SUCCEEDED
  override val scope = ARTIFACT
}

data class DeliveryConfigChangedNotification(
  val config: DeliveryConfig,
  val gitMetadata: GitMetadata? = null,
  val new: Boolean = false
) : NotificationEvent() {
  override val type = DELIVERY_CONFIG_CHANGED
  override val scope = APPLICATION
}

data class PluginNotification(
  val pluginNotificationConfig: PluginNotificationConfig,
  val constraintStateChanged: ConstraintStateChanged,
) : NotificationEvent() {
  override val type = PLUGIN_NOTIFICATION
  override val scope = ENVIRONMENT
}
