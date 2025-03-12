package com.netflix.spinnaker.keel.notifications.slack

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.NotificationDisplay
import com.netflix.spinnaker.keel.api.NotificationDisplay.NORMAL
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.plugins.PluginNotificationConfig
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.PinnedEnvironment
import com.netflix.spinnaker.keel.core.api.UID
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType
import java.time.Instant

abstract class SlackNotificationEvent(
  open val time: Instant,
  open val application: String
)

data class SlackPinnedNotification(
  val pin: EnvironmentArtifactPin,
  val currentArtifact: PublishedArtifact?,
  val pinnedArtifact: PublishedArtifact,
  override val time: Instant,
  override val application: String
) : SlackNotificationEvent(time, application)

data class SlackUnpinnedNotification(
  val latestApprovedArtifactVersion: PublishedArtifact?,
  val pinnedArtifact: PublishedArtifact?,
  override val time: Instant,
  override val application: String,
  val user: String,
  val targetEnvironment: String,
  val originalPin: PinnedEnvironment
) : SlackNotificationEvent(time, application)

data class SlackMarkAsBadNotification(
  val vetoedArtifact: PublishedArtifact,
  override val time: Instant,
  override val application: String,
  val user: String,
  val targetEnvironment: String,
  val comment: String?
) : SlackNotificationEvent(time, application)

data class SlackPausedNotification(
  override val time: Instant,
  override val application: String,
  val user: String?,
  val comment: String? = null
) : SlackNotificationEvent(time, application)

data class SlackResumedNotification(
  override val time: Instant,
  override val application: String,
  val user: String?,
  val comment: String? = null
) : SlackNotificationEvent(time, application)

data class SlackLifecycleNotification(
  val artifact: PublishedArtifact,
  val eventType: LifecycleEventType,
  override val time: Instant,
  override val application: String
) : SlackNotificationEvent(time, application)

data class SlackArtifactDeploymentNotification(
  val artifact: PublishedArtifact,
  override val time: Instant,
  val targetEnvironment: String,
  val priorVersion: PublishedArtifact? = null,
  val status: DeploymentStatus,
  override val application: String
) : SlackNotificationEvent(time, application)

data class SlackManualJudgmentNotification(
  val artifactCandidate: PublishedArtifact,
  val currentArtifact: PublishedArtifact? = null,
  val pinnedArtifact: PublishedArtifact? = null,
  override val time: Instant,
  val targetEnvironment: String,
  val deliveryArtifact: DeliveryArtifact,
  val stateUid: UID?,
  override val application: String,
  val config: DeliveryConfig
) : SlackNotificationEvent(time, application)

data class SlackManualJudgmentUpdateNotification(
  val channel: String,
  val timestamp: String,
  val status: ConstraintStatus,
  val user: String?,
  val artifactCandidate: PublishedArtifact,
  val currentArtifact: PublishedArtifact? = null,
  val pinnedArtifact: PublishedArtifact? = null,
  val targetEnvironment: String,
  val deliveryArtifact: DeliveryArtifact,
  val author: String? = null,
  val display: NotificationDisplay = NORMAL,
  val config: DeliveryConfig,
  override val application: String,
  override val time: Instant
  ) : SlackNotificationEvent(time, application)

data class SlackVerificationCompletedNotification(
  val artifact: PublishedArtifact,
  override val time: Instant,
  val targetEnvironment: String,
  val status: ConstraintStatus,
  override val application: String
) : SlackNotificationEvent(time, application)

data class SlackConfigNotification(
  override val time: Instant,
  override val application: String,
  val config: DeliveryConfig,
  val gitMetadata: GitMetadata?,
  val new: Boolean
) : SlackNotificationEvent(time, application)

data class SlackPluginNotification(
  override val time: Instant,
  override val application: String,
  val config: PluginNotificationConfig,
  val artifactVersion: PublishedArtifact,
  val targetEnvironment: String,
) : SlackNotificationEvent(time, application)

enum class DeploymentStatus {
  SUCCEEDED, FAILED;
}
