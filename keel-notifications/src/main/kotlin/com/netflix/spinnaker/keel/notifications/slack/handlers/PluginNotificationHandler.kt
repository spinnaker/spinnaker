package com.netflix.spinnaker.keel.notifications.slack.handlers

import com.netflix.spinnaker.config.BaseUrlConfig
import com.netflix.spinnaker.keel.api.NotificationDisplay
import com.netflix.spinnaker.keel.api.plugins.PluginNotificationsStatus.FAILED
import com.netflix.spinnaker.keel.api.plugins.PluginNotificationsStatus.SUCCEEDED
import com.netflix.spinnaker.keel.notifications.NotificationType.*
import com.netflix.spinnaker.keel.notifications.slack.SlackPluginNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackService
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.apache.logging.log4j.util.Strings
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

      val imageUrl = when (config.status) {
        FAILED -> "https://raw.githubusercontent.com/spinnaker/spinnaker.github.io/master/assets/images/md_icons/plugin_fail.png"
        SUCCEEDED -> "https://raw.githubusercontent.com/spinnaker/spinnaker.github.io/master/assets/images/md_icons/plugin_success.png"
      }

      val env = Strings.toRootUpperCase(targetEnvironment)
      val title = config.title

      val blocks = withBlocks {
        header {
          text(title, emoji = true)
        }

        val versionMarkdown = gitDataGenerator.generateVersionMarkdown(
          application,
          artifactVersion.reference,
          artifactVersion,
          null
        )

        section {
          markdownText("*App:* $application\n" +
            "*Environment:* $env\n" +
            "$versionMarkdown\n" +
            config.message
          )
          accessory {
            image(imageUrl = imageUrl, altText = title)
          }
        }

        val link = config.buttonLink
        val text = config.buttonText
        if (text != null && link != null) {
          actions {
            elements {
              button {
                text(text)
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
      slackService.sendSlackNotification(channel, blocks, application = application, type = supportedTypes, fallbackText = title)
    }
  }
}
