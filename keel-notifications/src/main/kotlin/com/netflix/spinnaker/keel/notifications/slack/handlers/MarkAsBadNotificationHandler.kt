package com.netflix.spinnaker.keel.notifications.slack.handlers

import com.netflix.spinnaker.config.BaseUrlConfig
import com.netflix.spinnaker.keel.api.NotificationDisplay
import com.netflix.spinnaker.keel.api.NotificationDisplay.*
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
    val env = Strings.toRootUpperCase(targetEnvironment)
    return ":x: $application ${vetoedArtifact.buildNumber ?: vetoedArtifact.version} marked as bad in $env"
  }

  private fun SlackMarkAsBadNotification.compactMessage(): List<LayoutBlock> =
    withBlocks {
      val vetoedLink = "<${gitDataGenerator.generateArtifactUrl(application, vetoedArtifact.reference, vetoedArtifact.version)}|#${vetoedArtifact.buildNumber ?: vetoedArtifact.version}>"
      val env = Strings.toRootUpperCase(targetEnvironment)
      val header = ":x: ${gitDataGenerator.linkedApp(application)} build $vetoedLink marked as bad in $env"
      section {
        markdownText(header)
      }
    }

  private fun SlackMarkAsBadNotification.normalMessage(): List<LayoutBlock> {
    val env = Strings.toRootUpperCase(targetEnvironment)
    val buildNumber = vetoedArtifact.buildMetadata?.number ?: vetoedArtifact.buildMetadata?.id
    val headerText = "#$buildNumber Marked as bad in $env"
    val imageUrl = "https://raw.githubusercontent.com/spinnaker/spinnaker.github.io/master/assets/images/md_icons/marked_as_bad.png"
    return withBlocks {
      header {
        text(headerText, emoji = true)
      }

      section {
        gitDataGenerator.generateCommitInfo(
          this,
          application,
          imageUrl,
          vetoedArtifact,
          "vetoed",
          env = env
        )
      }
      val gitMetadata = vetoedArtifact.gitMetadata
      if (gitMetadata != null) {
        gitDataGenerator.conditionallyAddFullCommitMsgButton(this, gitMetadata)
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

      val uniqueBlocks = when(notificationDisplay) {
        NORMAL -> notification.normalMessage()
        COMPACT -> notification.compactMessage()
      }

      val username = slackService.getUsernameByEmail(user)

      val commonBottomBlocks = withBlocks {
        val gitMetadata = vetoedArtifact.gitMetadata
        if (gitMetadata != null) {
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
      slackService.sendSlackNotification(
        channel,
        uniqueBlocks + commonBottomBlocks,
        application = application,
        type = supportedTypes,
        fallbackText = headerText()
      )
    }
  }
}
