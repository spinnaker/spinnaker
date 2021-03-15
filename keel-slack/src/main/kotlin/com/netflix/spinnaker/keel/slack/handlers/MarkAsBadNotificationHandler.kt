package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.slack.SlackMarkAsBadNotification
import com.netflix.spinnaker.keel.slack.SlackService
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

  override fun sendMessage(notification: SlackMarkAsBadNotification, channel: String) {
    log.debug("Sending mark as bad artifact notification for application ${notification.application}")

    with(notification) {
      val username = slackService.getUsernameByEmail(user)
      val env = Strings.toRootUpperCase(targetEnvironment)
      val buildNumber = vetoedArtifact.buildMetadata?.number ?: vetoedArtifact.buildMetadata?.id
      val headerText = "#$buildNumber Marked as bad in $env"
      val imageUrl = "https://raw.githubusercontent.com/spinnaker/spinnaker.github.io/master/assets/images/md_icons/marked_as_bad.png"

      val blocks = withBlocks {
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
            env = env)
        }
        val gitMetadata = vetoedArtifact.gitMetadata
        if (gitMetadata != null) {
          gitDataGenerator.conditionallyAddFullCommitMsgButton(this, gitMetadata)
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
      slackService.sendSlackNotification(channel, blocks, application = application, type = supportedTypes, fallbackText = headerText)
    }
  }
}
