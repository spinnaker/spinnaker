package com.netflix.spinnaker.keel.scm

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.api.artifacts.Commit
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.Repo
import com.netflix.spinnaker.keel.api.persistence.KeelReadOnlyRepository
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.front50.model.GitRepository
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter
import com.netflix.spinnaker.keel.notifications.DeliveryConfigImportFailed
import com.netflix.spinnaker.keel.persistence.DismissibleNotificationRepository
import com.netflix.spinnaker.keel.telemetry.safeIncrement
import com.netflix.spinnaker.keel.upsert.DeliveryConfigUpserter
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
  private val keelReadOnlyRepository: KeelReadOnlyRepository,
  private val deliveryConfigUpserter: DeliveryConfigUpserter,
  private val deliveryConfigImporter: DeliveryConfigImporter,
  private val notificationRepository: DismissibleNotificationRepository,
  private val front50Cache: Front50Cache,
  private val scmUtils: ScmUtils,
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
  @EventListener(CommitCreatedEvent::class, PrMergedEvent::class)
  fun handleCodeEvent(event: CodeEvent) {
    if (!enabled) {
      log.debug("Importing delivery config from source disabled by feature flag. Ignoring commit event: $event")
      return
    }

    val apps = runBlocking {
      try {
        front50Cache.searchApplicationsByRepo(GitRepository(event.repoType, event.projectKey, event.repoSlug))
          .also {
            log.debug("Retrieved ${it.size} applications from Front50")
          }
      } catch (e: Exception) {
        log.error("Error searching applications: $e", e)
        null
      }
    } ?: return

    // Hopefully this returns a single matching app, but who knows... ¯\_(ツ)_/¯
    val matchingApps = apps
      .filter { app ->
        app != null
          && app.managedDelivery?.importDeliveryConfig == true
          && event.matchesApplicationConfig(app)
          && event.targetBranch == scmUtils.getDefaultBranch(app)
          && keelReadOnlyRepository.isApplicationConfigured(app.name)
      }

    if (matchingApps.isEmpty()) {
      log.debug("No applications with matching SCM config found for event: $event")
      return
    }

    log.debug("Processing commit event: $event")
    matchingApps.forEach { app ->
      log.debug("Importing delivery config for app ${app.name} from branch ${event.targetBranch}, commit ${event.commitHash}")

      // We always want to dismiss the previous notifications, and if needed to create a new one
      notificationRepository.dismissNotification(DeliveryConfigImportFailed::class.java, app.name, event.targetBranch)

      try {
        val deliveryConfig = deliveryConfigImporter.import(
          codeEvent = event,
          manifestPath = app.managedDelivery?.manifestPath
        ).let {
          if (it.serviceAccount == null) {
            it.copy(serviceAccount = app.email)
          } else {
            it
          }
        }
        val gitMetadata = event.commitHash?.let {
          GitMetadata(
            commit = it,
            author = event.authorName,
            project = event.projectKey,
            branch = event.targetBranch,
            repo = Repo(name = event.repoKey),
            commitInfo = Commit(sha = event.commitHash, link = scmUtils.getCommitLink(event), message = event.message)
          )
        }
        log.debug("Creating/updating delivery config for application ${app.name} from branch ${event.targetBranch}")
        deliveryConfigUpserter.upsertConfig(deliveryConfig, gitMetadata)
        log.debug("Delivery config for application ${app.name} updated successfully from branch ${event.targetBranch}")
        event.emitCounterMetric(CODE_EVENT_COUNTER, DELIVERY_CONFIG_RETRIEVAL_SUCCESS, app.name)
      } catch (e: Exception) {
        log.error("Error retrieving/updating delivery config: $e", e)
        event.emitCounterMetric(CODE_EVENT_COUNTER, DELIVERY_CONFIG_RETRIEVAL_ERROR, app.name)
        eventPublisher.publishDeliveryConfigImportFailed(
          app.name,
          event,
          event.targetBranch,
          clock.instant(),
          e.message ?: "Unknown reason",
          scmUtils.getCommitLink(event)
        )
        return@forEach
      }
    }
  }

  private fun CodeEvent.emitCounterMetric(
    metric: String,
    extraTags: Collection<Pair<String, String>>,
    application: String? = null
  ) =
    spectator.counter(metric, metricTags(application, extraTags)).safeIncrement()
}
