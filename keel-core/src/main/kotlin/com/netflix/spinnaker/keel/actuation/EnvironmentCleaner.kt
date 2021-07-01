package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.config.EnvironmentDeletionConfig
import com.netflix.spinnaker.keel.api.Dependent
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.plugins.supporting
import com.netflix.spinnaker.keel.events.MaxResourceDeletionAttemptsReached
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock
import org.springframework.core.env.Environment as SpringEnvironment

/**
 * A component that periodically checks the database for environments that need to be deleted, and does so.
 */
@Component
@EnableConfigurationProperties(EnvironmentDeletionConfig::class)
class EnvironmentCleaner(
  private val deliveryConfigRepository: DeliveryConfigRepository,
  private val resourceRepository: ResourceRepository,
  private val resourceHandlers: List<ResourceHandler<*, *>>,
  private val springEnv: SpringEnvironment,
  private val config: EnvironmentDeletionConfig,
  private val clock: Clock,
  private val eventPublisher: ApplicationEventPublisher
) {
  companion object {
    private val log by lazy { LoggerFactory.getLogger(EnvironmentCleaner::class.java) }
  }

  private val enabled: Boolean
    get() = springEnv.getProperty("keel.environment-deletion.enabled", Boolean::class.java, true)

  private val isDryRun: Boolean
    get() = springEnv.getProperty("keel.environment-deletion.dryRun", Boolean::class.java, false)

  fun cleanupEnvironment(environment: Environment) {
    if (!enabled) return

    val environmentDetails = with(environment) {
      "Environment ${environment.name}" +
        if (repoKey != null && branch != null) " associated with repository $repoKey and branch $branch" else ""
    }

    // We only delete the environment once all resources have been deleted
    if (environment.resources.isEmpty()) {
      log.debug("Deleting $environmentDetails")
      try {
        // This will cascade-delete the record from environment_deletion
        deliveryConfigRepository.deleteEnvironment(
          deliveryConfigName = environment.deliveryConfigName
            ?: error("Missing delivery config name for environment ${environment.name}"),
          environmentName = environment.name
        )
        log.debug("Successfully deleted $environmentDetails")
      } catch (e: Exception) {
        log.error("Error deleting $environmentDetails", e)
      }
      return
    }

    log.debug("Environment ${environment.name} in application ${environment.application} still has attached resources.")
    val resource = environment.resourcesSortedByDependencies.first()
    if (isDryRun) {
      log.debug("Skipping deletion of resource ${resource.id} as dry-run is enabled.")
      return
    }

    log.debug("Deleting first candidate resource from environment ${environment.name}: ${resource.id}")
    val attempts = resourceRepository.countDeletionAttempts(resource)
    if (attempts >= config.maxResourceDeletionAttempts) {
      log.error("Maximum deletion attempts (${config.maxResourceDeletionAttempts}) reached for resource ${resource.id}. Will not retry.")
      eventPublisher.publishEvent(MaxResourceDeletionAttemptsReached(resource, config.maxResourceDeletionAttempts, clock))
      return
    }

    try {
      val plugin = resourceHandlers.supporting(resource.kind)
      runBlocking {
        plugin.delete(resource)
      }
      // This will cascade-delete the record in the environment_resource table
      resourceRepository.delete(resource.id)
      log.debug("Successfully deleted resource ${resource.name} of kind ${resource.kind}")
    } catch (e: Exception) {
      try {
        resourceRepository.incrementDeletionAttempts(resource)
      } catch (e: Exception) {
        log.warn("Error incrementing resource deletion attempts for ${resource.id}: $e")
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : ResourceSpec, R : Any> ResourceHandler<S, R>.delete(
    resource: Resource<*>
  ): List<Task> =
    delete(resource as Resource<S>)
}