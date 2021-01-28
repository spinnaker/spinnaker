package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.slack.SlackMarkAsBadNotification
import com.netflix.spinnaker.keel.slack.SlackService
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.apache.logging.log4j.util.Strings
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Sends notification when marking an artifact as bad
 */
@Component
class MarkAsBadNotificationHandler(
  private val slackService: SlackService,
  private val gitDataGenerator: GitDataGenerator,
  @Value("\${spinnaker.baseUrl}") private val spinnakerBaseUrl: String,
) : SlackNotificationHandler<SlackMarkAsBadNotification> {

  override val type: NotificationType = NotificationType.ARTIFACT_MARK_AS_BAD
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(notification: SlackMarkAsBadNotification, channel: String) {
    log.debug("Sending mark as bad artifact notification for application ${notification.application}")

    with(notification) {
      val username = slackService.getUsernameByEmail(user)
      val env = Strings.toRootUpperCase(targetEnvironment)
      val buildNumber = vetoedArtifact.buildMetadata?.number?: vetoedArtifact.buildMetadata?.id

      val vetoedArtifactUrl = "$spinnakerBaseUrl/#/applications/${application}/environments/${vetoedArtifact.reference}/${vetoedArtifact.version}"
      val blocks = withBlocks {
        header {
          text("#$buildNumber Marked as bad in $env", emoji = true)
        }

        section {
          with(vetoedArtifact) {
            if (buildMetadata != null && gitMetadata != null && gitMetadata!!.commitInfo != null) {
              markdownText("*Version:* <$vetoedArtifactUrl|#${buildMetadata?.number}> " +
                "by @${gitMetadata?.author}\n " +
                "*Where:* $env\n\n " +
                "${gitMetadata?.commitInfo?.message}")
              accessory {
                image(imageUrl = "https://raw.githubusercontent.com/gcomstock/managed.delivery/master/src/icons/marked_as_bad.png", altText = "vetoed")
              }
            }
          }
        }

        section {
          gitDataGenerator.generateData(this, application, vetoedArtifact)
        }
        context {
          elements {
            markdownText("$username marked as bad on <!date^${time.epochSecond}^{date_num} {time_secs}|fallback-text-include-PST>: \"${comment}\"")
          }
        }

      }
      slackService.sendSlackNotification(channel, blocks, application = application, type = type)
    }
  }
}
