package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.artifacts.ArtifactMetadata
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.SortingStrategy
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.igor.artifact.ArtifactMetadataService
import org.slf4j.LoggerFactory

abstract class BaseArtifactSupplier<A : DeliveryArtifact, V : SortingStrategy>(
  open val artifactMetadataService: ArtifactMetadataService
) : ArtifactSupplier<A, V> {
  override suspend fun getArtifactMetadata(artifact: PublishedArtifact): ArtifactMetadata? {

    val buildNumber = artifact.buildNumber
    // We first try to use the PR commit hash (for merge commits) and fall back to the commit hash
    val commitId = artifact.prCommitHash ?: artifact.commitHash
    if (commitId == null || buildNumber == null) {
      log.debug("Either commit id: $commitId or build number $buildNumber is missing, returning null")
      return null
    }
    if (artifact.prCommitHash != null) {
      log.debug("Using PR commit hash to fetch git metadata of artifact $artifact")
    }

    log.debug(
      "Fetching metadata for artifact: ${artifact.reference}, version: ${artifact.version}, type: ${artifact.type} " +
        "with build number: $buildNumber and commit id: $commitId"
    )
    return try {
      val artifactMetadata = artifactMetadataService.getArtifactMetadata(buildNumber, commitId)
      log.debug("Received artifact metadata $artifactMetadata for build $buildNumber and commit $commitId")
      artifactMetadata

    } catch (ex: Exception) {
      log.error("Failed to fetch artifact metadata for build $buildNumber and commit $commitId", ex)
      null
    }
  }

  protected val log by lazy { LoggerFactory.getLogger(javaClass) }
}
