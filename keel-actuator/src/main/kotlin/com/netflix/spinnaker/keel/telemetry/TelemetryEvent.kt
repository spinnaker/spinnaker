package com.netflix.spinnaker.keel.telemetry

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.name

sealed class TelemetryEvent

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

data class ResourceCheckError(
  val apiVersion: ApiVersion,
  val kind: String,
  val name: ResourceName,
  val exception: Throwable
) : TelemetryEvent() {
  constructor(resource: Resource<*>, exception: Throwable) : this(
    resource.apiVersion,
    resource.kind,
    resource.name,
    exception
  )
}

data class ArtifactVersionUpdated(
  val name: String,
  val type: ArtifactType
) : TelemetryEvent()
