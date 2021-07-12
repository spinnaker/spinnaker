package com.netflix.spinnaker.keel.notifications.slack.handlers

import com.netflix.spinnaker.config.BaseUrlConfig
import com.netflix.spinnaker.keel.api.NotificationDisplay
import com.netflix.spinnaker.keel.notifications.NotificationType.DELIVERY_CONFIG_CHANGED
import com.netflix.spinnaker.keel.notifications.slack.SlackConfigNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackService
import com.slack.api.model.block.LayoutBlock
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Sends notifications for delivery config updates (create, modify)
 */
@Component
class DeliveryConfigNotificationHandler(
  private val slackService: SlackService,
  private val gitDataGenerator: GitDataGenerator,
) : SlackNotificationHandler<SlackConfigNotification> {
  override val supportedTypes = listOf(DELIVERY_CONFIG_CHANGED)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private fun SlackConfigNotification.headerText(): String {
    val action = if (new) {
      "created"
    } else {
      "updated"
    }
    return "[$application] delivery config $action"
  }

  private fun SlackConfigNotification.toBlocks(): List<LayoutBlock> {
    val action = if (new) {
      "created"
    } else {
      "updated"
    }

    val headerText = "Managed delivery config $action"
    return withBlocks {
      val gitMetadata = gitMetadata
      if (gitMetadata != null) {
        gitDataGenerator.notificationBodyWithCommit(this, ":pencil:", application, gitMetadata, headerText)
        section {
          gitDataGenerator.generateScmInfo(this, application, gitMetadata, null)
        }
      } else {
        section {
          markdownText(":pencil: $headerText for ${gitDataGenerator.linkedApp(application)}\n\n" +
            "No commit info available. " +
            "View the current <${gitDataGenerator.generateConfigUrl(application)}|config>."
          )
        }
      }
    }
  }

  override fun sendMessage(
    notification: SlackConfigNotification,
    channel: String,
    notificationDisplay: NotificationDisplay
  ) {
    with(notification) {
      log.debug("Sending config changed notification for application ${notification.application}")

      slackService.sendSlackNotification(
        channel,
        notification.toBlocks(),
        application = application,
        type = supportedTypes,
        fallbackText = headerText()
      )
    }
  }
}
