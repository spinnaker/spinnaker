package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.slack.SlackResumedNotification
import com.netflix.spinnaker.keel.slack.SlackService
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Sends notification when resuming management for an application
 */
@Component
class ResumedNotificationHandler(
  private val slackService: SlackService,
  @Value("\${spinnaker.baseUrl}") private val spinnakerBaseUrl: String,
) : SlackNotificationHandler<SlackResumedNotification> {

  override val supportedTypes = listOf(NotificationType.APPLICATION_RESUMED)
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(notification: SlackResumedNotification, channel: String) {
    with(notification) {
      log.debug("Sending resume management notification for application $application")

      val appUrl = "$spinnakerBaseUrl/#/applications/${application}"
      val username = user?.let { slackService.getUsernameByEmail(it) }
      val headerText = "Management resumed for $application"

      val blocks = withBlocks {
        header {
          text(headerText, emoji = true)
        }

        section {
          markdownText("$username resumed at <!date^${time.epochSecond}^{date_num} {time_secs}|fallback-text-include-PST>")
          accessory {
            image(imageUrl = "https://raw.githubusercontent.com/gcomstock/managed.delivery/master/src/icons/md_resumed.png", altText = "resumed")
          }
        }

        section {
          markdownText("_This app will now be handled according to your managed delivery config_")
          accessory {
            button {
              text("More...")
              actionId("button-action")
              url("$appUrl/environments")
            }
          }
        }

      }
      slackService.sendSlackNotification(channel, blocks, application = application, type = supportedTypes, fallbackText = headerText)
    }
  }

}
