package com.netflix.spinnaker.keel.notifications.slack.handlers

import com.netflix.spinnaker.config.BaseUrlConfig
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.notifications.slack.SlackPausedNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackService
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Sends notification when pausing an application
 */
@Component
@EnableConfigurationProperties(BaseUrlConfig::class)
class PausedNotificationHandler(
  private val slackService: SlackService,
  private val baseUrlConfig: BaseUrlConfig
) : SlackNotificationHandler<SlackPausedNotification> {

  override val supportedTypes = listOf(NotificationType.APPLICATION_PAUSED)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(notification: SlackPausedNotification, channel: String) {
    log.debug("Sending paused notification for application ${notification.application}")

    with(notification) {
      val username = user?.let { slackService.getUsernameByEmail(it) }
      val appUrl = "${baseUrlConfig.baseUrl}/#/applications/${application}"
      val headerText = "Management is paused for $application"

      val blocks = withBlocks {
        header {
          text(headerText, emoji = true)
        }

        section {
          markdownText("$username paused at <!date^${time.epochSecond}^{date_num} {time_secs}|fallback-text-include-PST>")
          accessory {
            image(imageUrl = "https://raw.githubusercontent.com/spinnaker/spinnaker.github.io/master/assets/images/md_icons/md_paused.png", altText = "paused")
          }
        }

        section {
          markdownText("_This app's deployments must be via <$appUrl/executions|pipelines> or tasks until management is resumed_")
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
