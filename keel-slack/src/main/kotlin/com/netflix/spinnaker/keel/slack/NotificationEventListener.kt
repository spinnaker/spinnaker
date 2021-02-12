package com.netflix.spinnaker.keel.slack

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.NotificationType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
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
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.slack.handlers.SlackNotificationHandler
import com.netflix.spinnaker.keel.slack.handlers.supporting
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionVetoed
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Clock
import com.netflix.spinnaker.keel.notifications.NotificationType as Type

/**
 * Responsible to listening to notification events, and fetching the information needed
 * for sending a notification, based on NotificationType.
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
      val deliveryArtifact = config.artifacts.find {
        it.reference == pin.reference
      } ?: return

      val pinnedArtifact = repository.getArtifactVersion(deliveryArtifact, pin.version, null)
      val currentArtifact = repository.getArtifactVersionByPromotionStatus(config, pin.targetEnvironment, deliveryArtifact, PromotionStatus.CURRENT)

      if (pinnedArtifact == null || currentArtifact == null) {
        log.debug("can't send notification as either pinned artifact or current artifacts information is missing")
        return
      }

      sendSlackMessage(
        config,
        SlackPinnedNotification(
          pin = pin,
          currentArtifact = currentArtifact,
          pinnedArtifact = pinnedArtifact.copy(reference = pin.reference),
          application = config.application,
          time = clock.instant()
        ),
        Type.ARTIFACT_PINNED,
        pin.targetEnvironment)
    }

  }


  @EventListener(UnpinnedNotification::class)
  fun onUnpinnedNotification(notification: UnpinnedNotification) {
    with(notification) {
      if (pinnedEnvironment == null) {
        log.debug("no pinned artifacts exists for application ${config.application} and environment $targetEnvironment")
        return
      }

      val latestApprovedArtifactVersion = repository.latestVersionApprovedIn(config, pinnedEnvironment!!.artifact, targetEnvironment)
      if (latestApprovedArtifactVersion == null) {
        log.debug("latestApprovedArtifactVersion is null for application ${config.application}, env $targetEnvironment. Can't send UnpinnedNotification")
        return
      }

      val latestArtifact = repository.getArtifactVersion(pinnedEnvironment!!.artifact, latestApprovedArtifactVersion, null)
      val pinnedArtifact = repository.getArtifactVersion(pinnedEnvironment!!.artifact, pinnedEnvironment!!.version, null)

      sendSlackMessage(config,
        SlackUnpinnedNotification(
          latestArtifact = latestArtifact?.copy(reference = pinnedEnvironment!!.artifact.reference),
          pinnedArtifact = pinnedArtifact,
          application = config.application,
          time = clock.instant(),
          user = user,
          targetEnvironment = targetEnvironment
        ),
        Type.ARTIFACT_UNPINNED,
        targetEnvironment)

      log.debug("no environment $targetEnvironment was found in the config named ${config.name}")

    }
  }

  @EventListener(MarkAsBadNotification::class)
  fun onMarkAsBadNotification(notification: MarkAsBadNotification) {
    with(notification) {
      val deliveryArtifact = config.artifacts.find {
        it.reference == veto.reference
      } ?: return

      val vetoedArtifact = repository.getArtifactVersion(deliveryArtifact, veto.version, null)
      if (vetoedArtifact == null) {
        log.debug("vetoedArtifact is null for application ${config.application}. Can't send MarkAsBadNotification notification")
        return
      }

      sendSlackMessage(config,
        SlackMarkAsBadNotification(
          vetoedArtifact = vetoedArtifact.copy(reference = deliveryArtifact.reference),
          user = user,
          targetEnvironment = veto.targetEnvironment,
          time = clock.instant(),
          application = config.name,
          comment = veto.comment
        ),
        Type.ARTIFACT_MARK_AS_BAD,
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
        Type.APPLICATION_PAUSED)
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
        Type.APPLICATION_RESUMED)
    }
  }

  @EventListener(LifecycleEvent::class)
  fun onLifecycleEvent(notification: LifecycleEvent) {
    with(notification) {
      val config = repository.getDeliveryConfig(artifactRef.split(":")[0])
      val deliveryArtifact = config.artifacts.find {
        it.reference == artifactRef.split(":")[1]
      } ?: return

      val artifact = repository.getArtifactVersion(deliveryArtifact, artifactVersion, null)
      if (artifact == null) {
        log.debug("artifact version is null for application ${config.application}. Can't send $type notification")
        return
      }

      if (status == LifecycleEventStatus.FAILED) {
        sendSlackMessage(config,
          SlackLifecycleNotification(
            time = clock.instant(),
            artifact = artifact.copy(reference = deliveryArtifact.reference),
            eventType = type,
            application = config.application
          ),
          Type.LIFECYCLE_EVENT,
          artifact = deliveryArtifact)
      }
    }
  }

  @EventListener(ArtifactDeployedNotification::class)
  fun onArtifactVersionDeployed(notification: ArtifactDeployedNotification) {
    with(notification) {
      val artifact = repository.getArtifactVersion(deliveryArtifact, artifactVersion, null)
      if (artifact == null) {
        log.debug("artifact version is null for application ${config.application}. Can't send DeployedArtifactNotification.")
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
        Type.ARTIFACT_DEPLOYMENT,
        targetEnvironment)
    }
  }

  @EventListener(ArtifactVersionVetoed::class)
  fun onArtifactVersionVetoed(notification: ArtifactVersionVetoed) {
    with(notification) {
      val config = repository.getDeliveryConfigForApplication(application)

      val deliveryArtifact = config.artifacts.find {
        it.reference == veto.reference
      } ?: return

      val artifact = repository.getArtifactVersion(deliveryArtifact, veto.version, null)
      if (artifact == null) {
        log.debug("artifact version is null for application ${config.application}. Can't send failed deployment notification.")
        return
      }

      sendSlackMessage(config,
        SlackArtifactDeploymentNotification(
          time = clock.instant(),
          application = config.application,
          artifact = artifact.copy(reference = deliveryArtifact.reference),
          targetEnvironment = veto.targetEnvironment,
          status = DeploymentStatus.FAILED
        ),
        Type.ARTIFACT_DEPLOYMENT,
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
        val config = repository.getDeliveryConfig(currentState.deliveryConfigName)

        val deliveryArtifact = config.artifacts.find {
          it.reference == currentState.artifactReference
        } .also {
          if (it == null) log.debug("Artifact with reference ${currentState.artifactReference}  not found in delivery config")
        } ?: return

        val artifactCandidate = repository.getArtifactVersion(deliveryArtifact, currentState.artifactVersion, null)
        if (artifactCandidate == null) {
          log.debug("$deliveryArtifact version ${currentState.artifactVersion} not found. Can't send manual judgement notification.")
          return
        }
        val currentArtifact = repository.getArtifactVersionByPromotionStatus(config, currentState.environmentName , deliveryArtifact, PromotionStatus.CURRENT)

        sendSlackMessage(
          config,
          SlackManualJudgmentNotification(
            time = clock.instant(),
            application = config.application,
            artifactCandidate = artifactCandidate.copy(reference = deliveryArtifact.reference),
            targetEnvironment = currentState.environmentName,
            currentArtifact = currentArtifact,
            deliveryArtifact = deliveryArtifact,
            stateUid = currentState.uid
          ),
          Type.MANUAL_JUDGMENT,
          environment.name)
      }
    }
  }


  private inline fun <reified T : SlackNotificationEvent> sendSlackMessage(config: DeliveryConfig, message: T, type: Type,
                                                                           targetEnvironment: String? = null,
                                                                           artifact: DeliveryArtifact? = null) {
    val handler: SlackNotificationHandler<T>? = handlers.supporting(type)

    if (handler == null) {
      log.debug("no handler was found for notification type ${T::class.java}. Can't send slack notification.")
      return
    }

    //if targetEnvironment is not null, use only its notifications. Else, use all notifications configured for all environments.
    val environments: Set<Environment> = config.environments.filter {
      targetEnvironment == null || it.name == targetEnvironment
    }.filter {
      //if artifact is not null, make sure it used in the environment prior to sending the notification
      artifact == null || artifact.isUsedIn(it)
    }.toSet()

    environments.flatMap { it.notifications }
      .filter { it.type == NotificationType.slack }
      .groupBy { it.address }
      .forEach { (channel, _) ->
        handler.sendMessage(message, channel)
      }
  }
}

