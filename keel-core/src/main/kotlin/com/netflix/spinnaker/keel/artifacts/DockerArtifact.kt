package com.netflix.spinnaker.keel.artifacts

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.keel.api.artifacts.ArtifactOriginFilterSpec
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.DOCKER
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_TAG
import com.netflix.spinnaker.keel.api.artifacts.SortingStrategy

/**
 * A [DeliveryArtifact] that describes Docker images.
 */
data class DockerArtifact(
  override val name: String,
  override val deliveryConfigName: String? = null,
  override val reference: String = name,
  val tagVersionStrategy: TagVersionStrategy = SEMVER_TAG,
  val captureGroupRegex: String? = null,
  override val from: ArtifactOriginFilterSpec? = null
) : DeliveryArtifact() {
  init {
    require(name.count { it == '/' } <= 1) {
      "Docker image name has more than one slash, which is not Docker convention. Please convert to `organization/image-name` format."
    }
  }

  override val type = DOCKER

  @JsonIgnore
  val organization: String = name.substringBefore('/')

  @JsonIgnore
  val image: String = name.substringAfter('/')

  @JsonIgnore
  override val statuses: Set<ArtifactStatus> = emptySet()

  override val sortingStrategy: SortingStrategy
    get() = if (filteredByBranch || filteredByPullRequest) {
      CreatedAtSortingStrategy
    } else {
      DockerVersionSortingStrategy(tagVersionStrategy, captureGroupRegex)
    }

  override fun toString(): String = super.toString()
}
