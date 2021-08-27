package com.netflix.spinnaker.keel.notifications.slack.handlers

import com.netflix.spinnaker.config.BaseUrlConfig
import com.netflix.spinnaker.keel.api.NotificationDisplay
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.notifications.slack.SlackResumedNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackService
import com.slack.api.model.block.LayoutBlock
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

  private fun SlackResumedNotification.headerText(): String {
    return "[$application] resumed"
  }

  private fun SlackResumedNotification.toBlocks(): List<LayoutBlock> {
    val headerText = ":arrow_forward: Management resumed for ${linkedApp(baseUrlConfig, application)}"
    val username = user?.let { slackService.getUsernameByEmail(it) }
    val text = "$username resumed management at <!date^${time.epochSecond}^{date_num} {time_secs}|fallback-text-include-PST>"

    return withBlocks {
      section {
        markdownText(headerText + "\n" + text)
      }

      section {
        markdownText("_This app will now be handled according to your managed delivery config_")
      }
    }
  }

  override fun sendMessage(
    notification: SlackResumedNotification,
    channel: String,
    notificationDisplay: NotificationDisplay
  ) {
    with(notification) {
      log.debug("Sending resume management notification for application $application")

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
