package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.ArtifactMetadata
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.ArtifactVersion
import com.netflix.spinnaker.keel.api.artifacts.VersioningStrategy
import com.netflix.spinnaker.keel.api.events.ArtifactPublishedEvent
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.kork.exceptions.SystemException
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
interface ArtifactSupplier<A : DeliveryArtifact, V : VersioningStrategy> : SpinnakerExtensionPoint {
  val eventPublisher: EventPublisher
  val supportedArtifact: SupportedArtifact<A>
  val supportedVersioningStrategy: SupportedVersioningStrategy<V>

  /**
   * Publishes an [ArtifactPublishedEvent] to core Keel so that the corresponding artifact version can be
   * persisted and evaluated for promotion into deployment environments.
   *
   * The default implementation of [publishArtifact] simply publishes the event via the [EventPublisher],
   * and should typically *not* be overridden by implementors.
   */
  @JvmDefault
  fun publishArtifact(artifact: ArtifactVersion) =
    eventPublisher.publishEvent(ArtifactPublishedEvent(listOf(artifact)))

  /**
   * Returns the latest available version for the given [DeliveryArtifact], represented
   * as a [ArtifactVersion].
   *
   * This function may interact with external systems to retrieve artifact information as needed.
   */
  fun getLatestArtifact(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact): ArtifactVersion?

  /**
   * Returns the published artifact [DeliveryArtifact] by version, represented
   * as a [ArtifactVersion].
   *
   * This function may interact with external systems to retrieve artifact information as needed.
   */
  fun getArtifactByVersion(artifact: DeliveryArtifact, version: String): ArtifactVersion?

  /**
   * Given a [ArtifactVersion] supported by this [ArtifactSupplier], return the display name for the
   * artifact version, if different from [ArtifactVersion.version].
   */
  fun getVersionDisplayName(artifact: ArtifactVersion): String = artifact.version

  /**
   * Given a [ArtifactVersion] and a [VersioningStrategy] supported by this [ArtifactSupplier],
   * return the [BuildMetadata] for the artifact, if available.
   *
   * This function is currently *not* expected to make calls to other systems, but only look into
   * the metadata available within the [ArtifactVersion] object itself.
   */
  fun parseDefaultBuildMetadata(artifact: ArtifactVersion, versioningStrategy: VersioningStrategy): BuildMetadata? = null

  /**
   * Given a [ArtifactVersion] and a [VersioningStrategy] supported by this [ArtifactSupplier],
   * return the [GitMetadata] for the artifact, if available.
   *
   * This function is currently *not* expected to make calls to other systems, but only look into
   * the metadata available within the [ArtifactVersion] object itself.
   */
  fun parseDefaultGitMetadata(artifact: ArtifactVersion, versioningStrategy: VersioningStrategy): GitMetadata? = null

  /**
   * Given a [ArtifactVersion] supported by this [ArtifactSupplier],
   * return the [ArtifactMetadata] for the artifact, if available.
   *
   * This function is currently expected to make calls to CI systems.
   */
  suspend fun getArtifactMetadata(artifact: ArtifactVersion): ArtifactMetadata?
}

/**
 * Return the [ArtifactSupplier] supporting the specified artifact type.
 */
fun List<ArtifactSupplier<*, *>>.supporting(type: ArtifactType) =
  find { it.supportedArtifact.name.toLowerCase() == type.toLowerCase() }
    ?: throw UnsupportedArtifactException(type)

class UnsupportedArtifactException(type: ArtifactType) : SystemException("Artifact type '$type' is not supported.")
