package com.netflix.spinnaker.keel.notifications.slack.handlers

import com.netflix.spinnaker.config.BaseUrlConfig
import com.netflix.spinnaker.keel.api.NotificationDisplay
import com.netflix.spinnaker.keel.api.NotificationDisplay.COMPACT
import com.netflix.spinnaker.keel.api.NotificationDisplay.NORMAL
import com.netflix.spinnaker.keel.notifications.DEPLOY_FAILED_ICON
import com.netflix.spinnaker.keel.notifications.DEPLOY_SUCCEEDED_ICON
import com.netflix.spinnaker.keel.notifications.NotificationType.ARTIFACT_DEPLOYMENT_FAILED
import com.netflix.spinnaker.keel.notifications.NotificationType.ARTIFACT_DEPLOYMENT_SUCCEEDED
import com.netflix.spinnaker.keel.notifications.slack.DeploymentStatus
import com.netflix.spinnaker.keel.notifications.slack.DeploymentStatus.*
import com.netflix.spinnaker.keel.notifications.slack.SlackArtifactDeploymentNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackService
import com.slack.api.model.block.LayoutBlock
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.apache.logging.log4j.util.Strings
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
  private val baseUrlConfig: BaseUrlConfig
) : SlackNotificationHandler<SlackArtifactDeploymentNotification> {

  override val supportedTypes = listOf(ARTIFACT_DEPLOYMENT_SUCCEEDED, ARTIFACT_DEPLOYMENT_FAILED)
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private fun SlackArtifactDeploymentNotification.headerText(): String {
    val (emoji, verb) =  when (status) {
      FAILED -> ":no_entry:" to "failed"
      SUCCEEDED ->  ":white_check_mark:" to "succeeded"
    }
    val env = Strings.toRootUpperCase(targetEnvironment)
    return "$emoji $application ${artifact.buildNumber ?: artifact.version} $verb deploying to $env"
  }

  private fun SlackArtifactDeploymentNotification.compactMessage(): List<LayoutBlock> =
    withBlocks {
      val env = Strings.toRootUpperCase(targetEnvironment)
      val linkedVersion = "<${gitDataGenerator.generateArtifactUrl(application, artifact.reference, artifact.version)}|#${artifact.buildNumber ?: artifact.version}>"
      val header = when (status) {
        FAILED -> ":no_entry: ${linkedApp(baseUrlConfig, application)} $linkedVersion failed to deploy to $env"
        SUCCEEDED ->  ":white_check_mark: ${linkedApp(baseUrlConfig, application)} build $linkedVersion deployed to $env"
      }
      section {
        markdownText(header)
      }
      val gitMetadata = artifact.gitMetadata
      if (gitMetadata != null) {
        section {
          gitDataGenerator.generateScmInfo(this, application, gitMetadata, artifact)
        }
      }
    }

  private fun SlackArtifactDeploymentNotification.normalMessage(): List<LayoutBlock> {
    val imageUrl = when (status) {
      FAILED -> DEPLOY_FAILED_ICON
      SUCCEEDED -> DEPLOY_SUCCEEDED_ICON
    }

    val env = Strings.toRootUpperCase(targetEnvironment)

    val headerText = when (status) {
      FAILED -> "Deploy failed to $env"
      SUCCEEDED -> "Deployed to $env"
    }

    return withBlocks {
      header {
        text(headerText, emoji = true)
      }

      section {
        var details = ""
        if (priorVersion != null) {
          val priorArtifactUrl = "${baseUrlConfig.baseUrl}/#/applications/${application}/environments/${artifact.reference}/${priorVersion.version}"
          details += "<$priorArtifactUrl|#${priorVersion.buildMetadata?.number}>"
        }

        gitDataGenerator.generateCommitInfo(this,
          application,
          imageUrl,
          artifact,
          "artifact_deployment",
          details,
          env)
      }
      val gitMetadata = artifact.gitMetadata
      if (gitMetadata != null) {
        gitDataGenerator.conditionallyAddFullCommitMsgButton(this, gitMetadata)
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

      val uniqueBlocks = when(notificationDisplay) {
        NORMAL -> notification.normalMessage()
        COMPACT -> notification.compactMessage()
      }

      slackService.sendSlackNotification(
        channel,
        uniqueBlocks,
        application = application,
        type = supportedTypes,
        fallbackText = headerText()
      )
    }
  }
}
