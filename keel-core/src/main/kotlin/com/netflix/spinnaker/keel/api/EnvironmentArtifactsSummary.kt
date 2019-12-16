package com.netflix.spinnaker.keel.api

data class EnvironmentArtifactsSummary(
  val name: String,
  val artifacts: Collection<ArtifactVersions>
)

data class ArtifactVersions(
  val name: String,
  val type: ArtifactType,
  val versions: ArtifactVersionStatus
)

data class ArtifactVersionStatus(
  val current: String?,
  val deploying: String?,
  val pending: List<String>,
  val previous: List<String>
)
