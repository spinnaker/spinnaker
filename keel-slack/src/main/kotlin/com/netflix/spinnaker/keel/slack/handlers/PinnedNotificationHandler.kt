package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.slack.SlackPinnedNotification
import com.netflix.spinnaker.keel.slack.SlackService
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.apache.logging.log4j.util.Strings
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Sends notification when pinning an artifact
 */
@Component
class PinnedNotificationHandler(
  private val slackService: SlackService,
  private val gitDataGenerator: GitDataGenerator,
  @Value("\${spinnaker.baseUrl}") private val spinnakerBaseUrl: String,
) : SlackNotificationHandler<SlackPinnedNotification> {

  override val types = listOf(NotificationType.ARTIFACT_PINNED)
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(notification: SlackPinnedNotification, channel: String) {
    log.debug("Sending pinned artifact notification for application ${notification.application}")

    with(notification) {
      val env = Strings.toRootUpperCase(pin.targetEnvironment)
      val username = pin.pinnedBy?.let { slackService.getUsernameByEmail(it) }
      val pinnedArtifactUrl = "$spinnakerBaseUrl/#/applications/${application}/environments/${pinnedArtifact.reference}/${pinnedArtifact.version}"
      val headerText = "$env is pinned"

      val blocks = withBlocks {
        header {
          text(headerText, emoji = true)
        }

        section {
          with(pinnedArtifact) {
            if (buildMetadata != null && gitMetadata != null && gitMetadata!!.commitInfo != null) {
              markdownText("*Version:* ~#${currentArtifact.buildMetadata?.number}~ → <$pinnedArtifactUrl|#${buildMetadata?.number}> " +
                "by @${gitMetadata?.author}\n " +
                "*Where:* $env\n\n " +
                "${gitMetadata?.commitInfo?.message}")
              accessory {
                image(imageUrl = "https://raw.githubusercontent.com/gcomstock/managed.delivery/master/src/icons/pinned.png", altText = "pinned")
              }
            }
          }
        }

        section {
          gitDataGenerator.generateData(this, application, pinnedArtifact)
        }
        context {
          elements {
            markdownText("$username pinned on <!date^${time.epochSecond}^{date_num} {time_secs}|fallback-text-include-PST>: \"${pin.comment}\"")
          }
        }

      }
      slackService.sendSlackNotification(channel, blocks, application = application, type = types, fallbackText = headerText)
    }
  }

}
