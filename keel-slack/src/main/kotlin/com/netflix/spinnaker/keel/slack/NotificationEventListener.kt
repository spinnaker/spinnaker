package com.netflix.spinnaker.keel.slack

import com.netflix.spinnaker.keel.api.NotificationType
import com.netflix.spinnaker.keel.core.api.PromotionStatus
import com.netflix.spinnaker.keel.events.PinnedNotification
import com.netflix.spinnaker.keel.events.UnpinnedNotification
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.slack.handlers.SlackNotificationHandler
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Clock

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
      val envNotifications = repository.environmentNotifications(config.name, pin.targetEnvironment)
      val deliveryArtifact = pin.reference.let {
        repository.getArtifact(config.name, it)
      }
      val pinnedArtifact = repository.getArtifactVersion(deliveryArtifact, pin.version, null)
      val currentArtifact = repository.getArtifactVersionByPromotionStatus(config, pin.targetEnvironment, deliveryArtifact, PromotionStatus.CURRENT)

      envNotifications.forEach { notificationConfig ->
        if (notificationConfig.type == NotificationType.slack) {
          val handler = handlers.find {
            it.type == notification.type
          } as? SlackNotificationHandler<SlackPinnedNotification>
          handler?.let {
            it.sendMessage(SlackPinnedNotification(
              channel = notificationConfig.address,
              pin = pin,
              currentArtifact = currentArtifact,
              pinnedArtifact = pinnedArtifact,
              application = config.application,
              time = clock.instant()
            ))
          }
        }
      }
    }
  }

  @EventListener(UnpinnedNotification::class)
  fun onUnpinnedNotification(notification: UnpinnedNotification) {
    with(notification) {
      val envNotifications = repository.environmentNotifications(config.name, targetEnvironment)

      if (pinnedEnvironment != null) {
        val latestApprovedArtifactVersion = repository.latestVersionApprovedIn(config, pinnedEnvironment!!.artifact, targetEnvironment)
        val latestArtifact = latestApprovedArtifactVersion?.let { repository.getArtifactVersion(pinnedEnvironment!!.artifact, it, null) }

        envNotifications.forEach { notificationConfig ->
          if (notificationConfig.type == NotificationType.slack) {
            val handler = handlers.find { hand ->
              hand.type == notification.type
            } as? SlackNotificationHandler<SlackUnpinnedNotification>
            handler?.let {
              it.sendMessage(SlackUnpinnedNotification(
                channel = notificationConfig.address,
                latestArtifact = latestArtifact,
                pinnedVersion = pinnedEnvironment!!.version,
                application = config.application,
                time = clock.instant(),
                user = user
              ))
            }

          } else {
            log.debug("no pinned artifacts exists for application ${config.application} and environment $targetEnvironment")
          }
        }
      }
    }
  }
}

