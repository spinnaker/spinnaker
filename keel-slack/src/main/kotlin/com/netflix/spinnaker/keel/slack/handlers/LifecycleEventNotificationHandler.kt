package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.slack.SlackLifecycleNotification
import com.netflix.spinnaker.keel.slack.SlackService
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.apache.logging.log4j.util.Strings
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Sends notification for a lifecycle event, like bake / build failures
 */
@Component
class LifecycleEventNotificationHandler (
  private val slackService: SlackService,
  @Value("\${spinnaker.baseUrl}") private val spinnakerBaseUrl: String,
  private val gitDataGenerator: GitDataGenerator
) : SlackNotificationHandler<SlackLifecycleNotification> {

  override val type: NotificationType = NotificationType.LIFECYCLE_EVENT

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(notification: SlackLifecycleNotification, channel: String) {
    log.debug("Sending lifecycle event notification for application ${notification.application}")

    with(notification) {
      val imageUrl = when (eventType) {
          LifecycleEventType.BAKE -> "https://raw.githubusercontent.com/gcomstock/managed.delivery/master/src/icons/bake_fail.png"
          LifecycleEventType.BUILD -> "https://raw.githubusercontent.com/gcomstock/managed.delivery/master/src/icons/build_fail.png"
          else -> Strings.EMPTY
      }

      val artifactUrl = "$spinnakerBaseUrl/#/applications/${application}/environments/${artifact.reference}/${artifact.version}"

      val blocks = withBlocks {
        header {
          text("${eventType.name} failed", emoji = true)
        }

        section {
          with(artifact) {
            if (buildMetadata != null && gitMetadata != null && gitMetadata!!.commitInfo != null) {
              markdownText("*Version:* <$artifactUrl|#${buildMetadata!!.number}> " +
                "by @${gitMetadata!!.author}\n " +
                "${gitMetadata!!.commitInfo?.message}")
              accessory {
                image(imageUrl = imageUrl, altText = "lifecycle")
              }
            } else {
              log.debug("either git metadata or build metadata is null when trying to send SlackLifecycleNotification for application $application")
            }
          }
        }

        section {
          gitDataGenerator.generateData(this, application, artifact)
        }

      }
      slackService.sendSlackNotification(channel, blocks, application = application, type = type)
    }
  }

}
