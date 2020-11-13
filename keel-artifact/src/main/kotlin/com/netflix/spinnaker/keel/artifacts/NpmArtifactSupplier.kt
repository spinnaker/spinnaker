package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.NPM
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.SortingStrategy
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.plugins.SupportedArtifact
import com.netflix.spinnaker.keel.api.plugins.SupportedSortingStrategy
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.services.ArtifactMetadataService
import org.springframework.stereotype.Component

/**
 * Built-in keel implementation of [ArtifactSupplier] for NPM artifacts.
 *
 * Note: this implementation currently makes some Netflix-specific assumptions with regards to artifact
 * versions so that it can extract build and commit metadata.
 */
@Component
class NpmArtifactSupplier(
  override val eventPublisher: EventPublisher,
  private val artifactService: ArtifactService,
  override val artifactMetadataService: ArtifactMetadataService
) : BaseArtifactSupplier<NpmArtifact, NpmVersionSortingStrategy>(artifactMetadataService) {

  override val supportedArtifact = SupportedArtifact(NPM, NpmArtifact::class.java)

  override val supportedSortingStrategy =
    SupportedSortingStrategy(NPM, NpmVersionSortingStrategy::class.java)

  override fun getLatestArtifact(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact): PublishedArtifact? =
    runWithIoContext {
      artifactService
        .getVersions(artifact.nameForQuery, artifact.statusesForQuery, NPM)
        // FIXME: this is making N calls to fill in data for each version so we can sort.
        //  Ideally, we'd make a single call to return the list with details for each version.
        .also {
          log.warn("About to make ${it.size} calls to artifact service to retrieve version details...")
        }
        .map { version ->
          artifactService.getArtifact(artifact.name, version, NPM)
        }
        .sortedWith(artifact.sortingStrategy.comparator)
        .firstOrNull() // versioning strategies return descending by default... ¯\_(ツ)_/¯
    }

  override fun getArtifactByVersion(artifact: DeliveryArtifact, version: String): PublishedArtifact? =
    runWithIoContext {
      artifactService.getArtifact(artifact.nameForQuery, version, NPM)
    }

  /**
   * Extracts a version display name from version string using the Netflix semver convention.
   */
  override fun getVersionDisplayName(artifact: PublishedArtifact): String {
    return NetflixVersions.getVersionDisplayName(artifact)
  }

  /**
   * Extracts the build number from the version string using the Netflix semver convention.
   */
  override fun parseDefaultBuildMetadata(artifact: PublishedArtifact, sortingStrategy: SortingStrategy): BuildMetadata? {
    return NetflixVersions.getBuildNumber(artifact)
      ?.let { BuildMetadata(it) }
  }

  /**
   * Extracts the commit hash from the version string using the Netflix semver convention.
   */
  override fun parseDefaultGitMetadata(artifact: PublishedArtifact, sortingStrategy: SortingStrategy): GitMetadata? {
    return NetflixVersions.getCommitHash(artifact)
      ?.let { GitMetadata(it) }
  }


  // The API requires colons in place of slashes to avoid path pattern conflicts
  private val DeliveryArtifact.nameForQuery: String
    get() = name.replace("/", ":")

  private val DeliveryArtifact.statusesForQuery: List<String>
    get() = statuses.map { it.name }

  // Currently, we don't have any limitations for NPM artifact versions
  override fun shouldProcessArtifact(artifact: PublishedArtifact) = true
}
