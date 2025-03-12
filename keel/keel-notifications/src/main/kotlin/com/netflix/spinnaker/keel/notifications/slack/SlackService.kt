package com.netflix.spinnaker.keel.notifications.slack

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.SlackConfiguration
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.telemetry.safeIncrement
import com.slack.api.Slack
import com.slack.api.methods.response.chat.ChatPostMessageResponse
import com.slack.api.model.block.LayoutBlock
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * This notifier is responsible for actually sending or fetching data from Slack directly.
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

  /**
   * Sends slack notification to [channel], which the specified [blocks].
   * In case of an error with creating the blocks, or for notification preview, the fallback text will be sent.
   */
  fun sendSlackNotification(channel: String, blocks: List<LayoutBlock>,
                            application: String, type: List<NotificationType>,
                            fallbackText: String): ChatPostMessageResponse? {
    if (isSlackEnabled) {
      log.debug("Sending slack notification $type for application $application in channel $channel")

      val response = slack.methods(configToken).chatPostMessage { req ->
        req
          .channel(channel)
          .blocks(blocks)
          .text(fallbackText)
      }

      if (response.isOk) {
        spectator.counter(
          SLACK_MESSAGE_SENT,
          listOf(
            BasicTag("notificationType", type.first().name),
            BasicTag("application", application)
          )
        ).safeIncrement()
      }

      if (!response.isOk) {
        log.warn("slack couldn't send the notification $type for application $application in channel $channel. error is: ${response.error}, response: $response")
        spectator.counter(
          SLACK_MESSAGE_FAILED,
          listOf(
            BasicTag("notificationType", type.first().name),
            BasicTag("application", application)
          )
        ).safeIncrement()
        return response
      }

      log.debug("slack notification $type for application $application in channel $channel was successfully sent.")
      return response
    } else {
      log.debug("new slack integration is not enabled")
      return null
    }
  }

  /**
   * Updates an existing slack message with new text by finding the message and editing it
   */
  fun updateSlackMessage(
    channel: String,
    timestamp: String,
    blocks: List<LayoutBlock>,
    fallbackText: String,
    application: String
  ) {
    log.debug("Updating slack notification at timestamp $timestamp for application $application in channel $channel")
    val response = slack.methods(configToken).chatUpdate { req ->
      req
        .channel(channel)
        .ts(timestamp)
        .blocks(blocks)
        .text(fallbackText)
    }

    if (response.isOk) {
      spectator.counter(
        SLACK_MESSAGE_SENT,
        listOf(
          BasicTag("notificationType", "update"),
          BasicTag("application", application)
        )
      ).safeIncrement()
    }

    if (!response.isOk) {
      log.error("slack couldn't update the notification at timestamp $timestamp for application $application in channel $channel. error is: ${response.error}, response: $response")
      spectator.counter(
        SLACK_MESSAGE_FAILED,
        listOf(
          BasicTag("notificationType", "update"),
          BasicTag("application", application)
        )
      ).safeIncrement()
    }
  }

  /**
   * Get slack username by the user's [email]. Return the original email if username is not found.
   */
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
      return "<@${response.user.id}>"
    }
    return email
  }

  /**
   * Get slack username by the user's [email], apending the default domain if it is
   * not null and not present in [emailUsername].
   * Return the original email if username is not found.
   */
  fun getUsernameByEmailPrefix(emailUsername: String): String {
    val defaultDomain = slackConfig.defaultEmailDomain
    var email = emailUsername
    if (defaultDomain != null) {
      // sometimes we get responses from other systems that don't contain the full email
      // so we add the default domain to the username to construct our best guess at their email
      if (!emailUsername.contains(defaultDomain)) {
        email+= "@${defaultDomain}"
      }
    }
    log.debug("lookup user id for username $emailUsername - email guess $email")
    val response = slack.methods(configToken).usersLookupByEmail { req ->
      req.email(email)
    }

    if (!response.isOk) {
      log.warn("slack couldn't get username by email for $emailUsername - email guess $email. error is: ${response.error}")
      return emailUsername
    }

    if (response.user != null && response.user.name != null) {
      return "<@${response.user.id}>"
    }
    return emailUsername
  }

  /**
   * Get user's email address by slack [userId]. Return the original userId if email is not found.
   */
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

  companion object {
    private const val SLACK_MESSAGE_SENT = "keel.slack.message.sent"
    private const val SLACK_MESSAGE_FAILED = "keel.slack.message.failed"
  }
}
