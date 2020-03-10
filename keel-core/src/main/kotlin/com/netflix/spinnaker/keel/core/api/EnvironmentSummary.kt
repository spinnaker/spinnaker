package com.netflix.spinnaker.keel.core.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.id

/**
 * Summarized data about a specific environment, mostly for use by the UI.
 */
data class EnvironmentSummary(
  @JsonIgnore val environment: Environment,
  val artifacts: Set<ArtifactVersions>
) {
  val name: String
    get() = environment.name

  val resources: Set<String>
    get() = environment.resources.map { it.id }.toSet()
}

data class ArtifactVersions(
  val name: String,
  val type: ArtifactType,
  val statuses: Set<ArtifactStatus>,
  val versions: ArtifactVersionStatus
)

data class ArtifactVersionStatus(
  val current: String?,
  val deploying: String?,
  val pending: List<String>,
  val approved: List<String>,
  val previous: List<String>,
  val vetoed: List<String>
)
