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

  override val types = listOf(NotificationType.ARTIFACT_UNPINNED)
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(notification: SlackUnpinnedNotification, channel: String) {
    log.debug("Sending unpinned artifact notification for application ${notification.application}")

    with(notification) {
      val env = Strings.toRootUpperCase(targetEnvironment)
      val artifactUrl = "$spinnakerBaseUrl/#/applications/${application}/environments/${latestArtifact?.reference}/${latestArtifact?.version}"
      val username = slackService.getUsernameByEmail(user)
      val headerText = "$env was unpinned"

      val blocks = withBlocks {
        header {
          text(headerText, emoji = true)
        }

        section {
          if (latestArtifact?.buildMetadata != null
            && latestArtifact.gitMetadata != null && latestArtifact.gitMetadata!!.commitInfo != null) {

            markdownText("*Version:* ~#${pinnedArtifact?.buildMetadata?.number}~ → <$artifactUrl|#${latestArtifact.buildMetadata!!.number}> " +
              "by @${latestArtifact.gitMetadata!!.author}\n " +
              "*Where:* $env\n\n " +
              "${latestArtifact.gitMetadata!!.commitInfo?.message}")
            accessory {
              image(imageUrl = "https://raw.githubusercontent.com/gcomstock/managed.delivery/master/src/icons/unpinned.png", altText = "unpinned")
            }
          }
        }

        section {
          if (latestArtifact != null) {
            gitDataGenerator.generateData(this, application, latestArtifact)
          }
        }
        context {
          elements {
            markdownText("$username unpinned on <!date^${time.epochSecond}^{date_num} {time_secs}|fallback-text-include-PST>")
          }
        }

      }
      slackService.sendSlackNotification(channel, blocks, application = application, type = types, fallbackText = headerText)
    }
  }
}
