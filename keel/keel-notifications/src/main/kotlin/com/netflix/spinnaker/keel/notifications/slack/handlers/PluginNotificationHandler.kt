package com.netflix.spinnaker.keel.notifications.slack.handlers

import com.netflix.spinnaker.config.BaseUrlConfig
import com.netflix.spinnaker.keel.api.NotificationDisplay
import com.netflix.spinnaker.keel.api.plugins.PluginNotificationsStatus.FAILED
import com.netflix.spinnaker.keel.api.plugins.PluginNotificationsStatus.SUCCEEDED
import com.netflix.spinnaker.keel.notifications.NotificationType.PLUGIN_NOTIFICATION_NORMAL
import com.netflix.spinnaker.keel.notifications.NotificationType.PLUGIN_NOTIFICATION_QUIET
import com.netflix.spinnaker.keel.notifications.NotificationType.PLUGIN_NOTIFICATION_VERBOSE
import com.netflix.spinnaker.keel.notifications.slack.SlackPluginNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackService
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Sends notifications for plugins
 */
@Component
@EnableConfigurationProperties(BaseUrlConfig::class)
class PluginNotificationHandler(
  private val slackService: SlackService,
  private val gitDataGenerator: GitDataGenerator,
  val springEnv: Environment,
): SlackNotificationHandler<SlackPluginNotification> {
  override val supportedTypes = listOf(
    PLUGIN_NOTIFICATION_VERBOSE,
    PLUGIN_NOTIFICATION_NORMAL,
    PLUGIN_NOTIFICATION_QUIET,
  )
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val pluginNotificationsEnabled: Boolean
    get() = springEnv.getProperty("keel.plugins.notifications.enabled", Boolean::class.java, true)

  override fun sendMessage(
    notification: SlackPluginNotification,
    channel: String,
    notificationDisplay: NotificationDisplay
  ) {
    if (!pluginNotificationsEnabled) {
      return
    }

    with(notification) {
      log.debug("Sending plugin notification for application ${notification.application} and environment ${notification.targetEnvironment}")

      val emoji = when(config.status) {
        FAILED -> ":x: :gear:"
        SUCCEEDED -> ":white_check_mark: :gear:"
      }

      val blocks = withBlocks {
        gitDataGenerator.notificationBody(this, emoji, application, artifactVersion, config.title)
        section {
          markdownText("\n\n_${config.message}_")
        }

        val link = config.buttonLink
        val buttonText = config.buttonText
        if (buttonText != null && link != null) {
          actions {
            elements {
              button {
                text(buttonText)
                // action id will be consisted by 3 sections with ":" between them to keep it consistent
                actionId("button:${config.provenance}:link")
                url(link)
              }
            }
          }
        }

        artifactVersion.gitMetadata?.let { gitMetadata ->
          section {
            gitDataGenerator.generateScmInfo(this, application, gitMetadata, artifactVersion)
          }
        }
      }
      slackService.sendSlackNotification(channel, blocks, application = application, type = supportedTypes, fallbackText = "[$application] ${config.title}")
    }
  }
}
