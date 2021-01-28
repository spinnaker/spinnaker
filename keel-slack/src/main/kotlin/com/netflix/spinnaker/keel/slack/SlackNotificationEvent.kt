package com.netflix.spinnaker.keel.slack

import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType
import java.time.Instant

abstract class SlackNotificationEvent(
  open val time: Instant,
  open val application: String
)

data class SlackPinnedNotification(
  val pin: EnvironmentArtifactPin,
  val currentArtifact: PublishedArtifact,
  val pinnedArtifact: PublishedArtifact,
  override val time: Instant,
  override val application: String
) : SlackNotificationEvent(time, application)

data class SlackUnpinnedNotification(
  val latestArtifact: PublishedArtifact?,
  val pinnedArtifact: PublishedArtifact?,
  override val time: Instant,
  override val application: String,
  val user: String,
  val targetEnvironment: String
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
