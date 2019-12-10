package com.netflix.spinnaker.keel.artifact

import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.ArtifactStatus
import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.ArtifactType.DOCKER
import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DockerArtifact
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.events.ArtifactEvent
import com.netflix.spinnaker.keel.events.ArtifactRegisteredEvent
import com.netflix.spinnaker.keel.events.ArtifactSyncEvent
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
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
        if (artifactRepository.isRegistered(korkArtifact.name, korkArtifact.type())) {
          val artifact = artifactRepository.get(korkArtifact.name, korkArtifact.type())
          val version: String
          var status: ArtifactStatus? = null
          when (artifact) {
            is DebianArtifact -> {
              version = "${korkArtifact.name}-${korkArtifact.version}"
              status = debStatus(korkArtifact)
            }
            is DockerArtifact -> {
              version = "${korkArtifact.name}:${korkArtifact.version}"
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
    artifactRepository.register(artifact)

    if (artifactRepository.versions(artifact).isEmpty()) {
      when (artifact) {
        is DebianArtifact -> storeLatestDebVersion(artifact)
        is DockerArtifact -> storeLatestDockerVersion(artifact)
        else -> throw UnsupportedArtifactTypeException(artifact.type.value())
      }
    }
  }

  @EventListener(ArtifactSyncEvent::class)
  fun triggerDebSync(event: ArtifactSyncEvent) {
    if (event.controllerTriggered) {
      log.info("Fetching latest version of all registered artifacts...")
    }
    syncArtifactVersions()
  }

  /**
   * For each registered debian artifact, get the last version, and persist if it's newer than what we have.
   */
  // todo eb: should we fetch more than one version?
  @Scheduled(initialDelay = 60000, fixedDelayString = "\${keel.artifact-refresh.frequency:PT6H}")
  fun syncArtifactVersions() =
    runBlocking {
      artifactRepository.getAll().forEach { artifact ->
        launch {
          val lastRecordedVersion = getLatestStoredVersion(artifact)
          val latestVersion = when (artifact) {
            is DebianArtifact -> getLatestDeb(artifact)?.let { "${artifact.name}-$it" }
            is DockerArtifact -> getLatestDockerTag(artifact)
            else -> throw UnsupportedArtifactTypeException(artifact.type.value())
          }
          if (latestVersion != null) {
            val hasNew = when {
              lastRecordedVersion == null -> true
              latestVersion != lastRecordedVersion -> {
                artifact.versioningStrategy.comparator.compare(lastRecordedVersion, latestVersion) > 0
              }
              else -> false
            }

            if (hasNew) {
              log.debug("Artifact {} has a missing version {}, persisting..", artifact, latestVersion)
              val status = when (artifact.type) {
                DEB -> debStatus(artifactService.getArtifact(artifact.name, latestVersion.removePrefix("${artifact.name}-")))
                // todo eb: is there a better way to think of docker status?
                DOCKER -> null
              }
              artifactRepository.store(artifact, latestVersion, status)
            }
          }
        }
      }
    }

  private fun getLatestStoredVersion(artifact: DeliveryArtifact): String? =
    artifactRepository.versions(artifact).sortedWith(artifact.versioningStrategy.comparator).firstOrNull()

  private suspend fun getLatestDeb(artifact: DebianArtifact): String? =
    artifactService.getVersions(artifact.name).firstOrNull()

  private suspend fun getLatestDockerTag(artifact: DockerArtifact): String? = clouddriverService
    .findDockerTagsForImage("*", artifact.name)
    .distinct()
    .sortedWith(artifact.versioningStrategy.comparator)
    .firstOrNull()

  /**
   * Grab the latest version which matches the statuses we care about, so the artifact is relevant.
   */
  protected fun storeLatestDebVersion(artifact: DebianArtifact) =
    runBlocking {
      getLatestDeb(artifact)
        ?.let { firstVersion ->
          val version = "${artifact.name}-$firstVersion"
          val status = debStatus(artifactService.getArtifact(artifact.name, firstVersion))
          log.debug("Storing latest version {} ({}) for registered artifact {}", version, status, artifact)
          artifactRepository.store(artifact, version, status)
        }
    }

  /**
   * Grabs the latest tag and stores it.
   */
  protected fun storeLatestDockerVersion(artifact: DockerArtifact) =
    runBlocking {
      getLatestDockerTag(artifact)
        ?.let { firstVersion ->
          log.debug("Storing latest version {} for registered artifact {}", firstVersion, artifact)
          artifactRepository.store(artifact, firstVersion, null)
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

  private fun Artifact.type() = ArtifactType.valueOf(type.toUpperCase())

  private val artifactTypeNames by lazy { ArtifactType.values().map(ArtifactType::name) }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
