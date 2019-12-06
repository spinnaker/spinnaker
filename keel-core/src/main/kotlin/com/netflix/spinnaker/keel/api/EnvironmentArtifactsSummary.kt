package com.netflix.spinnaker.keel.api

data class EnvironmentArtifactsSummary(
  val name: String,
  val artifacts: Collection<ArtifactVersions>
)

data class ArtifactVersions(
  val name: String,
  val type: ArtifactType,
  val versions: Map<PromotionStatus, List<String>>
)

enum class PromotionStatus {
  PREVIOUS, CURRENT, DEPLOYING, PENDING
}
