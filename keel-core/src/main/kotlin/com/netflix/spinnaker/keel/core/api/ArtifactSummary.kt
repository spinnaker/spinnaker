package com.netflix.spinnaker.keel.core.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.constraints.ConstraintStateAttributes
import com.netflix.spinnaker.keel.constraints.ConstraintStatus
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

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(value = ["name", "version", "state"])
data class ArtifactVersionSummary(
  val version: String,
  val displayName: String,
  val environments: Set<ArtifactSummaryInEnvironment>,
  val build: BuildMetadata? = null,
  val git: GitMetadata? = null
)

/**
 * todo eb: other information should go here, like a link to the jenkins build. But that needs to be done
 * in a scalable way. For now, this is just a minimal container for information we can parse from the version.
 */
data class BuildMetadata(
  val id: Int
)

/**
 * todo eb: other information should go here, like a link to the commit. But that needs to be done
 * in a scalable way. For now, this is just a minimal container for information we can parse from the version.
 */
data class GitMetadata(
  val commit: String
)

@JsonInclude(Include.NON_EMPTY)
data class ArtifactSummaryInEnvironment(
  @JsonProperty("name")
  val environment: String,
  @JsonIgnore
  val version: String,
  val state: String,
  val deployedAt: Instant? = null,
  val replacedAt: Instant? = null,
  val replacedBy: String? = null,
  val constraints: List<EnvironmentConstraintSummary> = emptyList()
)

@JsonInclude(Include.NON_NULL)
data class EnvironmentConstraintSummary(
  val type: String,
  val status: ConstraintStatus,
  val createdAt: Instant,
  val judgedBy: String? = null,
  val judgedAt: Instant? = null,
  val comment: String? = null,
  val attributes: ConstraintStateAttributes? = null
)
