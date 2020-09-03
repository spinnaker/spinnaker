package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.NPM
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.VersioningStrategy
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.plugins.SupportedArtifact
import com.netflix.spinnaker.keel.api.plugins.SupportedVersioningStrategy
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.services.ArtifactMetadataService
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import org.springframework.stereotype.Component

/**
 * Built-in keel implementation of [ArtifactSupplier] that does not itself receive/retrieve artifact information
 * but is used by keel's `POST /artifacts/events` API to notify the core of new NPM artifacts.
 */
@Component
class NpmArtifactSupplier(
  override val eventPublisher: EventPublisher,
  private val artifactService: ArtifactService,
  override val artifactMetadataService: ArtifactMetadataService
) : BaseArtifactSupplier<NpmArtifact, NetflixSemVerVersioningStrategy>(artifactMetadataService) {

  override val supportedArtifact = SupportedArtifact(NPM, NpmArtifact::class.java)

  override val supportedVersioningStrategy =
    SupportedVersioningStrategy(NPM, NetflixSemVerVersioningStrategy::class.java)

  override fun getLatestArtifact(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact): PublishedArtifact? =
    runWithIoContext {
      artifactService
        .getVersions(artifact.nameForQuery, artifact.statusesForQuery, NPM)
        .sortedWith(artifact.versioningStrategy.comparator)
        .firstOrNull() // versioning strategies return descending by default... ¯\_(ツ)_/¯
        ?.let { version ->
          artifactService.getArtifact(artifact.nameForQuery, version, NPM)
        }
    }

  /**
   * Parses the status from a kork artifact, and throws an error if [releaseStatus] isn't
   * present in [metadata]
   */
  override fun getReleaseStatus(artifact: PublishedArtifact): ArtifactStatus {
    val status = artifact.metadata["releaseStatus"]?.toString()
      ?: throw IntegrationException("Artifact metadata does not contain 'releaseStatus' field")
    return ArtifactStatus.valueOf(status)
  }

  /**
   * Extracts a version display name from version string using the Netflix semver convention.
   */
  override fun getVersionDisplayName(artifact: PublishedArtifact): String {
    return NetflixSemVerVersioningStrategy.getVersionDisplayName(artifact)
  }

  /**
   * Extracts the build number from the version string using the Netflix semver convention.
   */
  override fun parseDefaultBuildMetadata(artifact: PublishedArtifact, versioningStrategy: VersioningStrategy): BuildMetadata? {
    return NetflixSemVerVersioningStrategy.getBuildNumber(artifact)
      ?.let { BuildMetadata(it) }
  }

  /**
   * Extracts the commit hash from the version string using the Netflix semver convention.
   */
  override fun parseDefaultGitMetadata(artifact: PublishedArtifact, versioningStrategy: VersioningStrategy): GitMetadata? {

    return NetflixSemVerVersioningStrategy.getCommitHash(artifact)
      ?.let { GitMetadata(it) }
  }


  // The API requires colons in place of slashes to avoid path pattern conflicts
  private val DeliveryArtifact.nameForQuery: String
    get() = name.replace("/", ":")

  private val DeliveryArtifact.statusesForQuery: List<String>
    get() = statuses.map { it.name }
}
