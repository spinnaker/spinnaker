package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.config.ArtifactConfig
import com.netflix.spinnaker.keel.activation.ApplicationDown
import com.netflix.spinnaker.keel.activation.ApplicationUp
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.events.ArtifactPublishedEvent
import com.netflix.spinnaker.keel.api.events.ArtifactRegisteredEvent
import com.netflix.spinnaker.keel.api.events.ArtifactSyncEvent
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.plugins.supporting
import com.netflix.spinnaker.keel.config.ArtifactRefreshConfig
import com.netflix.spinnaker.keel.exceptions.InvalidSystemStateException
import com.netflix.spinnaker.keel.lifecycle.LifecycleEvent
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventScope.PRE_DEPLOYMENT
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.RUNNING
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType.BUILD
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionUpdated
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

@EnableConfigurationProperties(ArtifactConfig::class, ArtifactRefreshConfig::class)
@Component
class ArtifactListener(
  private val repository: KeelRepository,
  private val publisher: ApplicationEventPublisher,
  private val artifactSuppliers: List<ArtifactSupplier<*, *>>,
  private val artifactConfig: ArtifactConfig,
  private val artifactRefreshConfig: ArtifactRefreshConfig
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val enabled = AtomicBoolean(false)

  @EventListener(ApplicationUp::class)
  fun onApplicationUp() {
    log.info("Application up, enabling scheduled artifact syncing")
    enabled.set(true)
  }

  @EventListener(ApplicationDown::class)
  fun onApplicationDown() {
    log.info("Application down, disabling scheduled artifact syncing")
    enabled.set(false)
  }

  @EventListener(ArtifactPublishedEvent::class)
  fun onArtifactPublished(event: ArtifactPublishedEvent) {
    log.debug("Received artifact published event: {}", event)
    event
      .artifacts
      .filter { it.type.toLowerCase() in artifactTypeNames }
      .forEach { artifact ->
        if (repository.isRegistered(artifact.name, artifact.artifactType)) {
          val artifactSupplier = artifactSuppliers.supporting(artifact.artifactType)
          if (artifactSupplier.shouldProcessArtifact(artifact)) {
            log.info("Registering version {} (status={}) of {} artifact {}",
              artifact.version, artifact.status, artifact.type, artifact.name)

            enrichAndStore(artifact, artifactSupplier)
              .also { wasAdded ->
                if (wasAdded) {
                  publisher.publishEvent(ArtifactVersionUpdated(artifact.name, artifact.artifactType))
                }
              }
          } else {
            log.debug("Artifact $artifact shouldn't be processed due to supplier limitations. Ignoring this artifact version.")
          }
        } else {
          log.debug("Artifact $artifact is not registered. Ignoring new artifact version.")
        }
      }
  }

  /**
   * Finds the delivery configs that are using an artifact,
   * and publishes a build lifecycle event for them.
   */
  fun publishBuildLifecycleEvent(artifact: PublishedArtifact) {
    log.debug("Publishing build lifecycle event for published artifact $artifact")
    artifact.buildMetadata
      ?.let { buildMetadata ->

        val data = mutableMapOf(
          "buildNumber" to artifact.metadata["buildNumber"]?.toString(),
          "commitId" to artifact.metadata["commitId"]?.toString(),
          "buildMetadata" to buildMetadata
        )

        repository
          .getAllArtifacts(artifact.artifactType, artifact.name)
          .forEach { deliveryArtifact ->
            deliveryArtifact.deliveryConfigName?.let { configName ->
              log.debug("Publishing build lifecycle event for delivery artifact $deliveryArtifact")
              data["application"] = repository.getDeliveryConfig(configName).application

              publisher.publishEvent(LifecycleEvent(
                scope = PRE_DEPLOYMENT,
                deliveryConfigName = configName,
                artifactReference = deliveryArtifact.reference,
                artifactVersion = artifact.version,
                type = BUILD,
                id = "build-${artifact.version}",
                // the build has already started, and is maybe complete.
                // We use running to convey that to users, and allow the [BuildLifecycleMonitor] to immediately
                // update the status
                status = RUNNING,
                text = "Monitoring build for ${artifact.version}",
                link = buildMetadata.uid,
                data = data,
                timestamp = buildMetadata.startedAtInstant,
                startMonitoring = true
              ))
            }
          }
      }
  }

  /**
   * Fetch latest version of an artifact after it is registered.
   */
  @EventListener(ArtifactRegisteredEvent::class)
  fun onArtifactRegisteredEvent(event: ArtifactRegisteredEvent) {
    val artifact = event.artifact

    if (repository.artifactVersions(artifact, artifactConfig.defaultMaxConsideredVersions).isEmpty()) {
      val artifactSupplier = artifactSuppliers.supporting(artifact.type)

      val latestArtifact = runBlocking {
        log.debug("Retrieving latest version of registered artifact {}", artifact)
        artifactSupplier.getLatestArtifact(artifact.deliveryConfig, artifact)
      }

      if (latestArtifact != null) {
        log.debug("Storing latest version {} (status={}) for registered artifact {}", latestArtifact.version, latestArtifact.status, artifact)
        enrichAndStore(latestArtifact, artifactSupplier)
      } else {
        log.warn("No artifact versions found for ${artifact.type}:${artifact.name}")
      }
    }
  }

  @EventListener(ArtifactSyncEvent::class)
  fun triggerArtifactSync(event: ArtifactSyncEvent) {
    if (event.controllerTriggered) {
      log.info("Fetching latest ${artifactRefreshConfig.limit} version(s) of all registered artifacts...")
    }
    syncLastLimitArtifactVersions()
  }

  /**
   * For each registered artifact, get the last [artifactRefreshConfig.limit] versions, and persist if it's newer than what we have.
   */
  @Scheduled(fixedDelayString = "\${keel.artifact-refresh.frequency:PT6H}")
  fun syncLastLimitArtifactVersions() {
    if (enabled.get()) {
      runBlocking {
        log.debug("Syncing last ${artifactRefreshConfig.limit} artifact version(s)...")
        repository.getAllArtifacts().forEach { artifact ->
          launch {
            val lastStoredVersions = repository.artifactVersions(artifact, artifactRefreshConfig.limit)
            val currentVersions = lastStoredVersions.map { it.version }
              log.debug("Last recorded versions of $artifact: $currentVersions")

            val artifactSupplier = artifactSuppliers.supporting(artifact.type)
            val latestAvailableVersions = artifactSupplier.getLatestArtifacts(artifact.deliveryConfig, artifact, artifactRefreshConfig.limit)
            log.debug("Latest available versions of $artifact: ${latestAvailableVersions.map { it.version }}")

            val newVersions = latestAvailableVersions
              .filterNot { currentVersions.contains(it.version) }
              .filter { artifactSupplier.shouldProcessArtifact(it) }
            if (newVersions.isNotEmpty()) {
              log.debug("$artifact has a missing version(s) ${newVersions.map { it.version }}, persisting.")
              newVersions.forEach { enrichAndStore(it, artifactSupplier) }
            } else {
              log.debug("No new versions to persist for $artifact")
            }
          }
        }
      }
    }
  }

  /**
   * Normalizes an artifact by calling [PublishedArtifact.normalized],
   * enriches it by adding metadata,
   * creates the appropriate build lifecycle event,
   * and stores in the database
   */
  private fun enrichAndStore(artifact: PublishedArtifact, supplier: ArtifactSupplier<*,*>): Boolean {
    val enrichedArtifact = supplier.addMetadata(artifact.normalized())
    publishBuildLifecycleEvent(enrichedArtifact)
    return repository.storeArtifactVersion(enrichedArtifact)
  }

  /**
   * Returns a copy of the [PublishedArtifact] with the git and build metadata populated, if available.
   */
  private fun ArtifactSupplier<*, *>.addMetadata(artifact: PublishedArtifact): PublishedArtifact {
    // only add metadata if either build or git metadata is null
    if (artifact.buildMetadata == null || artifact.gitMetadata == null) {
      val artifactMetadata = runBlocking {
        try {
          getArtifactMetadata(artifact)
        } catch (ex: Exception) {
          log.error("Could not fetch artifact metadata for name ${artifact.name} and version ${artifact.version}", ex)
          null
        }
      }
      return artifact.copy(gitMetadata = artifactMetadata?.gitMetadata, buildMetadata = artifactMetadata?.buildMetadata)
    }
    return artifact
  }

  private val DeliveryArtifact.deliveryConfig: DeliveryConfig
    get() = this.deliveryConfigName
      ?.let { repository.getDeliveryConfig(it) }
      ?: throw InvalidSystemStateException("Delivery config name missing in artifact object")

  private val PublishedArtifact.artifactType: ArtifactType
    get() = artifactTypeNames.find { it == type.toLowerCase() }
      ?.let { type.toLowerCase() }
      ?: throw InvalidSystemStateException("Unable to find registered artifact type for '$type'")

  private val artifactTypeNames by lazy {
    artifactSuppliers.map { it.supportedArtifact.name }
  }
}
