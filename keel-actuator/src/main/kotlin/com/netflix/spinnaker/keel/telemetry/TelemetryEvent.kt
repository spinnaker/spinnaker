package com.netflix.spinnaker.keel.telemetry

import com.netflix.spinnaker.keel.api.ArtifactType

sealed class TelemetryEvent

data class ResourceCheckSkipped(
  val apiVersion: String,
  val kind: String,
  val id: String,
  val skipper: String = "unknown"
) : TelemetryEvent()

data class ArtifactVersionUpdated(
  val name: String,
  val type: ArtifactType
) : TelemetryEvent()

data class ArtifactVersionApproved(
  val application: String,
  val deliveryConfigName: String,
  val environmentName: String,
  val artifactName: String,
  val artifactType: ArtifactType,
  val artifactVersion: String
) : TelemetryEvent()
