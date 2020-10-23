package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.VersioningStrategy
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.plugins.SupportedArtifact
import com.netflix.spinnaker.keel.api.plugins.SupportedVersioningStrategy
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.parseAppVersionOrNull
import com.netflix.spinnaker.keel.services.ArtifactMetadataService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Built-in keel implementation of [ArtifactSupplier] for Debian artifacts.
 *
 * Note: this implementation currently makes some Netflix-specific assumptions with regards to artifact
 * versions so that it can extract build and commit metadata.
 */
@Component
class DebianArtifactSupplier(
  override val eventPublisher: EventPublisher,
  private val artifactService: ArtifactService,
  override val artifactMetadataService: ArtifactMetadataService
) : BaseArtifactSupplier<DebianArtifact, DebianVersioningStrategy>(artifactMetadataService) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override val supportedArtifact = SupportedArtifact("deb", DebianArtifact::class.java)

  override val supportedVersioningStrategy =
    SupportedVersioningStrategy("deb", DebianVersioningStrategy::class.java)

  override fun publishArtifact(artifact: PublishedArtifact) {
    if (artifact.hasReleaseStatus()) {
      super.publishArtifact(artifact)
    } else {
      log.debug("Ignoring artifact event without release status: $artifact")
    }
  }

  override fun getLatestArtifact(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact): PublishedArtifact? =
    runWithIoContext {
      artifactService.getVersions(artifact.name, artifact.statusesForQuery, DEBIAN)
        .map { version -> "${artifact.name}-$version" }
        .sortedWith(artifact.versioningStrategy.comparator)
        .firstOrNull() // versioning strategies return descending by default... ¯\_(ツ)_/¯
        ?.let { version ->
          artifactService.getArtifact(artifact.name, version.removePrefix("${artifact.name}-"), DEBIAN)
        }
    }

  override fun getArtifactByVersion(artifact: DeliveryArtifact, version: String): PublishedArtifact? =
    runWithIoContext {
      artifactService.getArtifact(artifact.name, version.removePrefix("${artifact.name}-"), DEBIAN)
    }

  override fun getVersionDisplayName(artifact: PublishedArtifact): String {
    // TODO: Frigga and Rocket version parsing are not aligned. We should consolidate.
    val appversion = artifact.version.parseAppVersionOrNull()
    if (appversion != null) {
      if (appversion.version != null) {
        return appversion.version
      } else {
        return artifact.version.removePrefix("${artifact.name}-")
      }
    }
    return artifact.version
  }

  override fun parseDefaultBuildMetadata(artifact: PublishedArtifact, versioningStrategy: VersioningStrategy): BuildMetadata? {
    // attempt to parse helpful info from the appversion.
    // TODO: Frigga and Rocket version parsing are not aligned. We should consolidate.
    return try {
      val appversion = artifact.version.parseAppVersionOrNull()
      if (appversion?.buildNumber != null) {
        return BuildMetadata(id = appversion.buildNumber.toInt())
      }
      return null
    } catch (ex: NumberFormatException) {
      log.warn("parsed appversion.buildNumber for artifact version ${artifact.version} is not a number! ")
      null
    } catch (ex: Exception) {
      log.warn("trying to parse artifact ${artifact.name} with version ${artifact.version} but got an exception", ex)
      null
    }
  }

  override fun parseDefaultGitMetadata(artifact: PublishedArtifact, versioningStrategy: VersioningStrategy): GitMetadata? {
    // attempt to parse helpful info from the appversion.
    // TODO: Frigga and Rocket version parsing are not aligned. We should consolidate.
    val appversion = artifact.version.parseAppVersionOrNull()
    if (appversion?.commit != null) {
      return GitMetadata(commit = appversion.commit)
    }
    return null
  }


  private val DeliveryArtifact.statusesForQuery: List<String>
    get() = statuses.map { it.name }

  override fun shouldProcessArtifact(artifact: PublishedArtifact): Boolean =
    artifact.hasReleaseStatus() && artifact.hasCorrectVersion()


  // Debian Artifacts should contain a releaseStatus in the metadata
  private fun PublishedArtifact.hasReleaseStatus() : Boolean {
    return if (this.metadata.containsKey("releaseStatus") && this.metadata["releaseStatus"] != null) {
      true
    } else {
      log.debug("Ignoring artifact event without release status: $this")
      false
    }
  }

  // Debian Artifacts should not have "local" as a part of their version string
  private fun PublishedArtifact.hasCorrectVersion() : Boolean {
    val appversion = this.version.parseAppVersionOrNull()
    return if (appversion?.buildNumber?.contains("local")!!) {
      log.debug("Ignoring artifact which contains local is its version string: $this")
      false
    } else {
      true
    }
  }
}
