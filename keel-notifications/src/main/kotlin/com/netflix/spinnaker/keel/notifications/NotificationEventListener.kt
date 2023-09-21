package com.netflix.spinnaker.keel.notifications

import com.netflix.spinnaker.config.BaseUrlConfig
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.NotificationDisplay.NORMAL
import com.netflix.spinnaker.keel.api.NotificationFrequency
import com.netflix.spinnaker.keel.api.NotificationFrequency.normal
import com.netflix.spinnaker.keel.api.NotificationFrequency.notice
import com.netflix.spinnaker.keel.api.NotificationFrequency.quiet
import com.netflix.spinnaker.keel.api.NotificationFrequency.verbose
import com.netflix.spinnaker.keel.api.NotificationType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.api.events.ConstraintStateChanged
import com.netflix.spinnaker.keel.constraints.ManualJudgementConstraintAttributes
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.core.api.PromotionStatus
import com.netflix.spinnaker.keel.events.ApplicationActuationPaused
import com.netflix.spinnaker.keel.events.ApplicationActuationResumed
import com.netflix.spinnaker.keel.events.ArtifactDeployedNotification
import com.netflix.spinnaker.keel.events.DeliveryConfigChangedNotification
import com.netflix.spinnaker.keel.events.MarkAsBadNotification
import com.netflix.spinnaker.keel.events.PinnedNotification
import com.netflix.spinnaker.keel.events.PluginNotification
import com.netflix.spinnaker.keel.events.ResourceTaskFailed
import com.netflix.spinnaker.keel.events.UnpinnedNotification
import com.netflix.spinnaker.keel.lifecycle.LifecycleEvent
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType
import com.netflix.spinnaker.keel.network.NetworkEndpointProvider
import com.netflix.spinnaker.keel.notifications.NotificationType.APPLICATION_PAUSED
import com.netflix.spinnaker.keel.notifications.NotificationType.APPLICATION_RESUMED
import com.netflix.spinnaker.keel.notifications.NotificationType.ARTIFACT_DEPLOYMENT_FAILED
import com.netflix.spinnaker.keel.notifications.NotificationType.ARTIFACT_DEPLOYMENT_SUCCEEDED
import com.netflix.spinnaker.keel.notifications.NotificationType.ARTIFACT_MARK_AS_BAD
import com.netflix.spinnaker.keel.notifications.NotificationType.ARTIFACT_PINNED
import com.netflix.spinnaker.keel.notifications.NotificationType.ARTIFACT_UNPINNED
import com.netflix.spinnaker.keel.notifications.NotificationType.DELIVERY_CONFIG_CHANGED
import com.netflix.spinnaker.keel.notifications.NotificationType.LIFECYCLE_EVENT
import com.netflix.spinnaker.keel.notifications.NotificationType.MANUAL_JUDGMENT_APPROVED
import com.netflix.spinnaker.keel.notifications.NotificationType.MANUAL_JUDGMENT_AWAIT
import com.netflix.spinnaker.keel.notifications.NotificationType.MANUAL_JUDGMENT_REJECTED
import com.netflix.spinnaker.keel.notifications.NotificationType.MANUAL_JUDGMENT_UPDATE
import com.netflix.spinnaker.keel.notifications.NotificationType.PLUGIN_NOTIFICATION_NORMAL
import com.netflix.spinnaker.keel.notifications.NotificationType.PLUGIN_NOTIFICATION_QUIET
import com.netflix.spinnaker.keel.notifications.NotificationType.PLUGIN_NOTIFICATION_VERBOSE
import com.netflix.spinnaker.keel.notifications.NotificationType.TEST_FAILED
import com.netflix.spinnaker.keel.notifications.NotificationType.TEST_PASSED
import com.netflix.spinnaker.keel.notifications.scm.ScmNotifier
import com.netflix.spinnaker.keel.notifications.slack.DeploymentStatus
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.notifications.slack.DeploymentStatus.FAILED
import com.netflix.spinnaker.keel.notifications.slack.DeploymentStatus.SUCCEEDED
import com.netflix.spinnaker.keel.notifications.slack.SlackArtifactDeploymentNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackConfigNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackLifecycleNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackManualJudgmentNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackManualJudgmentUpdateNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackMarkAsBadNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackNotificationEvent
import com.netflix.spinnaker.keel.notifications.slack.SlackPausedNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackPinnedNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackPluginNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackResumedNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackUnpinnedNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackVerificationCompletedNotification
import com.netflix.spinnaker.keel.notifications.slack.handlers.SlackNotificationHandler
import com.netflix.spinnaker.keel.notifications.slack.handlers.supporting
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionVetoed
import com.netflix.spinnaker.keel.telemetry.VerificationCompleted
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Clock
import com.netflix.spinnaker.keel.notifications.NotificationType as Type

/**
 * Responsible for listening to notification events, and fetching the information needed
 * to send those notifications to end users via Slack, SCM systems (for PR comments), etc.
 */
@Component
class NotificationEventListener(
  private val repository: KeelRepository,
  private val clock: Clock,
  private val slackHandlers: List<SlackNotificationHandler<*>>,
  private val scmNotifier: ScmNotifier,
  private val baseUrlConfig: BaseUrlConfig,
  private val networkEndpointProvider: NetworkEndpointProvider
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @EventListener(PinnedNotification::class)
  fun onPinnedNotification(notification: PinnedNotification) {
    with(notification) {
      val (_, artifact) = getConfigAndArtifact(
        application = config.application,
        artifactReference = pin.reference,
        version = pin.version,
        config = config) ?: return

      val deliveryArtifact = config.artifacts.find {
        it.reference == pin.reference
      } ?: return

      val currentArtifact = repository.getArtifactVersionByPromotionStatus(config, pin.targetEnvironment, deliveryArtifact, PromotionStatus.CURRENT)

      sendSlackMessage(
        config,
        SlackPinnedNotification(
          pin = pin,
          currentArtifact = currentArtifact,
          pinnedArtifact = artifact,
          application = config.application,
          time = clock.instant()
        ),
        ARTIFACT_PINNED,
        pin.targetEnvironment)
    }
  }


  @EventListener(UnpinnedNotification::class)
  fun onUnpinnedNotification(notification: UnpinnedNotification) {
    with(notification) {
      val pinnedEnv = pinnedEnvironment
      if (pinnedEnv == null) {
        log.debug("can't send unpinned notification, as no pinned artifact exists for application ${config.application} and environment $targetEnvironment")
        return
      }

      val latestApprovedArtifactVersion = repository.latestVersionApprovedIn(config, pinnedEnv.artifact, targetEnvironment)
      if (latestApprovedArtifactVersion == null) {
        log.debug("last approved artifact version is null for application ${config.application}, env $targetEnvironment. Can't send unpinned notification")
        return
      }

      val latestArtifact = repository.getArtifactVersion(pinnedEnv.artifact, latestApprovedArtifactVersion, null)
      val pinnedArtifact = repository.getArtifactVersion(pinnedEnv.artifact, pinnedEnv.version, null)

      sendSlackMessage(config,
        SlackUnpinnedNotification(
          latestApprovedArtifactVersion = latestArtifact?.copy(reference = pinnedEnv.artifact.reference),
          pinnedArtifact = pinnedArtifact,
          application = config.application,
          time = clock.instant(),
          user = user,
          targetEnvironment = targetEnvironment,
          originalPin = pinnedEnv
        ),
        ARTIFACT_UNPINNED,
        targetEnvironment)
    }
  }

  @EventListener(MarkAsBadNotification::class)
  fun onMarkAsBadNotification(notification: MarkAsBadNotification) {
    with(notification) {
      val (_, artifact) = getConfigAndArtifact(
        application = config.application,
        artifactReference = veto.reference,
        version = veto.version,
        config = config) ?: return

      sendSlackMessage(config,
        SlackMarkAsBadNotification(
          vetoedArtifact = artifact,
          user = user,
          targetEnvironment = veto.targetEnvironment,
          time = clock.instant(),
          application = config.application,
          comment = veto.comment
        ),
        ARTIFACT_MARK_AS_BAD,
        veto.targetEnvironment
      )
    }
  }

  @EventListener(ApplicationActuationPaused::class)
  fun onApplicationActuationPaused(notification: ApplicationActuationPaused) {
    with(notification) {
      val config = repository.getDeliveryConfigForApplication(application)

      sendSlackMessage(config,
        SlackPausedNotification(
          user = triggeredBy,
          time = clock.instant(),
          application = application,
          comment = comment
        ),
        APPLICATION_PAUSED)
    }
  }

  @EventListener(ApplicationActuationResumed::class)
  fun onApplicationActuationResumed(notification: ApplicationActuationResumed) {
    with(notification) {
      val config = repository.getDeliveryConfigForApplication(application)

      sendSlackMessage(config,
        SlackResumedNotification(
          user = triggeredBy,
          time = clock.instant(),
          application = application
        ),
        APPLICATION_RESUMED)
    }
  }

  @EventListener(LifecycleEvent::class)
  fun onLifecycleEvent(notification: LifecycleEvent) {
    with(notification) {
      val (config, artifact) = getConfigAndArtifact(
        deliveryConfigName = deliveryConfigName,
        artifactReference = artifactReference,
        version = artifactVersion
      ) ?: return

      val deliveryArtifact = config.artifacts.find {
        it.reference == artifactReference
      } ?: return

      // we only send notifications for bake failures
      if (status == LifecycleEventStatus.FAILED && type == LifecycleEventType.BAKE) {
        sendSlackMessage(config,
          SlackLifecycleNotification(
            time = clock.instant(),
            artifact = artifact,
            eventType = type,
            application = config.application
          ),
          LIFECYCLE_EVENT,
          artifact = deliveryArtifact)
      }
    }
  }

  @EventListener(ArtifactDeployedNotification::class)
  fun onArtifactVersionDeployed(notification: ArtifactDeployedNotification) {
    with(notification) {
      val (config, artifact) = getConfigAndArtifact(
        application = config.application,
        artifactReference = deliveryArtifact.reference,
        version = artifactVersion
      ) ?: return
        .also {
          log.debug("artifact version is null for application ${config.application}. Can't send deployed artifact notification.")
        }

      val priorVersion = repository.getArtifactVersionByPromotionStatus(config, targetEnvironment.name, deliveryArtifact, PromotionStatus.PREVIOUS)

      sendSlackMessage(config,
        SlackArtifactDeploymentNotification(
          time = clock.instant(),
          application = config.application,
          artifact = artifact.copy(reference = deliveryArtifact.reference),
          targetEnvironment = targetEnvironment.name,
          priorVersion = priorVersion,
          status = SUCCEEDED
        ),
        ARTIFACT_DEPLOYMENT_SUCCEEDED,
        targetEnvironment.name
      )

      if (targetEnvironment.isPreview) {
        reportDeploymentResultToScm(config, targetEnvironment, artifact, SUCCEEDED)
      }
    }
  }

  @EventListener(PluginNotification::class)
  fun onPluginNotification(notification: PluginNotification) {
    with(notification) {
      val config = constraintStateChanged.deliveryConfig
      val deliveryArtifact = config
        .matchingArtifactByReference(constraintStateChanged.currentState.artifactReference) ?: return
      val artifact = repository.getArtifactVersion(deliveryArtifact, constraintStateChanged.currentState.artifactVersion, null)
      if (artifact == null) {
        log.debug("artifact version is null for application ${constraintStateChanged.deliveryConfig.application}. Can't send deployed artifact notification.")
        return
      }

      val level = when (notification.pluginNotificationConfig.notificationLevel) {
        verbose -> PLUGIN_NOTIFICATION_VERBOSE
          normal -> PLUGIN_NOTIFICATION_NORMAL
          notice -> PLUGIN_NOTIFICATION_NORMAL
          quiet ->PLUGIN_NOTIFICATION_QUIET
      }

      sendSlackMessage(config,
        SlackPluginNotification(
          time = clock.instant(),
          application = config.application,
          config = pluginNotificationConfig,
          artifactVersion = artifact.copy(reference = deliveryArtifact.reference),
          targetEnvironment = constraintStateChanged.currentState.environmentName,
        ),
        level,
        constraintStateChanged.currentState.environmentName
      )
    }
  }

  @EventListener(ResourceTaskFailed::class)
  fun onArtifactVersionDeployFailed(notification: ResourceTaskFailed) {
    log.debug("Attempting to send deployment failed notification for failing task:: {}", notification)
    val resource = repository.getResource(notification.id)
    val config = repository.getDeliveryConfigForApplication(notification.application)
    val artifact = resource.findAssociatedArtifact(config) ?: return
    val environment = config.environments.find { it.resourceIds.contains(resource.id) } ?: return

    // attempt to parse latest version from the task name, we have a convention
    val regex = Regex(pattern = """(?<=\Deploy )(.*?)(?=\ to)""")
    val taskName = notification.tasks.firstOrNull()?.name ?: return
    val match = regex.find(taskName) ?: return
    val latestApprovedVersion = match.groups[1]?.value ?: return //should be the version string
    val latestArtifact = repository.getArtifactVersion(artifact, latestApprovedVersion, null) ?: return

    sendSlackMessage(config,
      SlackArtifactDeploymentNotification(
        time = clock.instant(),
        application = notification.application,
        artifact = latestArtifact,
        targetEnvironment = environment.name,
        status = FAILED
      ),
      ARTIFACT_DEPLOYMENT_FAILED,
      environment.name
    )

    if (environment.isPreview) {
      reportDeploymentResultToScm(config, environment, latestArtifact, FAILED)
    }
  }

  @EventListener(ArtifactVersionVetoed::class)
  fun onArtifactVersionVetoed(notification: ArtifactVersionVetoed) {
    with(notification) {
      val (config, artifact) = getConfigAndArtifact(
        application = application,
        artifactReference = veto.reference,
        version = veto.version) ?: return

      sendSlackMessage(config,
        SlackArtifactDeploymentNotification(
          time = clock.instant(),
          application = application,
          artifact = artifact,
          targetEnvironment = veto.targetEnvironment,
          status = FAILED
        ),
        ARTIFACT_DEPLOYMENT_FAILED,
        veto.targetEnvironment)
    }
  }

  @EventListener(ConstraintStateChanged::class)
  fun onConstraintStateChanged(notification: ConstraintStateChanged) {
    log.debug("Received constraint state changed event: $notification")
    with(notification) {
      if (shouldSendManualJudgementAwait() || shouldSendManualJudgementUpdate()) {
        val (config, artifact) = getConfigAndArtifact(
          deliveryConfigName = currentState.deliveryConfigName,
          artifactReference = currentState.artifactReference,
          version = currentState.artifactVersion
        ) ?: return

        val deliveryArtifact = config.artifacts.find {
          it.reference == currentState.artifactReference
        } ?: return

        val currentArtifact = repository.getCurrentlyDeployedArtifactVersion(
          config,
          deliveryArtifact,
          currentState.environmentName,
        )

        // fetch the pinned artifact, if exists
        val pinnedArtifact =
          repository.getPinnedVersion(config, currentState.environmentName, deliveryArtifact.reference)?.let {
            repository.getArtifactVersion(deliveryArtifact, it, null)
              ?.copy(reference = deliveryArtifact.reference)
          }

        if (shouldSendManualJudgementAwait()) {
          // if this is the first time the constraint was evaluated, send a notification
          // so the user can react via other interfaces outside the UI (e.g. Slack)
          sendSlackMessage(
            config,
            SlackManualJudgmentNotification(
              time = clock.instant(),
              application = config.application,
              artifactCandidate = artifact,
              targetEnvironment = currentState.environmentName,
              currentArtifact = currentArtifact,
              deliveryArtifact = deliveryArtifact,
              pinnedArtifact = pinnedArtifact,
              stateUid = currentState.uid,
              config = config
            ),
            MANUAL_JUDGMENT_AWAIT,
            environment.name
          )
        } else if (shouldSendManualJudgementUpdate()) {
          // update all slack notifications we sent if the judgement came from the ui or api
          (currentState.attributes as? ManualJudgementConstraintAttributes)?.let { attrs ->
            attrs.slackDetails.forEach { slackDetail ->
              sendSlackMessage(
                config,
                SlackManualJudgmentUpdateNotification(
                  timestamp = slackDetail.timestamp,
                  channel = slackDetail.channel,
                  application = config.application,
                  time = clock.instant(),
                  status = currentState.status,
                  user = currentState.judgedBy,
                  artifactCandidate = artifact,
                  targetEnvironment = currentState.environmentName,
                  currentArtifact = currentArtifact,
                  deliveryArtifact = deliveryArtifact,
                  pinnedArtifact = pinnedArtifact,
                  author = slackDetail.author,
                  display = slackDetail.display ?: NORMAL,
                  config = config
                ),
                MANUAL_JUDGMENT_UPDATE,
                environment.name
              )
            }
          }
        }
      }
    }
  }

  fun ConstraintStateChanged.shouldSendManualJudgementAwait(): Boolean =
    constraint is ManualJudgementConstraint && previousState == null && currentState.status == PENDING

  fun ConstraintStateChanged.shouldSendManualJudgementUpdate(): Boolean =
    constraint is ManualJudgementConstraint && previousState?.status == PENDING && currentState.complete()

  @EventListener(VerificationCompleted::class)
  fun onVerificationCompletedNotification(notification: VerificationCompleted) {
    log.debug("Received verification completed event: $notification")
    with(notification) {
      if (status != ConstraintStatus.PASS && status != ConstraintStatus.FAIL) {
        log.debug("can't send notification for verification completed with status $status it's not pass/fail. Ignoring notification for" +
          "application $application")
        return
      }

      val (config, artifact) = getConfigAndArtifact(deliveryConfigName = deliveryConfigName,
          artifactReference = artifactReference,
          version = artifactVersion)
       ?: return

      val type = when (status) {
        ConstraintStatus.PASS -> TEST_PASSED
        ConstraintStatus.FAIL -> TEST_FAILED
        //We shouldn't get here as we checked prior that status is either fail/pass
        else -> TEST_PASSED
      }

      sendSlackMessage(
        config,
        SlackVerificationCompletedNotification(
          time = clock.instant(),
          application = config.application,
          artifact = artifact,
          targetEnvironment = environmentName,
          status = status
        ),
        type,
        environmentName)
    }
  }

  @EventListener(DeliveryConfigChangedNotification::class)
  fun onDeliveryConfigChangedNotification(notification: DeliveryConfigChangedNotification) {
    log.debug("Received delivery config changed event: $notification")
    with(notification) {
      sendSlackMessage(config,
        SlackConfigNotification(
          time = clock.instant(),
          application = config.application,
          config = config,
          gitMetadata = gitMetadata,
          new = notification.new
        ),
        DELIVERY_CONFIG_CHANGED)
    }
  }

  private inline fun <reified T : SlackNotificationEvent> sendSlackMessage(config: DeliveryConfig,
                                                                           message: T,
                                                                           type: Type,
                                                                           targetEnvironment: String? = null,
                                                                           artifact: DeliveryArtifact? = null) {
    val handler: SlackNotificationHandler<T>? = slackHandlers.supporting(type)
    if (handler == null) {
      log.debug("no handler was found for notification type ${T::class.java}. Can't send slack notification.")
      return
    }

    val notifications = getNotificationsConfig(config, targetEnvironment, artifact)

    notifications
      .filter { shouldSend(it, type) }
      .groupBy { it.address }
      .forEach { (channel, notificationConfigs) ->
        val display = notificationConfigs.mapNotNull { it.display }.toSet().firstOrNull() ?: NORMAL
        handler.sendMessage(message, channel, display)
      }
  }

  private fun getNotificationsConfig(config: DeliveryConfig, targetEnvironment: String?, artifact: DeliveryArtifact?): Set<NotificationConfig> {
    return config.environments
      // if targetEnvironment is not null, use only its notifications. Else, use all notifications configured for all environments.
      .filter { targetEnvironment == null || it.name == targetEnvironment }
      // if artifact is not null, make sure it used in the environment prior to sending the notification
      .filter { artifact == null || artifact.isUsedIn(it) }
      .flatMap { it.notifications }
      .toSet()
  }

  // return true of the notification type is slack, and the notification type included in the frequencies list below
  private fun shouldSend(config: NotificationConfig, type: Type): Boolean {
    return config.type == NotificationType.slack && translateFrequencyToEvents(config.frequency).contains(type)
  }


  private fun translateFrequencyToEvents(frequency: NotificationFrequency): List<Type> {
    val quietNotifications = listOf(
      ARTIFACT_MARK_AS_BAD,
      ARTIFACT_PINNED,
      ARTIFACT_UNPINNED,
      LIFECYCLE_EVENT,
      APPLICATION_PAUSED,
      APPLICATION_RESUMED,
      MANUAL_JUDGMENT_AWAIT,
      MANUAL_JUDGMENT_UPDATE,
      ARTIFACT_DEPLOYMENT_FAILED,
      TEST_FAILED,
      PLUGIN_NOTIFICATION_QUIET
    )
    val normalNotifications = quietNotifications + listOf(
      ARTIFACT_DEPLOYMENT_SUCCEEDED,
      DELIVERY_CONFIG_CHANGED,
      TEST_PASSED,
      PLUGIN_NOTIFICATION_NORMAL
    )
    val verboseNotifications = normalNotifications + listOf(
      MANUAL_JUDGMENT_REJECTED,
      MANUAL_JUDGMENT_APPROVED,
      PLUGIN_NOTIFICATION_VERBOSE
    )

    val noticeNotifications = listOf(
      ARTIFACT_DEPLOYMENT_SUCCEEDED
    )

    return when (frequency) {
      verbose -> verboseNotifications
      normal -> normalNotifications
      quiet -> quietNotifications
      notice -> noticeNotifications
    }
  }

  // helper function to get the config file and the right artifact for sending the notification
  private fun getConfigAndArtifact(application: String? = null,
                                   deliveryConfigName: String? = null,
                                   artifactReference: String,
                                   version: String,
                                   config: DeliveryConfig? = null)
    : Pair<DeliveryConfig, PublishedArtifact>? {
    //get the config either by name, application name or directly from the notification
    val deliveryConfig = config ?: application?.let { repository.getDeliveryConfigForApplication(it) }
    ?: deliveryConfigName?.let { repository.getDeliveryConfig(it) }
      .also {
        if (it == null) log.debug("delivery config is null. Can't send notification.")
      } ?: return null


    val deliveryArtifact = deliveryConfig.artifacts.find {
      it.reference == artifactReference
    }.also {
      if (it == null) log.debug("can't find artifact $artifactReference in config ${deliveryConfig.name}. Can't send notification.")
    } ?: return null

    val artifact = repository.getArtifactVersion(deliveryArtifact, version, null)
      .also {
        if (it == null) log.debug("artifact version is null for application ${deliveryConfig.application}. Can't send notification.")
      } ?: return null

    return Pair(deliveryConfig, artifact.copy(reference = deliveryArtifact.reference))
  }

  /**
   * Reports the result of a deployment to the SCM system as follows:
   *  - Posts a comment on the pull request associated with the [environment] and [publishedArtifact].  The comment
   *    includes the DNS endpoint information for the applicable resources.
   *  - Posts specified deployment [status] to the commit associated with the [publishedArtifact]. This can be used
   *    to gate merging of PRs, for example.
   */
  private fun reportDeploymentResultToScm(
    config: DeliveryConfig,
    environment: Environment,
    publishedArtifact: PublishedArtifact,
    status: DeploymentStatus
  ) {
    val environmentsLink = "${baseUrlConfig.baseUrl}/#/applications/${config.application}/environments/overview"

    val markdownComment = if (status == SUCCEEDED) {
      config.resourcesUsing(publishedArtifact.reference, environment.name)
        .joinToString("\n\n") { resource ->
          val endpoints = runBlocking {
            networkEndpointProvider.getNetworkEndpoints(resource)
          }.groupBy { it.region }

          "✅ &nbsp;[${resource.kind.friendlyName} ${resource.name} deployed to preview environment]($environmentsLink)" +
            "\n\nEndpoints:\n" + endpoints.map { (region, endpoints) ->
              endpoints.joinToString("\n") { endpoint ->
                // TODO: determine the proper protocol and port for these links
                "  - [$region] [${endpoint.address}](https://${endpoint.address})"
              }
          }.joinToString("\n")
        }
    } else {
      "❌ &nbsp;[Preview environment deployment failed]($environmentsLink)"
    }

    scmNotifier.commentOnPullRequest(config, environment, markdownComment)
    scmNotifier.postDeploymentStatusToCommit(config, environment, publishedArtifact, status)
  }
}
