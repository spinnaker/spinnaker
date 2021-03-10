package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.slack.SlackService
import com.netflix.spinnaker.keel.slack.SlackUnpinnedNotification
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.apache.logging.log4j.util.Strings
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Sends notification when unpinning an artifact
 */
@Component
class UnpinnedNotificationHandler(
  private val slackService: SlackService,
  private val gitDataGenerator: GitDataGenerator,
  @Value("\${spinnaker.baseUrl}") private val spinnakerBaseUrl: String,
) : SlackNotificationHandler<SlackUnpinnedNotification> {

  override val supportedTypes = listOf(NotificationType.ARTIFACT_UNPINNED)
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(notification: SlackUnpinnedNotification, channel: String) {
    with(notification) {
      log.debug("Sending unpinned artifact notification for application $application")

      val env = Strings.toRootUpperCase(targetEnvironment)
      val username = slackService.getUsernameByEmail(user)
      val usernameThatPinned: String = if (originalPin.pinnedBy != null ) {
        slackService.getUsernameByEmail(originalPin.pinnedBy!!)
      } else originalPin.pinnedBy!!

      val buildNumberText = when (pinnedArtifact?.buildNumber) {
        null -> ""
        else -> " from #${pinnedArtifact.buildNumber}"
      }
      val headerText = "$env was unpinned${buildNumberText}"
      val imageUrl = "https://raw.githubusercontent.com/spinnaker/spinnaker.github.io/master/assets/images/md_icons/unpinned.png"

      val blocks = withBlocks {
        header {
          text(headerText, emoji = true)
        }

        context {
          elements {
            markdownText("$username unpinned on <!date^${time.epochSecond}^{date_num} {time_secs}|fallback-text-include-PST>")
          }
        }

        context {
          elements {
            markdownText("$usernameThatPinned originally pinned on " +
              "<!date^${originalPin.pinnedAt!!.epochSecond}^{date_num} {time_secs}|fallback-text-include-PST>" +
              ": \"${originalPin.comment}\"")
          }
        }

        section {
          if (latestArtifact != null) {
            gitDataGenerator.generateUnpinCommitInfo(this,
              application,
              imageUrl,
              latestArtifact,
              "unpinned",
              env)
          }
        }
        if (pinnedArtifact != null) {
          gitDataGenerator.conditionallyAddFullCommitMsgButton(this, pinnedArtifact)
        }

        section {
          if (latestArtifact != null) {
            gitDataGenerator.generateScmInfo(this, application, latestArtifact)
          }
        }
      }
      slackService.sendSlackNotification(channel, blocks, application = application, type = supportedTypes, fallbackText = headerText)
    }
  }
}
