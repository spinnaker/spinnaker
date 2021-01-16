package com.netflix.spinnaker.keel.slack

import com.netflix.spinnaker.config.SlackConfiguration
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
class SlackNotifier(
  private val springEnv: Environment,
  val slackConfig: SlackConfiguration
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val isSlackEnabled: Boolean
    get() = springEnv.getProperty("keel.notifications.slack", Boolean::class.java, true)


  fun sendSlackNotification(channel: String, blocks: List<LayoutBlock>, token: String? = null) {
    if (isSlackEnabled) {
      log.debug("starting slack notification")
      val slack = Slack.getInstance()

      val actualToken = token ?: slackConfig.token

      val response = slack.methods(actualToken).chatPostMessage { req ->
        req
          .channel(channel)
          .blocks(blocks)
      }

      log.debug("response: ${response.message}")
      log.debug("response metadata: ${response.responseMetadata}")

    } else {
      log.debug("new slack integration is not enabled")
    }
  }
}
