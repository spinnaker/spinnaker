package com.netflix.spinnaker.keel.scm

import com.netflix.spinnaker.keel.api.scm.CommitCreatedEvent
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter.Companion.DEFAULT_MANIFEST_PATH
import com.netflix.spinnaker.keel.igor.ScmService
import com.netflix.spinnaker.keel.persistence.KeelRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Listens to commit events from applications' main source code branch to import their delivery configs from source.
 */
@Component
@ConditionalOnProperty("keel.scm.importDeliveryConfigs", matchIfMissing = true)
class DeliveryConfigImportListener(
  private val repository: KeelRepository,
  private val deliveryConfigImporter: DeliveryConfigImporter,
  private val front50Cache: Front50Cache,
  private val scmService: ScmService
) {
  companion object {
    private val log by lazy { LoggerFactory.getLogger(DeliveryConfigImportListener::class.java) }
  }

  /**
   * Listens to [CommitCreatedEvent] events to catch those that match the default branch
   * associated with any applications configured in Spinnaker.
   *
   * When a match is found, retrieves the delivery config from the target branch, and stores
   * it in the database. This is our main path for supporting SCM integration to monitor
   * delivery config changes in source repos.
   */
  @EventListener(CommitCreatedEvent::class)
  fun handleCommitCreated(event: CommitCreatedEvent) {
    val apps = runBlocking {
      try {
        front50Cache.allApplications().also {
          log.debug("Retrieved ${it.size} applications from Front50")
        }
      } catch (e: Exception) {
        log.error("Error retrieving applications: $e", e)
        null
      }
    } ?: return

    // Hopefully this returns a single matching app, but who knows... ¯\_(ツ)_/¯
    val matchingApps = apps
      .filter { app ->
        app != null
          && app.managedDelivery?.importDeliveryConfig == true
          && event.matchesApplicationConfig(app)
          && event.targetBranch == app.defaultBranch
      }

    if (matchingApps.isEmpty()) {
      log.debug("No applications with a matching default branch found for event: $event")
      return
    }

    log.debug("Processing commit event: $event")
    matchingApps.forEach { app ->
      log.debug("Importing delivery config for app ${app.name} from branch ${event.targetBranch}, commit ${event.commitHash}")

      val newDeliveryConfig = try {
        deliveryConfigImporter.import(
          commitEvent = event,
          manifestPath = DEFAULT_MANIFEST_PATH // TODO: allow location of manifest to be configurable
        )
      } catch (e: Exception) {
        log.error("Error retrieving delivery config: $e", e)
        // TODO: emit event/metric
        return@forEach
      }

      log.info("Creating/updating delivery config for application ${app.name} from branch ${event.targetBranch}")
      repository.upsertDeliveryConfig(newDeliveryConfig)
    }
  }

  private val Application.defaultBranch: String
    get() = runBlocking {
      scmService.getDefaultBranch(
        scmType = repoType ?: error("Missing SCM type in config for application $name"),
        projectKey = repoProjectKey ?: error("Missing SCM project in config for application $name"),
        repoSlug = repoSlug ?: error("Missing SCM repository in config for application $name")
      ).name
    }
}