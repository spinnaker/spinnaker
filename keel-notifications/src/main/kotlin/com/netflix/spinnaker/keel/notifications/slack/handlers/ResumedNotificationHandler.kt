package com.netflix.spinnaker.keel.notifications.slack.handlers

import com.netflix.spinnaker.config.BaseUrlConfig
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.notifications.slack.SlackResumedNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackService
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Sends notification when resuming management for an application
 */
@Component
@EnableConfigurationProperties(BaseUrlConfig::class)
class ResumedNotificationHandler(
  private val slackService: SlackService,
  private val baseUrlConfig: BaseUrlConfig
) : SlackNotificationHandler<SlackResumedNotification> {

  override val supportedTypes = listOf(NotificationType.APPLICATION_RESUMED)
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(notification: SlackResumedNotification, channel: String) {
    with(notification) {
      log.debug("Sending resume management notification for application $application")

      val appUrl = "${baseUrlConfig.baseUrl}/#/applications/${application}"
      val username = user?.let { slackService.getUsernameByEmail(it) }
      val headerText = "Management resumed for $application"

      val blocks = withBlocks {
        header {
          text(headerText, emoji = true)
        }

        section {
          markdownText("$username resumed at <!date^${time.epochSecond}^{date_num} {time_secs}|fallback-text-include-PST>")
          accessory {
            image(imageUrl = "https://raw.githubusercontent.com/spinnaker/spinnaker.github.io/master/assets/images/md_icons/md_resumed.png", altText = "resumed")
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
