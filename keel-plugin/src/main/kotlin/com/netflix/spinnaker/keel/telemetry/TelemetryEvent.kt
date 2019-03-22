package com.netflix.spinnaker.keel.telemetry

import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.persistence.ResourceState

sealed class TelemetryEvent

data class ResourceChecked(
  val name: ResourceName,
  val state: ResourceState
) : TelemetryEvent()
