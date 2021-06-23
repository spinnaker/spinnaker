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
  private val baseUrlConfig: BaseUrlConfig
) : SlackNotificationHandler<SlackConfigNotification> {
  override val supportedTypes = listOf(DELIVERY_CONFIG_CHANGED)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private fun SlackConfigNotification.headerText(): String {
    val action = if (new) {
      "created"
    } else {
      "updated"
    }
    return ":pencil: $application delivery config $action"
  }

  private fun SlackConfigNotification.compactMessage(): List<LayoutBlock> =
    withBlocks {
      val action = if (new) {
        "created"
      } else {
        "updated"
      }
      val header = ":pencil: ${linkedApp(baseUrlConfig, application)} delivery config $action"
      section {
        markdownText(header)
      }
      val gitMetadata = gitMetadata
      if (gitMetadata != null) {
        section {
          gitDataGenerator.generateScmInfo(this, application, gitMetadata, null)
        }
      } else {
        section {
          markdownText("View the current <${gitDataGenerator.generateConfigUrl(application)}|config>.")
        }
      }
    }

  private fun SlackConfigNotification.normalMessage(): List<LayoutBlock> {
    val imageUrl = "https://raw.githubusercontent.com/spinnaker/spinnaker.github.io/master/assets/images/md_icons/config_change.png"

    val action = if (new) {
      "created"
    } else {
      "updated"
    }

    val headerText = "Delivery config $action"
    val altText = "config $action"
    return withBlocks {
      header {
        text(headerText, emoji = true)
      }
      val gitMetadata = gitMetadata
      if (gitMetadata != null) {
        section {
          gitDataGenerator.generateCommitInfoNoArtifact(
            this,
            application,
            imageUrl,
            altText,
            gitMetadata,
            gitMetadata.author?.let{ slackService.getUsernameByEmail(it) }
          )
        }
        gitDataGenerator.conditionallyAddFullCommitMsgButton(this, gitMetadata)
        section {
          gitDataGenerator.generateScmInfo(this, application, gitMetadata, null)
        }
      } else {
        section {
          markdownText("*App:* $application\n\n" +
            "No commit info available.\n" +
            "View the current <${gitDataGenerator.generateConfigUrl(application)}|config>."
          )
          accessory {
            image(imageUrl = imageUrl, altText = altText)
          }
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

      val uniqueBlocks = when(notificationDisplay) {
        NotificationDisplay.NORMAL -> notification.normalMessage()
        NotificationDisplay.COMPACT -> notification.compactMessage()
      }

      slackService.sendSlackNotification(
        channel,
        uniqueBlocks,
        application = application,
        type = supportedTypes,
        fallbackText = headerText()
      )
    }
  }
}
