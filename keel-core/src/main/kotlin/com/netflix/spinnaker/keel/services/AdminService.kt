package com.netflix.spinnaker.keel.services

import com.netflix.spinnaker.keel.api.StatefulConstraint
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.plugins.supporting
import com.netflix.spinnaker.keel.core.api.ApplicationSummary
import com.netflix.spinnaker.keel.exceptions.NoSuchEnvironmentException
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.lang.Thread.sleep

@Component
class AdminService(
  private val repository: KeelRepository,
  private val actuationPauser: ActuationPauser,
  private val diffFingerprintRepository: DiffFingerprintRepository,
  private val artifactSuppliers: List<ArtifactSupplier<*, *>>
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  fun deleteApplicationData(application: String) {
    log.debug("Deleting all data for application: $application")
    repository.deleteDeliveryConfigByApplication(application)
  }

  fun getPausedApplications() = actuationPauser.pausedApplications()

  fun getManagedApplications(): Collection<ApplicationSummary> {
    return repository.getApplicationSummaries()
  }

  fun triggerRecheck(resourceId: String) {
    log.info("Triggering a recheck for $resourceId by clearing our record of the diff")
    diffFingerprintRepository.clear(resourceId)
  }

  /**
   * Removes the stored state we have for any stateful constraints for an environment
   * so they will evaluate again
   */
  fun forceConstraintReevaluation(application: String, environment: String, type: String? = null) {
    log.info("[app=$application, env=$environment] Forcing reevaluation of stateful constraints.")
    if (type != null) {
      log.info("[app=$application, env=$environment] Forcing only type $type")
    }

    val deliveryConfig = repository.getDeliveryConfigForApplication(application)
    val env = deliveryConfig.environments.find { it.name == environment }
      ?: throw NoSuchEnvironmentException(environment, application)
    env.constraints.forEach { constraint ->
      if (constraint is StatefulConstraint) {
        if (type == null || type == constraint.type) {
          log.info("[app=$application, env=$environment] Deleting constraint state for ${constraint.type}.")
          repository.deleteConstraintState(deliveryConfig.name, environment, constraint.type)
        }
      }
    }
  }

  /**
   * Updates artifact version records with the corresponding metadata, if available, by type [deb/docker/npm]
   */
  fun backFillArtifactMetadata(type: String) {
    val artifactSupplier = artifactSuppliers.supporting(type)
    log.debug("Starting to back-fill old artifacts versions with artifact metadata...")
    // 1. get all registered artifacts
    val deliveryArtifacts = repository.getAllArtifacts(type)
    // 2. for each artifact, fetch all versions
    deliveryArtifacts.forEach { artifact ->
      val versions = repository.artifactVersions(artifact)
      versions.forEach { version ->
        sleep(5000) // sleep a little in-between versions to cut the instance where this runs some slack
        log.debug("Evaluating $artifact version $version as candidate to back-fill metadata")
        val status = repository.getReleaseStatus(artifact, version)
        val artifactVersion = repository.getArtifactInstance(artifact.name, type, version, status)
        // don't update if metadata is already exists
        if (artifactVersion != null && (artifactVersion.gitMetadata == null || artifactVersion.buildMetadata == null)) {
          runBlocking {
            try {
              // 3. for each version, get and store artifact metadata
              log.debug("Fetching artifact metadata for $artifact version $version")
              val artifactMetadata = artifactSupplier.getArtifactMetadata(artifactVersion)
              if (artifactMetadata != null) {
                log.debug("Storing updated metadata for $artifact version $version: $artifactMetadata")
                repository.updateArtifactMetadata(artifactVersion, artifactMetadata)
              }
            } catch (ex: Exception) {
              log.error("error trying to get artifact by version or its metadata for artifact ${artifact.name}, and version $version", ex)
            }
          }
        }
      }
    }
  }
}
