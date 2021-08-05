package com.netflix.spinnaker.keel.scm

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter.Companion.DEFAULT_MANIFEST_PATH
import com.netflix.spinnaker.keel.igor.ScmService
import com.netflix.spinnaker.keel.igor.getDefaultBranch
import com.netflix.spinnaker.keel.notifications.DeliveryConfigImportFailed
import com.netflix.spinnaker.keel.persistence.DismissibleNotificationRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.telemetry.safeIncrement
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * Listens to commit events from applications' main source code branch to import their delivery configs from source.
 */
@Component
class DeliveryConfigImportListener(
  private val repository: KeelRepository,
  private val deliveryConfigImporter: DeliveryConfigImporter,
  private val notificationRepository: DismissibleNotificationRepository,
  private val front50Cache: Front50Cache,
  private val scmService: ScmService,
  private val springEnv: Environment,
  private val spectator: Registry,
  private val eventPublisher: ApplicationEventPublisher,
  private val clock: Clock
) {
  companion object {
    private val log by lazy { LoggerFactory.getLogger(DeliveryConfigImportListener::class.java) }
    internal const val CODE_EVENT_COUNTER = "importConfig.codeEvent.count"
  }

  private val enabled: Boolean
    get() = springEnv.getProperty("keel.importDeliveryConfigs.enabled", Boolean::class.java, true)

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
    if (!enabled) {
      log.debug("Importing delivery config from source disabled by feature flag. Ignoring commit event: $event")
      return
    }

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
          && event.targetBranch == app.getDefaultBranch(scmService)
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
        ).also {
          event.emitCounterMetric(CODE_EVENT_COUNTER, DELIVERY_CONFIG_RETRIEVAL_SUCCESS, app.name)
        }
      } catch (e: Exception) {
        log.error("Error retrieving delivery config: $e", e)
        event.emitCounterMetric(CODE_EVENT_COUNTER, DELIVERY_CONFIG_RETRIEVAL_ERROR, app.name)
        eventPublisher.publishDeliveryConfigImportFailed(app.name, event, clock.instant())
        return@forEach
      }

      log.info("Creating/updating delivery config for application ${app.name} from branch ${event.targetBranch}")
      repository.upsertDeliveryConfig(newDeliveryConfig)
      notificationRepository.dismissNotification(DeliveryConfigImportFailed::class.java, newDeliveryConfig.application, event.targetBranch)
    }
  }

  private fun CodeEvent.emitCounterMetric(metric: String, extraTags: Collection<Pair<String, String>>, application: String? = null) =
    spectator.counter(metric, metricTags(application, extraTags) ).safeIncrement()
}
