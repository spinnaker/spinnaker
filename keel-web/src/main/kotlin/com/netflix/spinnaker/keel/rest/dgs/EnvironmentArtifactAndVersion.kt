package com.netflix.spinnaker.keel.rest.dgs

import com.netflix.spinnaker.keel.api.action.ActionType

data class EnvironmentArtifactAndVersion(
  val environmentName: String,
  val artifactReference: String,
  val version: String,
  val actionType: ActionType? = null
)
