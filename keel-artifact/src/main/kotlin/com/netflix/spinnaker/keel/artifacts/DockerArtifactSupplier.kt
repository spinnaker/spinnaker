package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.DOCKER
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.BRANCH_JOB_COMMIT_BY_JOB
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_JOB_COMMIT_BY_JOB
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_JOB_COMMIT_BY_SEMVER
import com.netflix.spinnaker.keel.api.artifacts.SortingStrategy
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.plugins.SupportedArtifact
import com.netflix.spinnaker.keel.api.plugins.SupportedSortingStrategy
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.services.ArtifactMetadataService
import org.springframework.stereotype.Component

/**
 * Built-in keel implementation of [ArtifactSupplier] that does not itself receive/retrieve artifact information
 * but is used by keel's `POST /artifacts/events` API to notify the core of new Docker artifacts.
 */
@Component
class DockerArtifactSupplier(
  override val eventPublisher: EventPublisher,
  private val cloudDriverService: CloudDriverService,
  override val artifactMetadataService: ArtifactMetadataService
) : BaseArtifactSupplier<DockerArtifact, DockerVersionSortingStrategy>(artifactMetadataService) {
  override val supportedArtifact = SupportedArtifact("docker", DockerArtifact::class.java)

  override val supportedSortingStrategy =
    SupportedSortingStrategy("docker", DockerVersionSortingStrategy::class.java)

  private fun findArtifactVersions(artifact: DeliveryArtifact, version: String? = null): List<PublishedArtifact> {
    return runWithIoContext {
      // TODO: we currently don't have a way to derive account information from artifacts,
      //  so we look in all accounts.
      cloudDriverService.findDockerImages(account = "*", repository = artifact.name, tag = version)
        .map { dockerImage ->
          PublishedArtifact(
            name = dockerImage.repository,
            type = DOCKER,
            reference = dockerImage.repository.substringAfter(':', dockerImage.repository),
            version = dockerImage.tag,
            metadata = let {
              if (dockerImage.commitId != null && dockerImage.buildNumber != null) {
                mapOf(
                  "commitId" to dockerImage.commitId,
                  "buildNumber" to dockerImage.buildNumber,
                  "branch" to dockerImage.branch,
                  "createdAt" to dockerImage.date
                )
              } else {
                emptyMap()
              }
            }
          )
        }
    }
  }

  override fun getArtifactByVersion(artifact: DeliveryArtifact, version: String): PublishedArtifact? =
    findArtifactVersions(artifact, version).firstOrNull()

  override fun getLatestArtifact(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact): PublishedArtifact? {
    if (artifact !is DockerArtifact) {
      throw IllegalArgumentException("Only Docker artifacts are supported by this implementation.")
    }

    return runWithIoContext {
      findArtifactVersions(artifact)
        .sortedWith(artifact.sortingStrategy.comparator)
        .firstOrNull()
    }
  }

  override fun parseDefaultBuildMetadata(artifact: PublishedArtifact, sortingStrategy: SortingStrategy): BuildMetadata? {
      if (sortingStrategy.hasBuild()) {
        val regex = Regex("""^.*-h(\d+).*$""")
        val result = regex.find(artifact.version)
        if (result != null && result.groupValues.size == 2) {
          return BuildMetadata(id = result.groupValues[1].toInt())
        }
      }
    return null
  }

  override fun parseDefaultGitMetadata(artifact: PublishedArtifact, sortingStrategy: SortingStrategy): GitMetadata? {
      if (sortingStrategy.hasCommit()) {
        return GitMetadata(commit = artifact.version.substringAfterLast("."))
      }
    return null
  }

  private fun SortingStrategy.hasBuild(): Boolean {
    return (this as? DockerVersionSortingStrategy)
      ?.let { it.strategy in listOf(BRANCH_JOB_COMMIT_BY_JOB, SEMVER_JOB_COMMIT_BY_JOB, SEMVER_JOB_COMMIT_BY_SEMVER) }
      ?: false
  }

  private fun SortingStrategy.hasCommit(): Boolean {
    return (this as? DockerVersionSortingStrategy)
      ?.let { it.strategy in listOf(BRANCH_JOB_COMMIT_BY_JOB, SEMVER_JOB_COMMIT_BY_JOB, SEMVER_JOB_COMMIT_BY_SEMVER) }
      ?: false
  }

  override fun shouldProcessArtifact(artifact: PublishedArtifact): Boolean =
    artifact.version != "latest"

}
