package com.netflix.spinnaker.keel.core.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import java.time.Instant

/**
 * Summarized data about a specific artifact, mostly for use by the UI.
 */
@JsonPropertyOrder(value = ["name", "type", "versions"])
data class ArtifactSummary(
  val name: String,
  val type: ArtifactType,
  val versions: Set<ArtifactVersionSummary> = emptySet()
)

data class ArtifactVersionSummary(
  val version: String,
  val environments: Set<ArtifactSummaryInEnvironment>
)

@JsonPropertyOrder(value = ["name", "version", "state"])
data class ArtifactSummaryInEnvironment(
  @JsonProperty("name")
  val environment: String,
  @JsonIgnore
  val version: String,
  val state: String,
  val deployedAt: Instant? = null,
  val replacedAt: Instant? = null,
  val replacedBy: String? = null
)
