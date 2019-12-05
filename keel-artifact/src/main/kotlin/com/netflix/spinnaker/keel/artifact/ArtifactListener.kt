package com.netflix.spinnaker.keel.artifact

import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.ArtifactStatus
import com.netflix.spinnaker.keel.api.ArtifactStatus.RELEASE
import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.ArtifactType.DOCKER
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.events.ArtifactEvent
import com.netflix.spinnaker.keel.events.ArtifactRegisteredEvent
import com.netflix.spinnaker.keel.events.ArtifactSyncEvent
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.VERSION_COMPARATOR
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionUpdated
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ArtifactListener(
  private val artifactRepository: ArtifactRepository,
  private val artifactService: ArtifactService,
  private val clouddriverService: CloudDriverService,
  private val publisher: ApplicationEventPublisher
) {
  @EventListener(ArtifactEvent::class)
  fun onArtifactEvent(event: ArtifactEvent) {
    log.debug("Received artifact event: {}", event)
    event
      .artifacts
      .filter { it.type.toUpperCase() in artifactTypeNames }
      .forEach { korkArtifact ->
        val artifact = korkArtifact.toDeliveryArtifact()
        if (artifactRepository.isRegistered(artifact.name, artifact.type)) {
          val version: String
          val status: ArtifactStatus
          when (artifact.type) {
            DEB -> {
              version = "${korkArtifact.name}-${korkArtifact.version}"
              status = debStatus(korkArtifact)
            }
            DOCKER -> {
              version = "${korkArtifact.name}:${korkArtifact.version}"
              status = RELEASE // todo eb: should we default? should we re-think status? should status be null?
            }
            else -> throw UnsupportedArtifactTypeException(korkArtifact.type)
          }
          log.info("Registering version {} ({}) of {} {}", version, status, artifact.name, artifact.type)
          artifactRepository.store(artifact, version, status)
            .also { wasAdded ->
              if (wasAdded) {
                publisher.publishEvent(ArtifactVersionUpdated(artifact.name, artifact.type))
              }
            }
        }
      }
  }

  @EventListener(ArtifactRegisteredEvent::class)
  fun onArtifactRegisteredEvent(event: ArtifactRegisteredEvent) {
    val artifact = event.artifact
    if (artifactRepository.isRegistered(artifact.name, artifact.type)) {
      log.debug("Artifact {} is already registered", artifact)
    } else {
      log.debug("Registering artifact {}", artifact)
      artifactRepository.register(artifact)
    }

    if (artifactRepository.versions(artifact).isEmpty()) {
      when (artifact.type) {
        DEB -> storeLatestDebVersion(artifact, event.statuses)
        DOCKER -> storeLatestDockerVersion(artifact)
      }
    }
  }

  @EventListener(ArtifactSyncEvent::class)
  fun triggerDebSync(event: ArtifactSyncEvent) {
    if (event.controllerTriggered) {
      log.info("Fetching latest version of all registered artifacts...")
    }
    syncDebArtifactVersions()
  }

  /**
   * For each registered debian artifact, get the last version, and persist if it's newer than what we have.
   */
  // todo eb: should we fetch more than one version?
  // todo eb: do this for docker images also
  @Scheduled(initialDelay = 60000, fixedDelayString = "\${keel.artifact-refresh.frequency:PT6H}")
  fun syncDebArtifactVersions() =
    runBlocking {
      artifactRepository.getAll(DEB).forEach { artifact ->
        launch {
          val lastRecordedVersion: String? = artifactRepository.versions(artifact).firstOrNull()
          val latestVersion: String? = artifactService.getVersions(artifact.name).firstOrNull()
          val latestAppVersion = "${artifact.name}-$latestVersion"
          if (latestVersion != null) {
            val hasNew = when {
              lastRecordedVersion == null -> true
              latestAppVersion != lastRecordedVersion -> {
                listOf(latestAppVersion, lastRecordedVersion).sortedWith(VERSION_COMPARATOR.reversed()).first() == latestAppVersion
              }
              else -> false
            }

            if (hasNew) {
              log.debug("Artifact {} has a missing version {}, persisting..", artifact, latestVersion)
              val version = artifactService.getArtifact(artifact.name, latestVersion)
              artifactRepository.store(artifact, latestAppVersion, debStatus(version))
            }
          }
        }
      }
    }

  /**
   * Grab the latest version which matches the statuses we care about, so the artifact is relevant.
   */
  protected fun storeLatestDebVersion(artifact: DeliveryArtifact, statuses: List<ArtifactStatus>) =
    runBlocking {
      artifactService
        .getVersions(artifact.name, statuses.map { it.toString() })
        .firstOrNull()
        ?.let { firstVersion ->
          val version = "${artifact.name}-$firstVersion"
          val status = debStatus(artifactService.getArtifact(artifact.name, firstVersion))
          log.debug("Storing latest version {} ({}) for registered artifact {}", version, status, artifact)
          artifactRepository.store(artifact, version, status)
        }
    }

  /**
   * Grabs the latest tag and stores it.
   * todo eb: calculate latest based on versioning strategy
   */
  protected fun storeLatestDockerVersion(artifact: DeliveryArtifact) =
    runBlocking {
      clouddriverService
        .findDockerTagsForImage("*", artifact.name)
        .firstOrNull()
        ?.let { firstVersion ->
          log.debug("Storing latest version {} for registered artifact {}", firstVersion, artifact)
          artifactRepository.store(artifact, firstVersion, RELEASE)
        }
    }

  /**
   * Parses the status from a kork artifact, and throws an error if [releaseStatus] isn't
   * present in [metadata]
   */
  private fun debStatus(artifact: Artifact): ArtifactStatus {
    val status = artifact.metadata["releaseStatus"]?.toString()
      ?: throw IllegalStateException("Artifact event received without 'releaseStatus' field")
    return ArtifactStatus.valueOf(status)
  }

  private val artifactTypeNames by lazy { ArtifactType.values().map(ArtifactType::name) }

  private fun Artifact.toDeliveryArtifact(): DeliveryArtifact =
    DeliveryArtifact(name, ArtifactType.valueOf(type.toUpperCase()))

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
