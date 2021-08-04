package com.netflix.spinnaker.keel.preview

import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.ArtifactOriginFilter
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.branchStartsWith
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.test.deliveryConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class PreviewEnvironmentBranchConstraintEvaluatorTests {
  private val artifact = DockerArtifact(
    name = "docker",
    reference = "docker-artifact",
    from = ArtifactOriginFilter(branch = branchStartsWith("feature/"))
  )

  private val matchingVersion = artifact
    .toArtifactVersion("1.0.0")
    .copy(gitMetadata = GitMetadata("asdf", branch = "feature/preview"))

  private val nonMatchingVersion = artifact
    .toArtifactVersion("2.0.0")
    .copy(gitMetadata = GitMetadata("asdf", branch = "a-non-matching-branch"))

  private val versionWithMissingMetadata = artifact
    .toArtifactVersion("3.0.0")
    .copy(gitMetadata = null)

  private val previewEnvironment = Environment(
    name = "test-preview",
    constraints = setOf(PreviewEnvironmentBranchConstraint()),
    isPreview = true
  ).addMetadata(
    "basedOn" to "test",
    "branch" to matchingVersion.branch
  )

  private val deliveryConfig = deliveryConfig(
    env = previewEnvironment
  )

  private val eventPublisher: EventPublisher = mockk()

  private val artifactRepository: ArtifactRepository = mockk {
    every {
      getArtifactVersion(artifact, matchingVersion.version, any())
    } returns matchingVersion

    every {
      getArtifactVersion(artifact, nonMatchingVersion.version, any())
    } returns nonMatchingVersion

    every {
      getArtifactVersion(artifact, versionWithMissingMetadata.version, any())
    } returns versionWithMissingMetadata
  }

  private val subject = PreviewEnvironmentBranchConstraintEvaluator(artifactRepository, eventPublisher)

  @Test
  fun `accepts an artifact version from a branch matching the preview environment`() {
    expectThat(
      subject.canPromote(artifact, matchingVersion.version, deliveryConfig, previewEnvironment)
    ).isTrue()
  }

  @Test
  fun `rejects an artifact version from a branch not matching the preview environment`() {
    expectThat(
      subject.canPromote(artifact, nonMatchingVersion.version, deliveryConfig, previewEnvironment)
    ).isFalse()
  }

  @Test
  fun `rejects an artifact version with missing git metadata`() {
    expectThat(
      subject.canPromote(artifact, versionWithMissingMetadata.version, deliveryConfig, previewEnvironment)
    ).isFalse()
  }
}