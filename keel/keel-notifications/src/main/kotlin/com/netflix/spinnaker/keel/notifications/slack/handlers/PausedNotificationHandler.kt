package com.netflix.spinnaker.keel.notifications.slack.handlers

import com.netflix.spinnaker.config.BaseUrlConfig
import com.netflix.spinnaker.keel.api.NotificationDisplay
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.notifications.slack.SlackPausedNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackService
import com.slack.api.model.block.LayoutBlock
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
  private val baseUrlConfig: BaseUrlConfig,
) : SlackNotificationHandler<SlackPausedNotification> {

  override val supportedTypes = listOf(NotificationType.APPLICATION_PAUSED)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private fun SlackPausedNotification.headerText(): String {
    return "[$application] paused"
  }

  private fun SlackPausedNotification.toBlocks(): List<LayoutBlock> {
    val appUrl = "${baseUrlConfig.baseUrl}/#/applications/${application}"
    val headerText = ":double_vertical_bar: Management paused for ${linkedApp(baseUrlConfig, application)}"

    val username = user?.let { slackService.getUsernameByEmail(it) }

    var text = "*$username paused at <!date^${time.epochSecond}^{date_num} {time_secs}|fallback-text-include-PST>*"
    if (comment != null) {
      text += ": \"$comment\""
    }

    return withBlocks {
      section {
        markdownText(headerText + "\n" + text)
      }

      section {
        markdownText("_This app's deployments must be via <$appUrl/executions|pipelines> or tasks until management is resumed_")
      }
    }
  }

  override fun sendMessage(
    notification: SlackPausedNotification,
    channel: String,
    notificationDisplay: NotificationDisplay
  ) {
    log.debug("Sending paused notification for application ${notification.application}")

    with(notification) {
      slackService.sendSlackNotification(
        channel,
        notification.toBlocks(),
        application = application,
        type = supportedTypes,
        fallbackText = headerText()
      )
    }
  }

}
