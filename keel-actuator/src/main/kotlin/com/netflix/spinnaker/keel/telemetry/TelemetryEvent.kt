package com.netflix.spinnaker.keel.telemetry

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.name

sealed class TelemetryEvent

data class ResourceChecked(
  val apiVersion: ApiVersion,
  val kind: String,
  val name: ResourceName,
  val state: ResourceState
) : TelemetryEvent() {
  constructor(resource: Resource<*>, state: ResourceState) : this(
    resource.apiVersion,
    resource.kind,
    resource.name,
    state
  )
}

data class ResourceCheckSkipped(
  val apiVersion: ApiVersion,
  val kind: String,
  val name: ResourceName
) : TelemetryEvent() {
  constructor(resource: Resource<*>) : this(
    resource.apiVersion,
    resource.kind,
    resource.name
  )
}

data class ArtifactVersionUpdated(
  val name: String,
  val type: ArtifactType
) : TelemetryEvent()
