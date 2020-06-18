package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.DockerVersioningStrategy
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.BRANCH_JOB_COMMIT_BY_JOB
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_JOB_COMMIT_BY_JOB
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_JOB_COMMIT_BY_SEMVER
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_TAG
import com.netflix.spinnaker.keel.api.artifacts.VersioningStrategy

data class DockerArtifact(
  override val name: String,
  override val deliveryConfigName: String? = null,
  override val reference: String = name,
  val tagVersionStrategy: TagVersionStrategy = SEMVER_TAG,
  val captureGroupRegex: String? = null,
  override val versioningStrategy: VersioningStrategy = DockerVersioningStrategy(tagVersionStrategy, captureGroupRegex)
) : DeliveryArtifact() {
  override val type = ArtifactType.docker
  val organization: String = name.substringBefore('/')
  val image: String = name.substringAfter('/')

  fun hasBuild(): Boolean {
    return tagVersionStrategy in listOf(BRANCH_JOB_COMMIT_BY_JOB, SEMVER_JOB_COMMIT_BY_JOB, SEMVER_JOB_COMMIT_BY_SEMVER)
  }

  fun hasCommit(): Boolean {
    return tagVersionStrategy in listOf(BRANCH_JOB_COMMIT_BY_JOB, SEMVER_JOB_COMMIT_BY_JOB, SEMVER_JOB_COMMIT_BY_SEMVER)
  }
}
