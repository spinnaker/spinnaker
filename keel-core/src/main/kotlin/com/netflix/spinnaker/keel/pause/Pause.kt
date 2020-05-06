package com.netflix.spinnaker.keel.pause

import java.time.Instant

/**
 * A pause in an application or resource's actuation, requested by a user.
 */
data class Pause(
  val scope: PauseScope,
  val name: String,
  val pausedBy: String,
  val pausedAt: Instant
)

// todo eb: add environment
enum class PauseScope {
  APPLICATION, RESOURCE
}
