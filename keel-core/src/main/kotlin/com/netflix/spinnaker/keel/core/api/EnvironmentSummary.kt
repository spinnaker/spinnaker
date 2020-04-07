package com.netflix.spinnaker.keel.core.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.core.api.PromotionStatus.APPROVED
import com.netflix.spinnaker.keel.core.api.PromotionStatus.CURRENT
import com.netflix.spinnaker.keel.core.api.PromotionStatus.DEPLOYING
import com.netflix.spinnaker.keel.core.api.PromotionStatus.PENDING
import com.netflix.spinnaker.keel.core.api.PromotionStatus.PREVIOUS
import com.netflix.spinnaker.keel.core.api.PromotionStatus.SKIPPED
import com.netflix.spinnaker.keel.core.api.PromotionStatus.VETOED

/**
 * Summarized data about a specific environment, mostly for use by the UI.
 */
@JsonPropertyOrder(value = ["name"])
data class EnvironmentSummary(
  @JsonIgnore val environment: Environment,
  val artifacts: Set<ArtifactVersions>
) {
  val name: String
    get() = environment.name

  val resources: Set<String>
    get() = environment.resources.map { it.id }.toSet()

  fun getArtifactPromotionStatus(artifact: DeliveryArtifact, version: String) =
    artifacts.find { it.name == artifact.name && it.type == artifact.type }
      ?.let {
        when (version) {
          it.versions.current -> CURRENT
          it.versions.deploying -> DEPLOYING
          in it.versions.previous -> PREVIOUS
          in it.versions.approved -> APPROVED
          in it.versions.pending -> PENDING
          in it.versions.vetoed -> VETOED
          in it.versions.skipped -> SKIPPED
          else -> throw IllegalStateException("Unknown promotion status for artifact ${it.type}:${it.name}@$version in environment ${this.name}"
          )
        }
      }
}

data class ArtifactVersions(
  val name: String,
  val type: ArtifactType,
  val reference: String,
  val statuses: Set<ArtifactStatus>,
  val versions: ArtifactVersionStatus
)

data class ArtifactVersionStatus(
  val current: String?,
  val deploying: String?,
  val pending: List<String>,
  val approved: List<String>,
  val previous: List<String>,
  val vetoed: List<String>,
  val skipped: List<String>
)
