package com.netflix.spinnaker.keel.notifications.slack.handlers

import com.netflix.spinnaker.keel.api.NotificationDisplay
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.notifications.slack.SlackLifecycleNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackService
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.apache.logging.log4j.util.Strings
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Sends notification for a lifecycle event, like bake failures
 */
@Component
class LifecycleEventNotificationHandler(
  private val slackService: SlackService,
  private val gitDataGenerator: GitDataGenerator
) : SlackNotificationHandler<SlackLifecycleNotification> {

  override val supportedTypes = listOf(NotificationType.LIFECYCLE_EVENT)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(
    notification: SlackLifecycleNotification,
    channel: String,
    notificationDisplay: NotificationDisplay
  ) {
    with(notification) {
      log.debug("Sending lifecycle event $eventType notification for application ${notification.application}")

      val headerText = "[$application] bake failed for ${artifact.version}"

      val blocks = withBlocks {
        gitDataGenerator.notificationBody(this, ":x::cake:", application, artifact, "Bake failed")
        artifact.gitMetadata?.let { gitMetadata ->
          section {
            gitDataGenerator.generateScmInfo(this, application, gitMetadata, artifact)
          }
        }
      }
      slackService.sendSlackNotification(channel, blocks, application = application, type = supportedTypes, fallbackText = headerText)
    }
  }

}
