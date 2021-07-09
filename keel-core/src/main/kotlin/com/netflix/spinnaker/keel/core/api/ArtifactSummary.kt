package com.netflix.spinnaker.keel.core.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.constraints.ConstraintStateAttributes
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.lifecycle.LifecycleStep
import java.time.Instant

/**
 * Summarized data about a specific artifact, mostly for use by the UI.
 */
@JsonPropertyOrder(value = ["name", "type", "reference", "versions"])
data class ArtifactSummary(
  val name: String,
  val type: ArtifactType,
  val reference: String,
  val versions: Set<ArtifactVersionSummary> = emptySet()
)

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(value = ["name", "version", "state"])
data class ArtifactVersionSummary(
  val version: String,
  val displayName: String,
  val environments: Set<ArtifactSummaryInEnvironment>,
  val build: BuildMetadata? = null,
  val git: GitMetadata? = null,
  val createdAt: Instant? = null,
  val lifecycleSteps: List<LifecycleStep> = emptyList()
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
  val pinned: ActionMetadata? = null,
  val vetoed: ActionMetadata? = null,
  val constraints: List<ConstraintSummary> = emptyList(),
  val compareLink: String? = null,
  val verifications : List<VerificationSummary> = emptyList()
)

data class VerificationSummary(
  val id: String,
  val type: String,
  val status: String,
  val startedAt: Instant? = null,
  val completedAt: Instant? = null,
  val link: String? = null
) {
  constructor(v: Verification, s: ActionState) :
    this(v.id, v.type, s.status.toString(), s.startedAt, s.endedAt, s.link)
}

data class ActionMetadata(
  val at: Instant,
  val by: String?,
  val comment: String?
)

@JsonInclude(Include.NON_NULL)
data class ConstraintSummary(
  val type: String,
  val status: ConstraintStatus,
  val startedAt: Instant? = null,
  val judgedBy: String? = null,
  val judgedAt: Instant? = null,
  val comment: String? = null,
  val attributes: ConstraintStateAttributes? = null
)

abstract class ConstraintMetadata()

data class DependOnConstraintMetadata(
  val environment: String
) : ConstraintMetadata()

data class AllowedTimesConstraintMetadata(
  val windows: List<TimeWindowNumeric>,
  val timezone: String? = null
) : ConstraintMetadata() {
  constructor(constraint: TimeWindowConstraint): this(constraint.windowsNumeric, constraint.tz)
}
