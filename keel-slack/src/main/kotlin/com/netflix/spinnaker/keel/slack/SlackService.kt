package com.netflix.spinnaker.keel.slack

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.SlackConfiguration
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.slack.api.Slack
import com.slack.api.model.block.LayoutBlock
import com.slack.api.webhook.Payload.PayloadBuilder
import com.slack.api.webhook.WebhookPayloads.payload
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * This notifier is responsible for actually sending the Slack notification,
 * based on the [channel] and the [blocks] it gets from the different handlers.
 */
@Component
@EnableConfigurationProperties(SlackConfiguration::class)
class SlackService(
  private val springEnv: Environment,
  final val slackConfig: SlackConfiguration,
  private val spectator: Registry
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val configToken = slackConfig.token
  private val slack = Slack.getInstance()

  private val isSlackEnabled: Boolean
    get() = springEnv.getProperty("keel.notifications.slack", Boolean::class.java, true)

  fun sendSlackNotification(channel: String, blocks: List<LayoutBlock>,
                            application: String, type: NotificationType) {
    if (isSlackEnabled) {
      log.debug("Sending slack notification $type for application $application in channel $channel")

      val response = slack.methods(configToken).chatPostMessage { req ->
        req
          .channel(channel)
          .blocks(blocks)
      }

      if (response.isOk) {
        spectator.counter(
          SLACK_MESSAGE_SENT,
          listOf(
            BasicTag("notificationType", type.name),
            BasicTag("application", application)
          )
        ).safeIncrement()
      }

      if (!response.isOk) {
        log.warn("slack couldn't send the notification. error is: ${response.error}")
        return
      }

      log.debug("slack notification $type for application $application and channel $channel was successfully sent.")

    } else {
      log.debug("new slack integration is not enabled")
    }
  }

  fun getUsernameByEmail(email: String): String {
    log.debug("lookup user id for email $email")
    val response = slack.methods(configToken).usersLookupByEmail { req ->
      req.email(email)
    }

    if (!response.isOk) {
      log.warn("slack couldn't get username by email. error is: ${response.error}")
      return email
    }

    if (response.user != null && response.user.name != null) {
      return "@${response.user.name}"
    }
    return email
  }

  fun getEmailByUserId(userId: String): String {
    log.debug("lookup user email for username $userId")
    val response = slack.methods(configToken).usersInfo { req ->
      req.user(userId)
    }

    if (!response.isOk) {
      log.warn("slack couldn't get email by user id. error is: ${response.error}")
      return userId
    }

    log.debug("slack getEmailByUserId returned ${response.isOk}")

    if (response.user != null && response.user.profile.email != null) {
      return response.user.profile.email
    }
    return userId
  }

  // Update a notification based on the response Url, using blocks (the actual notification).
  // If something failed, the fallback text will be displayed
  fun respondToCallback(responseUrl: String, blocks: List<LayoutBlock>, fallbackText: String) {
    val response = slack.send(responseUrl, payload { p: PayloadBuilder ->
      p
        .text(fallbackText)
        .blocks(blocks)
    })

    log.debug("slack respondToCallback returned ${response.code}")
  }


  companion object {
    private const val SLACK_MESSAGE_SENT = "keel.slack.message.sent"
  }

  private fun Counter.safeIncrement() =
    try {
      increment()
    } catch (ex: Exception) {
      log.error("Exception incrementing {} counter: {}", id().name(), ex.message)
    }
}
