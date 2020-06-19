package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.VersioningStrategy
import com.netflix.spinnaker.keel.api.events.ArtifactPublishedEvent
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint

/**
 * Keel plugin interface to be implemented by suppliers of artifact information.
 *
 * The primary responsibility of an [ArtifactSupplier] is to detect new versions of artifacts, using
 * whatever mechanism they choose (e.g. they could receive events from another system,
 * or poll an artifact repository for artifact versions), and notify keel via the [publishArtifact]
 * method, so that the artifact versions can be persisted and evaluated for promotion.
 *
 * Secondarily, [ArtifactSupplier]s are also periodically called to retrieve the latest available
 * version of an artifact. This is so that we don't miss any versions in case of missed or failure
 * to handle events in case of downtime, etc.
 */
interface ArtifactSupplier<T : DeliveryArtifact> : SpinnakerExtensionPoint {
  val eventPublisher: EventPublisher
  val supportedArtifact: SupportedArtifact<T>
  val supportedVersioningStrategies: List<SupportedVersioningStrategy<*>>

  /**
   * Publishes an [ArtifactPublishedEvent] to core Keel so that the corresponding artifact version can be
   * persisted and evaluated for promotion into deployment environments.
   *
   * The default implementation of [publishArtifact] simply publishes the event via the [EventPublisher],
   * and should *not* be overridden by implementors.
   */
  fun publishArtifact(artifactPublishedEvent: ArtifactPublishedEvent) =
    eventPublisher.publishEvent(artifactPublishedEvent)

  /**
   * Returns the latest available version for the given [DeliveryArtifact], represented
   * as a [PublishedArtifact].
   *
   * This function may interact with external systems to retrieve artifact information as needed.
   */
  suspend fun getLatestArtifact(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact): PublishedArtifact?

  /**
   * Given a [PublishedArtifact] supported by this [ArtifactSupplier], return the full representation of
   * a version string, if different from [PublishedArtifact.version].
   */
  fun getFullVersionString(artifact: PublishedArtifact): String = artifact.version

  /**
   * Given a [PublishedArtifact] supported by this [ArtifactSupplier], return the display name for the
   * artifact version, if different from [PublishedArtifact.version].
   */
  fun getVersionDisplayName(artifact: PublishedArtifact): String = artifact.version

  /**
   * Given a [PublishedArtifact] supported by this [ArtifactSupplier], return the [ArtifactStatus] for
   * the artifact, if applicable.
   */
  fun getReleaseStatus(artifact: PublishedArtifact): ArtifactStatus? = null

  /**
   * Given a [PublishedArtifact] and a [VersioningStrategy] supported by this [ArtifactSupplier],
   * return the [BuildMetadata] for the artifact, if available.
   *
   * This function is currently *not* expected to make calls to other systems, but only look into
   * the metadata available within the [PublishedArtifact] object itself.
   */
  fun getBuildMetadata(artifact: PublishedArtifact, versioningStrategy: VersioningStrategy): BuildMetadata? = null

  /**
   * Given a [PublishedArtifact] and a [VersioningStrategy] supported by this [ArtifactSupplier],
   * return the [GitMetadata] for the artifact, if available.
   *
   * This function is currently *not* expected to make calls to other systems, but only look into
   * the metadata available within the [PublishedArtifact] object itself.
   */
  fun getGitMetadata(artifact: PublishedArtifact, versioningStrategy: VersioningStrategy): GitMetadata? = null
}

/**
 * Return the [ArtifactSupplier] supporting the specified artifact type.
 */
fun List<ArtifactSupplier<*>>.supporting(type: ArtifactType) =
  find { it.supportedArtifact.name.toLowerCase() == type.toLowerCase() }
    ?: error("Artifact type '$type' is not supported.")
