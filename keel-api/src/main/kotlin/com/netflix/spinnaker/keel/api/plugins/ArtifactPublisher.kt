package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.SpinnakerArtifact
import com.netflix.spinnaker.keel.api.events.ArtifactEvent
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint

/**
 * Keel plugin interface to be implemented by publishers of artifact information.
 *
 * The primary responsibility of an [ArtifactPublisher] is to detect new versions of artifacts, using
 * whatever mechanism they choose (e.g. they could receive events from another system,
 * or poll an artifact repository for artifact versions), and notify keel via the [publishArtifact]
 * method, so that the artifact versions can be persisted and evaluated for promotion.
 */
interface ArtifactPublisher<T : DeliveryArtifact> : SpinnakerExtensionPoint {
  val eventPublisher: EventPublisher
  val supportedArtifact: SupportedArtifact<T>
  val supportedVersioningStrategies: List<SupportedVersioningStrategy<*>>

  /**
   * Publishes an [ArtifactEvent] to core Keel so that the corresponding artifact version can be
   * persisted and evaluated for promotion into deployment environments.
   *
   * The default implementation of [publishArtifact] simply publishes the event via the [EventPublisher],
   * and should *not* be overridden by implementors.
   */
  fun publishArtifact(artifactEvent: ArtifactEvent) =
    eventPublisher.publishEvent(artifactEvent)

  /**
   * Returns the latest available version for the given [DeliveryArtifact], represented
   * as a [SpinnakerArtifact].
   *
   * This function may interact with external systems to retrieve artifact information as needed.
   */
  suspend fun getLatestArtifact(artifact: DeliveryArtifact): SpinnakerArtifact?
}

fun List<ArtifactPublisher<*>>.supporting(type: ArtifactType) =
  find { it.supportedArtifact.name == type.name }
    ?: error("Artifact type '$type' is not supported.")
