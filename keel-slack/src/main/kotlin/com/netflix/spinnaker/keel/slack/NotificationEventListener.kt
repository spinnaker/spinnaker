package com.netflix.spinnaker.keel.slack

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.NotificationFrequency
import com.netflix.spinnaker.keel.api.NotificationFrequency.normal
import com.netflix.spinnaker.keel.api.NotificationFrequency.quiet
import com.netflix.spinnaker.keel.api.NotificationFrequency.verbose
import com.netflix.spinnaker.keel.api.NotificationType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.events.ConstraintStateChanged
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.core.api.PromotionStatus
import com.netflix.spinnaker.keel.events.ApplicationActuationPaused
import com.netflix.spinnaker.keel.events.ApplicationActuationResumed
import com.netflix.spinnaker.keel.events.ArtifactDeployedNotification
import com.netflix.spinnaker.keel.events.MarkAsBadNotification
import com.netflix.spinnaker.keel.events.PinnedNotification
import com.netflix.spinnaker.keel.events.UnpinnedNotification
import com.netflix.spinnaker.keel.lifecycle.LifecycleEvent
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus
import com.netflix.spinnaker.keel.notifications.NotificationType.APPLICATION_PAUSED
import com.netflix.spinnaker.keel.notifications.NotificationType.APPLICATION_RESUMED
import com.netflix.spinnaker.keel.notifications.NotificationType.ARTIFACT_DEPLOYMENT_FAILED
import com.netflix.spinnaker.keel.notifications.NotificationType.ARTIFACT_DEPLOYMENT_SUCCEEDED
import com.netflix.spinnaker.keel.notifications.NotificationType.ARTIFACT_MARK_AS_BAD
import com.netflix.spinnaker.keel.notifications.NotificationType.ARTIFACT_PINNED
import com.netflix.spinnaker.keel.notifications.NotificationType.ARTIFACT_UNPINNED
import com.netflix.spinnaker.keel.notifications.NotificationType.DELIVER_CONFIG_UPDATED
import com.netflix.spinnaker.keel.notifications.NotificationType.LIFECYCLE_EVENT
import com.netflix.spinnaker.keel.notifications.NotificationType.MANUAL_JUDGMENT_APPROVED
import com.netflix.spinnaker.keel.notifications.NotificationType.MANUAL_JUDGMENT_AWAIT
import com.netflix.spinnaker.keel.notifications.NotificationType.MANUAL_JUDGMENT_REJECTED
import com.netflix.spinnaker.keel.notifications.NotificationType.TEST_FAILED
import com.netflix.spinnaker.keel.notifications.NotificationType.TEST_PASSED
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.slack.handlers.SlackNotificationHandler
import com.netflix.spinnaker.keel.slack.handlers.supporting
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionVetoed
import com.netflix.spinnaker.keel.telemetry.VerificationCompleted
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Clock
import com.netflix.spinnaker.keel.notifications.NotificationType as Type

/**
 * Responsible to listening to notification events, and fetching the information needed
 * for sending a slack notification, based on NotificationType.
 */
@Component
class NotificationEventListener(
  private val repository: KeelRepository,
  private val clock: Clock,
  private val handlers: List<SlackNotificationHandler<*>>
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

      if (currentArtifact == null) {
        log.debug("can't send pinned notification as current artifacts information is missing")
        return
      }

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
          latestArtifact = latestArtifact?.copy(reference = pinnedEnv.artifact.reference),
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
          application = application
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
        deliveryConfigName = artifactRef.split(":")[0],
        artifactReference = artifactRef.split(":")[1],
        version = artifactVersion) ?: return

      val deliveryArtifact = config.artifacts.find {
        it.reference == artifactRef.split(":")[1]
      } ?: return

      // we only send notifications for failures
      if (status == LifecycleEventStatus.FAILED) {
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
      val artifact = repository.getArtifactVersion(deliveryArtifact, artifactVersion, null)
      if (artifact == null) {
        log.debug("artifact version is null for application ${config.application}. Can't send deployed artifact notification.")
        return
      }

      val priorVersion = repository.getArtifactVersionByPromotionStatus(config, targetEnvironment, deliveryArtifact, PromotionStatus.PREVIOUS)

      sendSlackMessage(config,
        SlackArtifactDeploymentNotification(
          time = clock.instant(),
          application = config.application,
          artifact = artifact.copy(reference = deliveryArtifact.reference),
          targetEnvironment = targetEnvironment,
          priorVersion = priorVersion,
          status = DeploymentStatus.SUCCEEDED
        ),
        ARTIFACT_DEPLOYMENT_SUCCEEDED,
        targetEnvironment)
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
          status = DeploymentStatus.FAILED
        ),
        ARTIFACT_DEPLOYMENT_FAILED,
        veto.targetEnvironment)
    }
  }

  @EventListener(ConstraintStateChanged::class)
  fun onConstraintStateChanged(notification: ConstraintStateChanged) {
    log.debug("Received constraint state changed event: $notification")
    with(notification) {
      // if this is the first time the constraint was evaluated, send a notification
      // so the user can react via other interfaces outside the UI (e.g. e-mail, Slack)
      if (constraint is ManualJudgementConstraint &&
        previousState == null &&
        currentState.status == ConstraintStatus.PENDING
      ) {

        val (config, artifact) = currentState.artifactReference?.let {
          getConfigAndArtifact(
            deliveryConfigName = currentState.deliveryConfigName,
            artifactReference = it,
            version = currentState.artifactVersion
          )
        } ?: return

        val deliveryArtifact = config.artifacts.find {
          it.reference == currentState.artifactReference
        } ?: return

        val currentArtifact = repository.getArtifactVersionByPromotionStatus(config, currentState.environmentName, deliveryArtifact, PromotionStatus.CURRENT)

        // fetch the pinned artifact, if exists
        val pinnedArtifact =
          repository.getPinnedVersion(config, currentState.environmentName, deliveryArtifact.reference)?.let {
            repository.getArtifactVersion(deliveryArtifact, it, null)
              ?.copy(reference = deliveryArtifact.reference)
          }

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
            stateUid = currentState.uid
          ),
          MANUAL_JUDGMENT_AWAIT,
          environment.name)
      }
    }
  }

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


  private inline fun <reified T : SlackNotificationEvent> sendSlackMessage(config: DeliveryConfig,
                                                                           message: T,
                                                                           type: Type,
                                                                           targetEnvironment: String? = null,
                                                                           artifact: DeliveryArtifact? = null) {
    val handler: SlackNotificationHandler<T>? = handlers.supporting(type)
    if (handler == null) {
      log.debug("no handler was found for notification type ${T::class.java}. Can't send slack notification.")
      return
    }

    val notifications = getNotificationsConfig(config, targetEnvironment, artifact)

    notifications
      .filter { shouldSend(it, type) }
      .groupBy { it.address }
      .forEach { (channel, _) ->
        handler.sendMessage(message, channel)
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
    val quietNotifications = listOf(ARTIFACT_MARK_AS_BAD, ARTIFACT_PINNED, ARTIFACT_UNPINNED, LIFECYCLE_EVENT, APPLICATION_PAUSED,
      APPLICATION_RESUMED, MANUAL_JUDGMENT_AWAIT, ARTIFACT_DEPLOYMENT_FAILED, TEST_FAILED)
    val normalNotifications = quietNotifications + listOf(ARTIFACT_DEPLOYMENT_SUCCEEDED, DELIVER_CONFIG_UPDATED, TEST_PASSED)
    val verboseNotifications = normalNotifications + listOf(MANUAL_JUDGMENT_REJECTED, MANUAL_JUDGMENT_APPROVED)

    return when (frequency) {
      verbose -> verboseNotifications
      normal -> normalNotifications
      quiet -> quietNotifications
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
}

