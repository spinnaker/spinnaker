package com.netflix.spinnaker.keel.slack

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.SlackConfiguration
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.slack.api.Slack
import com.slack.api.model.block.LayoutBlock
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

  fun sendSlackNotification(channel: String, blocks: List<LayoutBlock>, token: String? = null,
                            application: String, type: NotificationType) {
    if (isSlackEnabled) {
      log.debug("sending slack notification for channel $channel")

      val actualToken = token ?: configToken

      val response = slack.methods(actualToken).chatPostMessage { req ->
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

      log.debug("response: ${response.message}")
      log.debug("response metadata: ${response.responseMetadata}")

    } else {
      log.debug("new slack integration is not enabled")
    }
  }

  fun getUsernameByEmail(email: String): String {
    log.debug("lookup user id for email $email")
    val response = slack.methods(configToken).usersLookupByEmail { req ->
      req.email(email)
    }
    log.debug("slack returned ${response.isOk}")

    if (response.user != null && response.user.name != null) {
      return "@${response.user.name}"
    }
    return email
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
