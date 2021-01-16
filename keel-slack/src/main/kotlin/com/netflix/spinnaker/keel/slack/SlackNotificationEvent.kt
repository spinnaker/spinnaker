package com.netflix.spinnaker.keel.slack

import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import java.time.Instant

abstract class SlackNotificationEvent(
  open val channel: String
)

data class SlackPinnedNotification(
  override val channel: String,
  val pin: EnvironmentArtifactPin,
  val currentArtifact: PublishedArtifact?,
  val pinnedArtifact: PublishedArtifact?,
  val time: Instant,
  val application: String
  ) : SlackNotificationEvent(channel)

data class SlackUnpinnedNotification(
  override val channel: String,
  val latestArtifact: PublishedArtifact?,
  val pinnedVersion: String?,
  val time: Instant,
  val application: String,
  val user: String
  ) : SlackNotificationEvent(channel)
