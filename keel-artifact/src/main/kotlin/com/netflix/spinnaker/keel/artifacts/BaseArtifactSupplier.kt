package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.artifacts.ArtifactMetadata
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.SortingStrategy
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.services.ArtifactMetadataService
import org.slf4j.LoggerFactory

abstract class BaseArtifactSupplier<A : DeliveryArtifact, V : SortingStrategy>(
  open val artifactMetadataService: ArtifactMetadataService
) : ArtifactSupplier<A, V> {
  override suspend fun getArtifactMetadata(artifact: PublishedArtifact): ArtifactMetadata? {

    val buildNumber = artifact.metadata["buildNumber"]?.toString()
    val commitId = artifact.metadata["commitId"]?.toString()
    if (commitId == null || buildNumber == null) {
      log.debug("either commit id: $commitId or build number $buildNumber is missing, returning null")
      return null
    }

    log.debug("calling to artifact metadata service to get information for artifact: ${artifact.reference}, version: ${artifact.version}, type: ${artifact.type} " +
      "with build number: $buildNumber and commit id: $commitId")
    return try {
      val artifactMetadata = artifactMetadataService.getArtifactMetadata(buildNumber, commitId)
      log.debug("received $artifactMetadata for build $buildNumber and commit $commitId")
      artifactMetadata

    } catch (ex: Exception) {
      log.error("failed to get artifact metadata for build $buildNumber and commit $commitId", ex)
      null
    }
  }

  protected val log by lazy { LoggerFactory.getLogger(javaClass) }
}
