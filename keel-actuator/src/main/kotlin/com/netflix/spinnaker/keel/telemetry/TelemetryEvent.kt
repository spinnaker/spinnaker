package com.netflix.spinnaker.keel.telemetry

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.api.id

sealed class TelemetryEvent

data class ResourceCheckSkipped(
  val apiVersion: ApiVersion,
  val kind: String,
  val id: ResourceId
) : TelemetryEvent() {
  constructor(resource: Resource<*>) : this(
    resource.apiVersion,
    resource.kind,
    resource.id
  )
}

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
