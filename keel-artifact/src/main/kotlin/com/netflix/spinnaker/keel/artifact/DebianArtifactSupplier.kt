package com.netflix.spinnaker.keel.artifact

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.VersioningStrategy
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.plugins.SupportedArtifact
import com.netflix.spinnaker.keel.api.plugins.SupportedVersioningStrategy
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.artifacts.DebianSemVerVersioningStrategy
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import org.springframework.stereotype.Component

/**
 * Built-in keel implementation of [ArtifactSupplier] that does not itself receive/retrieve artifact information
 * but is used by keel's `POST /artifacts/events` API to notify the core of new Debian artifacts.
 */
@Component
class DebianArtifactSupplier(
  override val eventPublisher: EventPublisher,
  private val artifactService: ArtifactService
) : ArtifactSupplier<DebianArtifact, DebianSemVerVersioningStrategy> {
  override val supportedArtifact = SupportedArtifact("deb", DebianArtifact::class.java)

  override val supportedVersioningStrategy =
    SupportedVersioningStrategy("deb", DebianSemVerVersioningStrategy::class.java)

  override suspend fun getLatestArtifact(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact): PublishedArtifact? =
    artifactService
      .getVersions(artifact.name)
      .map { version -> "${artifact.name}-$version" }
      .sortedWith(artifact.versioningStrategy.comparator)
      .firstOrNull() // versioning strategies return descending by default... ¯\_(ツ)_/¯
      ?.let { version ->
        artifactService.getArtifact(artifact.name, version.removePrefix("${artifact.name}-"))
      }

  override fun getFullVersionString(artifact: PublishedArtifact): String =
    "${artifact.name}-${artifact.version}"

  /**
   * Parses the status from a kork artifact, and throws an error if [releaseStatus] isn't
   * present in [metadata]
   */
  override fun getReleaseStatus(artifact: PublishedArtifact): ArtifactStatus {
    val status = artifact.metadata["releaseStatus"]?.toString()
      ?: throw IntegrationException("Artifact metadata does not contain 'releaseStatus' field")
    return ArtifactStatus.valueOf(status)
  }

  override fun getVersionDisplayName(artifact: PublishedArtifact): String {
    val appversion = AppVersion.parseName(artifact.version)
    return if (appversion?.version != null) {
      appversion.version
    } else {
      artifact.version.removePrefix("${artifact.name}-")
    }
  }

  override fun getBuildMetadata(artifact: PublishedArtifact, versioningStrategy: VersioningStrategy): BuildMetadata? {
    // attempt to parse helpful info from the appversion.
    // todo(eb): replace, this is brittle
    val appversion = AppVersion.parseName(artifact.version)
    if (appversion?.buildNumber != null) {
      return BuildMetadata(id = appversion.buildNumber.toInt())
    }
    return null
  }

  override fun getGitMetadata(artifact: PublishedArtifact, versioningStrategy: VersioningStrategy): GitMetadata? {
    // attempt to parse helpful info from the appversion.
    // todo(eb): replace, this is brittle
    val appversion = AppVersion.parseName(artifact.version)
    if (appversion?.commit != null) {
      return GitMetadata(commit = appversion.commit)
    }
    return null
  }
}
