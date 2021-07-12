package com.netflix.spinnaker.keel.notifications.slack.handlers

import com.netflix.spinnaker.keel.api.NotificationDisplay
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.notifications.slack.SlackMarkAsBadNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackService
import com.slack.api.model.block.LayoutBlock
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.apache.logging.log4j.util.Strings
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Sends notification when marking an artifact as bad
 */
@Component
class MarkAsBadNotificationHandler(
  private val slackService: SlackService,
  private val gitDataGenerator: GitDataGenerator,
) : SlackNotificationHandler<SlackMarkAsBadNotification> {

  override val supportedTypes = listOf(NotificationType.ARTIFACT_MARK_AS_BAD)
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private fun SlackMarkAsBadNotification.headerText(): String {
    return "[$application] ${vetoedArtifact.buildNumber ?: vetoedArtifact.version} marked as bad in ${targetEnvironment.toLowerCase()}"
  }

  private fun SlackMarkAsBadNotification.toBlocks(): List<LayoutBlock> {
    val username = slackService.getUsernameByEmail(user)

    return withBlocks {
      gitDataGenerator.notificationBodyWithEnv(this, ":broken_heart:", application, vetoedArtifact, "marked as bad", targetEnvironment, preposition = "in")

      vetoedArtifact.gitMetadata?.let { gitMetadata ->
        section {
          gitDataGenerator.generateScmInfo(this, application, gitMetadata, vetoedArtifact)
        }
      }

      context {
        elements {
          markdownText("$username marked as bad on <!date^${time.epochSecond}^{date_num} {time_secs}|fallback-text-include-PST>: \"${comment}\"")
        }
      }
    }
  }

  override fun sendMessage(
    notification: SlackMarkAsBadNotification,
    channel: String,
    notificationDisplay: NotificationDisplay
  ) {
    log.debug("Sending mark as bad artifact notification for application ${notification.application}")

    with(notification) {
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
