package com.netflix.spinnaker.keel.telemetry

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.persistence.ResourceState

sealed class TelemetryEvent

data class ResourceChecked(
  val apiVersion: ApiVersion,
  val kind: String,
  val name: ResourceName,
  val state: ResourceState
) : TelemetryEvent()

data class LockAttempt(
  val success: Boolean
) : TelemetryEvent()

val LockAttemptSucceeded = LockAttempt(true)
val LockAttemptFailed = LockAttempt(false)
