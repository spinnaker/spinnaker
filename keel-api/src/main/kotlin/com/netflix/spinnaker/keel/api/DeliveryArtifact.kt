package com.netflix.spinnaker.keel.api

enum class ArtifactType {
  deb, docker
}

enum class ArtifactStatus {
  FINAL, CANDIDATE, SNAPSHOT, RELEASE
}

sealed class DeliveryArtifact {
  abstract val name: String
  abstract val type: ArtifactType
  abstract val versioningStrategy: VersioningStrategy
  abstract val reference: String // friendly reference to use within a delivery config
  abstract val deliveryConfigName: String? // the delivery config this artifact is a part of
}

data class DebianArtifact(
  override val name: String,
  override val deliveryConfigName: String? = null,
  override val reference: String = name,
  val statuses: List<ArtifactStatus> = emptyList(),
  override val versioningStrategy: VersioningStrategy = DebianSemVerVersioningStrategy
) : DeliveryArtifact() {
  override val type = ArtifactType.deb
}

data class DockerArtifact(
  override val name: String,
  override val deliveryConfigName: String? = null,
  override val reference: String = name,
  val tagVersionStrategy: TagVersionStrategy = TagVersionStrategy.SEMVER_TAG,
  val captureGroupRegex: String? = null,
  override val versioningStrategy: VersioningStrategy = DockerVersioningStrategy(tagVersionStrategy, captureGroupRegex)
) : DeliveryArtifact() {
  override val type = ArtifactType.docker
}
