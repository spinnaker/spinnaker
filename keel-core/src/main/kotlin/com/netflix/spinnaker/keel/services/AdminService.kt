package com.netflix.spinnaker.keel.services

import com.netflix.spinnaker.keel.api.StatefulConstraint
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.DOCKER
import com.netflix.spinnaker.keel.api.artifacts.NPM
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.plugins.supporting
import com.netflix.spinnaker.keel.api.verification.VerificationContext
import com.netflix.spinnaker.keel.core.api.ApplicationSummary
import com.netflix.spinnaker.keel.exceptions.NoSuchEnvironmentException
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.kork.exceptions.UserException
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.lang.Thread.sleep

@Component
class AdminService(
  private val repository: KeelRepository,
  private val actuationPauser: ActuationPauser,
  private val diffFingerprintRepository: DiffFingerprintRepository,
  private val artifactSuppliers: List<ArtifactSupplier<*, *>>,
  private val publisher: ApplicationEventPublisher
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
   * For each artifact type [deb/docker/npm], run back-fill script to in case there's some missing data
   */
  @Scheduled(fixedDelayString = "\${keel.artifact-metadata-backfill.frequency:PT1H}")
  fun periodicallyBackFillArtifactMetadata(){
    listOf(DEBIAN, NPM, DOCKER)
      .forEach{
        type ->  backFillArtifactMetadata(type)
      }
  }

  /**
   * Updates last 10 artifact's versions with the corresponding metadata, if available, by type [deb/docker/npm]
   */
  fun backFillArtifactMetadata(type: String) {
    val artifactSupplier = artifactSuppliers.supporting(type)
    log.debug("Starting to back-fill old artifacts versions with artifact metadata...")
    // 1. get all registered artifacts
    val deliveryArtifacts = repository.getAllArtifacts(type = type)
    // 2. for each artifact, fetch all versions
    deliveryArtifacts.forEach { artifact ->
      val versions = repository.artifactVersions(artifact, 10)
      versions.forEach { artifactVersion ->
        sleep(5000) // sleep a little in-between versions to cut the instance where this runs some slack
        log.debug("Evaluating $artifact version ${artifactVersion.version} as candidate to back-fill metadata")
        // don't update if metadata is already exists
        if (artifactVersion.gitMetadata == null || artifactVersion.buildMetadata == null) {
          runBlocking {
            try {
              // 3. for each version, get and store artifact metadata
              log.debug("Fetching artifact metadata for $artifact version $artifactVersion")
              val artifactMetadata = artifactSupplier.getArtifactMetadata(artifactVersion)
              if (artifactMetadata != null) {
                log.debug("Storing updated metadata for $artifact version $artifactVersion: $artifactMetadata")
                repository.updateArtifactMetadata(artifactVersion, artifactMetadata)
              }
            } catch (ex: Exception) {
              log.error("error trying to get artifact by version or its metadata for artifact ${artifact.name}, and version $artifactVersion", ex)
            }
          }
        }
      }
    }
  }

  /**
   * Mark artifact [version] as SKIPPED in [environment]
   *
   * Preconditions:
   *   - there is a delivery config in the repository that corresponds to [application]
   *   - config has an environment named [environment]
   *   - config has an artifact with reference [artifactReference]
   *   - there is a version of artifact with reference [artifactReference], in environment [environment], with a status of CURRENT
   *
   * Postconditions:
   *   - the artifact version [version] record is updated with:
   *      * promotion_status set to skipped
   *      * replaced_by set to the version that is CURRENT
   *      * replaced_at set to now
   */
  fun forceSkipArtifactVersion(application: String, environment: String, artifactReference: String, version: String) {
    val deliveryConfig = repository.getDeliveryConfigForApplication(application)
    val artifact = deliveryConfig.matchingArtifactByReference(artifactReference)
      ?: throw UserException("application $application contains no artifact ref $artifactReference. Artifact references are: ${deliveryConfig.artifacts.map { it.reference }}")

     // Identify the current version in the environment
    val currentVersion = repository.getCurrentArtifactVersions(deliveryConfig, environment)
      .firstOrNull { it.reference == artifactReference }

    if(currentVersion == null) {
      log.warn("forcing application $application artifact $artifactReference version $version to SKIPPED even though there is no version in CURRENT state")
    }

    // Mark as skipped
    repository.markAsSkipped(deliveryConfig, artifact, version, environment, currentVersion?.version)
  }

  fun forceFailVerifications(application: String, environmentName: String, artifactReference: String, version: String, verificationId: String) {
    val deliveryConfig = repository.getDeliveryConfigForApplication(application)
    val context = VerificationContext(
      deliveryConfig = deliveryConfig,
      environmentName = environmentName,
      artifactReference = artifactReference,
      version = version
    )

    val environment = deliveryConfig.environments
      .firstOrNull { it.name == environmentName }
      ?: throw UserException("application $application has no environment named $environmentName. Names are: ${deliveryConfig.environments.map { it.name }}")

    val verification = environment.verifyWith
      .firstOrNull { it.id == verificationId }
      ?: throw UserException("application $application in environment $environmentName has no verification with ID $verificationId. IDs are: ${environment.verifyWith.map { it.id }}")

    repository.updateState(context, verification, ConstraintStatus.OVERRIDE_FAIL)
  }
}
