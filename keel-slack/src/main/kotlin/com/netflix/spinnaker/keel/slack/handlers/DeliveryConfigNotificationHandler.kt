package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.keel.notifications.NotificationType.DELIVERY_CONFIG_CHANGED
import com.netflix.spinnaker.keel.slack.SlackConfigNotification
import com.netflix.spinnaker.keel.slack.SlackService
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Sends notifications for delivery config updates (create, modify)
 */
@Component
class DeliveryConfigNotificationHandler(
  private val slackService: SlackService,
  private val gitDataGenerator: GitDataGenerator
) : SlackNotificationHandler<SlackConfigNotification> {
  override val supportedTypes = listOf(DELIVERY_CONFIG_CHANGED)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(notification: SlackConfigNotification, channel: String) {
    with(notification) {
      log.debug("Sending config changed notification for application ${notification.application}")
      val imageUrl = "https://raw.githubusercontent.com/spinnaker/spinnaker.github.io/master/assets/images/md_icons/config_change.png"

      val headerText = "Delivery config changed"
      val altText = "config changed"

      val blocks = withBlocks {
        header {
          text(headerText, emoji = true)
        }
        val gitMetadata = notification.gitMetadata
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
              "View the current <${gitDataGenerator.generateConfigUrl(application)}|config as JSON>."
            )
            accessory {
              image(imageUrl = imageUrl, altText = altText)
            }
          }
        }

      }
      slackService.sendSlackNotification(channel, blocks, application = application, type = supportedTypes, fallbackText = headerText)
    }
  }
}
