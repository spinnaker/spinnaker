package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.slack.SlackLifecycleNotification
import com.netflix.spinnaker.keel.slack.SlackService
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.apache.logging.log4j.util.Strings
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Sends notification for a lifecycle event, like bake / build failures
 */
@Component
class LifecycleEventNotificationHandler(
  private val slackService: SlackService,
  private val gitDataGenerator: GitDataGenerator
) : SlackNotificationHandler<SlackLifecycleNotification> {

  override val supportedTypes = listOf(NotificationType.LIFECYCLE_EVENT)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(notification: SlackLifecycleNotification, channel: String) {
    with(notification) {
      log.debug("Sending lifecycle event $eventType notification for application ${notification.application}")

      val imageUrl = when (eventType) {
        LifecycleEventType.BAKE -> "https://raw.githubusercontent.com/gcomstock/managed.delivery/master/src/icons/bake_fail.png"
        LifecycleEventType.BUILD -> "https://raw.githubusercontent.com/gcomstock/managed.delivery/master/src/icons/build_fail.png"
        else -> Strings.EMPTY
      }

      val headerText = "${eventType.name} failed"

      val blocks = withBlocks {
        header {
          text(headerText, emoji = true)
        }

        section {
          gitDataGenerator.generateCommitInfo(this, application, imageUrl, artifact, "lifecycle")
        }

        section {
          gitDataGenerator.generateScmInfo(this, application, artifact)
        }

      }
      slackService.sendSlackNotification(channel, blocks, application = application, type = supportedTypes, fallbackText = headerText)
    }
  }

}
