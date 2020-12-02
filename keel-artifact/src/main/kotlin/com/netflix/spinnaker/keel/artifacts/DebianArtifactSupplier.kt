package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.igor.artifact.ArtifactService
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.SortingStrategy
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.plugins.SupportedArtifact
import com.netflix.spinnaker.keel.api.plugins.SupportedSortingStrategy
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.parseAppVersionOrNull
import com.netflix.spinnaker.keel.igor.artifact.ArtifactMetadataService
import org.springframework.core.env.Environment
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
  override val artifactMetadataService: ArtifactMetadataService,
  private val springEnv: Environment
) : BaseArtifactSupplier<DebianArtifact, DebianVersionSortingStrategy>(artifactMetadataService) {
  override val supportedArtifact = SupportedArtifact("deb", DebianArtifact::class.java)

  override val supportedSortingStrategy =
    SupportedSortingStrategy("deb", DebianVersionSortingStrategy::class.java)

  private val forceSortByVersion: Boolean
    get() = springEnv.getProperty("keel.artifacts.debian.forceSortByVersion", Boolean::class.java, false)

  override fun getLatestArtifact(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact): PublishedArtifact? =
    runWithIoContext {
      val versions = artifactService.getVersions(artifact.name, artifact.statusesForQuery, DEBIAN)

      val latestVersion = if (forceSortByVersion) {
        versions.sortedWith(DEBIAN_VERSION_COMPARATOR)
          .firstOrNull()
          ?.let {
            artifactService.getArtifact(artifact.name, it, DEBIAN)
          }
      } else {
        versions
          // FIXME: this is making N calls to fill in data for each version so we can sort.
          //  Ideally, we'd make a single call to return the list with details for each version.
          .also {
            log.warn("About to make ${it.size} calls to artifact service to retrieve version details...")
          }
          .map { version ->
            artifactService.getArtifact(artifact.name, version, DEBIAN)
          }
          .sortedWith(artifact.sortingStrategy.comparator)
          .firstOrNull() // versioning strategies return descending by default... ¯\_(ツ)_/¯
      }

      latestVersion
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

  override fun parseDefaultBuildMetadata(artifact: PublishedArtifact, sortingStrategy: SortingStrategy): BuildMetadata? {
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

  override fun parseDefaultGitMetadata(artifact: PublishedArtifact, sortingStrategy: SortingStrategy): GitMetadata? {
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
    val appversion = "${this.name}-${this.version}".parseAppVersionOrNull()
    return if (appversion != null && appversion.buildNumber != null) {
      if (appversion.buildNumber.contains("local")) {
        log.debug("Ignoring artifact which contains local is its version string: $this")
        false
      } else {
        //appversion is not null, and the version does not contains "local"
        true
      }
    } else {
      log.debug("Either appversion or appversion.buildNumber is null. Ignoring this version")
      false
    }
  }
}
