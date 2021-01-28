package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.slack.SlackNotificationEvent
import com.netflix.spinnaker.keel.slack.SlackService
import com.netflix.spinnaker.keel.slack.SlackPausedNotification
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Sends notification when pausing an application
 */
@Component
class PausedNotificationHandler (
  private val slackService: SlackService,
  @Value("\${spinnaker.baseUrl}") private val spinnakerBaseUrl: String
) : SlackNotificationHandler<SlackPausedNotification>{

  override val type: NotificationType = NotificationType.APPLICATION_PAUSED

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(notification: SlackPausedNotification, channel: String) {
    log.debug("Sending paused notification for application ${notification.application}")

    with(notification) {
      val username = user?.let { slackService.getUsernameByEmail(it) }
      val appUrl = "$spinnakerBaseUrl/#/applications/${application}"

      val blocks = withBlocks {
        header {
          text("Management is paused for $application", emoji = true)
        }

        section {
          markdownText("$username paused at <!date^${time.epochSecond}^{date_num} {time_secs}|fallback-text-include-PST>")
          accessory {
            image(imageUrl = "https://raw.githubusercontent.com/gcomstock/managed.delivery/master/src/icons/md_paused.png", altText = "paused")
          }
        }

        section {
          markdownText("_This app's deployments must be via <$appUrl/executions|pipelines> until management is resumed_")
          accessory {
            button {
              text("More...")
              actionId("button-action")
              url("$appUrl/environments")
            }
          }
        }

      }
      slackService.sendSlackNotification(channel, blocks, application = application, type = type)
    }
  }

}
