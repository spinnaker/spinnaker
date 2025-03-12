package com.netflix.spinnaker.keel.notifications.slack.handlers

import com.netflix.spinnaker.config.BaseUrlConfig
import com.netflix.spinnaker.keel.api.NotificationDisplay
import com.netflix.spinnaker.keel.notifications.NotificationType.ARTIFACT_DEPLOYMENT_FAILED
import com.netflix.spinnaker.keel.notifications.NotificationType.ARTIFACT_DEPLOYMENT_SUCCEEDED
import com.netflix.spinnaker.keel.notifications.slack.DeploymentStatus.FAILED
import com.netflix.spinnaker.keel.notifications.slack.DeploymentStatus.SUCCEEDED
import com.netflix.spinnaker.keel.notifications.slack.SlackArtifactDeploymentNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackService
import com.slack.api.model.block.LayoutBlock
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Sends notification based on artifact deployment status --> deployed successfully, or failed to deploy
 */
@Component
@EnableConfigurationProperties(BaseUrlConfig::class)
class ArtifactDeploymentNotificationHandler(
  private val slackService: SlackService,
  private val gitDataGenerator: GitDataGenerator,
) : SlackNotificationHandler<SlackArtifactDeploymentNotification> {

  override val supportedTypes = listOf(ARTIFACT_DEPLOYMENT_SUCCEEDED, ARTIFACT_DEPLOYMENT_FAILED)
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private fun SlackArtifactDeploymentNotification.headerText(): String {
    val verb =  when (status) {
      FAILED -> "failed"
      SUCCEEDED ->  "succeeded"
    }
    return "[$application] deploy $verb for ${artifact.buildNumber ?: artifact.version} to ${targetEnvironment.toLowerCase()}"
  }

  private fun SlackArtifactDeploymentNotification.toBlocks(): List<LayoutBlock> {
    val (emoji, verb) =  when (status) {
      FAILED -> ":x::ship:" to "failed to deploy"
      SUCCEEDED ->  ":ship:" to "deployed"
    }

    return withBlocks {
      gitDataGenerator.notificationBodyWithEnv(this, emoji, application, artifact, verb, targetEnvironment)

      artifact.gitMetadata?.let { gitMetadata ->
        section {
          gitDataGenerator.generateScmInfo(this, application, gitMetadata, artifact)
        }
      }
    }
  }

  override fun sendMessage(
    notification: SlackArtifactDeploymentNotification,
    channel: String,
    notificationDisplay: NotificationDisplay
  ) {
    with(notification) {
      log.debug("Sending artifact deployment notification with status $status for application ${notification.application}")

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
