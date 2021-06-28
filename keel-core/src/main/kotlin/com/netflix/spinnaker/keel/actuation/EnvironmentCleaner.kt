package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment as SpringEnvironment
import org.springframework.stereotype.Component

/**
 * A component that periodically checks the database for environments that need to be deleted, and does so.
 */
@Component
class EnvironmentCleaner(
  private val deliveryConfigRepository: DeliveryConfigRepository,
  private val springEnv: SpringEnvironment
) {
  companion object {
    private val log by lazy { LoggerFactory.getLogger(EnvironmentCleaner::class.java) }

    internal const val CLEANUP_DURATION = "previewEnvironments.cleanupDuration"
  }

  private val enabled: Boolean
    get() = springEnv.getProperty("keel.environment-deletion.enabled", Boolean::class.java, true)

  fun cleanupEnvironment(environment: Environment) {
    if (!enabled) return

    val environmentDetails = with(environment) {
      "Environment ${environment.name}" +
        if (repoKey != null && branch != null) " associated with repository $repoKey and branch $branch" else ""
    }

    log.debug("Deleting $environmentDetails")
    if (environment.resources.isNotEmpty()) {
      log.debug("Environment ${environment.name} in application ${environment.application} still has attached resources.")
      // TODO: implement resource deletion
      return
    }

    try {
      // This will cascade delete any matching record from environment_deletion
      deliveryConfigRepository.deleteEnvironment(
        deliveryConfigName = environment.deliveryConfigName
          ?: error("Missing delivery config name for environment ${environment.name}"),
        environmentName = environment.name
      )
      log.debug("Successfully deleted $environmentDetails")
    } catch (e: Exception) {
      log.error("Error deleting $environmentDetails", e)
    }
  }
}