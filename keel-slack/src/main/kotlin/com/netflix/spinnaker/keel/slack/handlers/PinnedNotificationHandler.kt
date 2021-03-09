package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.slack.SlackPinnedNotification
import com.netflix.spinnaker.keel.slack.SlackService
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.apache.logging.log4j.util.Strings
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Sends notification when pinning an artifact
 */
@Component
class PinnedNotificationHandler(
  private val slackService: SlackService,
  private val gitDataGenerator: GitDataGenerator
) : SlackNotificationHandler<SlackPinnedNotification> {

  override val supportedTypes = listOf(NotificationType.ARTIFACT_PINNED)
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(notification: SlackPinnedNotification, channel: String) {
    with(notification) {
      log.debug("Sending pinned artifact notification for application $application")

      val env = Strings.toRootUpperCase(pin.targetEnvironment)
      val username = pin.pinnedBy?.let { slackService.getUsernameByEmail(it) }
      val headerText = "$env is pinned"
      val imageUrl = "https://raw.githubusercontent.com/gcomstock/managed.delivery/master/src/icons/pinned_r2.png"

      val blocks = withBlocks {
        header {
          text(headerText, emoji = true)
        }

        section {
          val olderVersion = "#${currentArtifact.buildMetadata?.number}"
          gitDataGenerator.generateCommitInfo(this,
            application,
            imageUrl,
            pinnedArtifact,
            "pinned",
            olderVersion,
            env)
        }

        section {
          gitDataGenerator.generateScmInfo(this,
            application,
            pinnedArtifact)
        }
        context {
          elements {
            markdownText("$username pinned on <!date^${time.epochSecond}^{date_num} {time_secs}|fallback-text-include-PST>: \"${pin.comment}\"")
          }
        }

      }
      slackService.sendSlackNotification(channel, blocks, application = application, type = supportedTypes, fallbackText = headerText)
    }
  }

}
