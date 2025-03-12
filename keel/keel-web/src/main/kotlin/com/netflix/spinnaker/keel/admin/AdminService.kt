package com.netflix.spinnaker.keel.admin

import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.StatefulConstraint
import com.netflix.spinnaker.keel.actuation.ExecutionSummary
import com.netflix.spinnaker.keel.actuation.ExecutionSummaryService
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.OVERRIDE_FAIL
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.plugins.supporting
import com.netflix.spinnaker.keel.core.api.ApplicationSummary
import com.netflix.spinnaker.keel.core.api.PromotionStatus.CURRENT
import com.netflix.spinnaker.keel.exceptions.NoSuchEnvironmentException
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.front50.Front50Service
import com.netflix.spinnaker.keel.front50.model.ManagedDeliveryConfig
import com.netflix.spinnaker.keel.logging.TracingSupport
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.kork.exceptions.UserException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import kotlin.coroutines.CoroutineContext
@Component
class AdminService(
  private val repository: KeelRepository,
  private val actuationPauser: ActuationPauser,
  private val diffFingerprintRepository: DiffFingerprintRepository,
  private val artifactSuppliers: List<ArtifactSupplier<*, *>>,
  private val front50Cache: Front50Cache,
  private val front50Service: Front50Service,
  private val executionSummaryService: ExecutionSummaryService,
  private val publisher: ApplicationEventPublisher,
  private val clock: Clock
) : CoroutineScope {
  override val coroutineContext: CoroutineContext = Dispatchers.IO

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
  fun forceConstraintReevaluation(application: String, environment: String, reference: String, version: String, type: String? = null) {
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
          repository.deleteConstraintState(deliveryConfig.name, environment, reference, version, constraint.type)
        }
      }
    }
  }

  @Scheduled(fixedDelayString = "\${keel.artifact-metadata-backfill.frequency:PT1H}")
  fun scheduledBackfillArtifactMetadata() {
    val startTime = clock.instant()
    val job = launch(TracingSupport.blankMDC) {
      supervisorScope {
        backfillArtifactMetadata()
      }
    }
    runBlocking { job.join() }
    log.info("Back-filled artifact metadata in ${Duration.between(startTime, clock.instant()).seconds} seconds")
  }

  /**
   * Updates last 10 artifact's versions with the corresponding metadata, if available, by type [deb/docker/npm]
   */
  fun backfillArtifactMetadataAsync(age: Duration) {
    launch(TracingSupport.blankMDC) {
      backfillArtifactMetadata(age)
    }
  }

  /**
   * Updates last 10 artifact's versions with the corresponding metadata, if available, by type [deb/docker/npm]
   */
  suspend fun backfillArtifactMetadata(age: Duration = Duration.ofHours(3)) {
    log.debug("Starting to back-fill old artifacts versions with artifact metadata...")
    val versions = repository
      // only check versions that are < 3 hours old, and probably nothing changes after one hour
      .getVersionsWithoutMetadata(100, age)
    versions.forEach { artifactVersion ->
      log.debug("Evaluating version ${artifactVersion.version} as candidate to back-fill metadata")
      try {
        log.debug("Fetching artifact metadata for version $artifactVersion")
        val artifactSupplier = artifactSuppliers.supporting(artifactVersion.type)
        val artifactMetadata = artifactSupplier.getArtifactMetadata(artifactVersion)
        if (artifactMetadata != null) {
          log.debug("Storing updated metadata for version $artifactVersion: $artifactMetadata")
          repository.updateArtifactMetadata(artifactVersion, artifactMetadata)
        }
      } catch (ex: Exception) {
        log.error("error trying to get artifact by version or its metadata for version $artifactVersion", ex)
      }
    }
    delay(
      Duration.ofSeconds(1).toMillis()
    ) // sleep a little in-between versions to cut the instance where this runs some slack
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
    val currentVersion = repository.getArtifactVersionsByStatus(deliveryConfig, environment, listOf(CURRENT))
      .firstOrNull { it.reference == artifactReference }

    if (currentVersion == null) {
      log.warn("forcing application $application artifact $artifactReference version $version to SKIPPED even though there is no version in CURRENT state")
    }

    // Mark as skipped
    repository.markAsSkipped(deliveryConfig, artifact, version, environment, currentVersion?.version)
  }

  fun forceFailVerifications(
    application: String,
    environmentName: String,
    artifactReference: String,
    version: String,
    verificationId: String
  ) {
    val deliveryConfig = repository.getDeliveryConfigForApplication(application)
    val context = ArtifactInEnvironmentContext(
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

    repository.updateActionState(context, verification, OVERRIDE_FAIL)
  }

  /**
   * Force a refresh of the application cache.
   */
  fun refreshApplicationCache() {
    front50Cache.primeCaches()
  }

  /**
   * Migrates all currently known managed applications from an import pipeline to the native git integration.
   */
  suspend fun migrateImportPipelinesToGitIntegration() {
    val configs = repository.allDeliveryConfigs()
    var migrated = 0
    val startTime = clock.instant()
    log.debug("Retrieved ${configs.size} delivery configs for git integration migration")

    configs.forEach { config ->
      val app = try {
        front50Cache.applicationByName(config.application)
      } catch (e: Exception) {
        null
      }

      if (app == null) {
        log.debug("App ${config.application} not found. Skipping git integration migration.")
        return@forEach
      }

      if (app.managedDelivery?.importDeliveryConfig == true) {
        log.debug("App ${app.name} already configured for git integration. Skipping migration.")
        return@forEach
      }

      val importPipeline = try {
        front50Cache.pipelinesByApplication(app.name).find {
          it.stages.any { stage -> stage.type == "importDeliveryConfig" }
        }
      } catch (e: Exception) {
        log.error("Failed to retrieve pipelines for app ${config.application}. Skipping migration.")
        return@forEach
      }

      if (importPipeline == null) {
        log.debug("No import pipeline found for app ${app.name}. Skipping migration.")
        return@forEach
      }

      val disabled = importPipeline.disabled || importPipeline.triggers.none { trigger -> trigger.enabled }
      if (disabled) {
        log.debug("Import pipeline [${importPipeline.name}] found for app ${app.name} (${config.serviceAccount}), but already disabled. Skipping migration.")
        return@forEach
      }

      log.debug("Enabling git integration for app ${config.application} in Front50.")
      try {
        val updatedApp = app.copy(managedDelivery = ManagedDeliveryConfig(importDeliveryConfig = true))
        front50Service.updateApplication(app.name, config.serviceAccount, updatedApp)
      } catch (e: Exception) {
        log.error("Failed to enable git integration for app ${config.application} in Front50. Skipping disabling pipeline.")
        return@forEach
      }

      log.debug("Disabling import pipeline [${importPipeline.name}] for app ${config.application} in Front50.")
      try {
        val updatedPipeline = importPipeline.copy(disabled = true)
        front50Service.updatePipeline(importPipeline.id, updatedPipeline, config.serviceAccount)
      } catch (e: Exception) {
        log.error("Failed to disable import pipeline [${importPipeline.name}] for app ${config.application} in Front50")
        return@forEach
      }

      migrated++
    }
    val duration = Duration.between(startTime, clock.instant())
    log.debug("Migrated $migrated/${configs.size} apps (${configs.size - migrated} skipped) in ${duration.toSeconds()} seconds")
  }

  fun getTaskSummary(id: String): ExecutionSummary =
      executionSummaryService.getSummary(id)
}
