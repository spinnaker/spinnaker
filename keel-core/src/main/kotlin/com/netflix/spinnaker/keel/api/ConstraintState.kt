package com.netflix.spinnaker.keel.api

import java.time.Duration
import java.time.Instant

data class ConstraintState(
  val deliveryConfigName: String,
  val environmentName: String,
  val artifactVersion: String,
  val constraintType: String,
  val status: ConstraintStatus,
  val createdAt: Instant = Instant.now(),
  val judgedBy: String? = null,
  val judgedAt: Instant? = null,
  val comment: String? = null,
  // TODO: reconsider type when implementing a constraint that uses attributes
  val attributes: Map<String, Any?> = emptyMap()
) {
  fun canPromote() = status.passes()

  fun failed() = status.failed()

  fun timedOut(timeout: Duration, now: Instant) =
    createdAt.plus(timeout).isBefore(now)
}

enum class ConstraintStatus(private val passed: Boolean, private val failed: Boolean) {
  PENDING(false, false),
  PASS(true, false),
  FAIL(false, true),
  OVERRIDE_PASS(true, false),
  OVERRIDE_FAIL(false, true);

  fun passes() = passed
  fun failed() = failed
}
