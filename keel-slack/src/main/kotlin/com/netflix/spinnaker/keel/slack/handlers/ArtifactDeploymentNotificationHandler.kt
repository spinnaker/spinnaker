package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.keel.notifications.NotificationType.ARTIFACT_DEPLOYMENT_FAILED
import com.netflix.spinnaker.keel.notifications.NotificationType.ARTIFACT_DEPLOYMENT_SUCCEEDED
import com.netflix.spinnaker.keel.slack.DeploymentStatus
import com.netflix.spinnaker.keel.slack.SlackArtifactDeploymentNotification
import com.netflix.spinnaker.keel.slack.SlackService
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.apache.logging.log4j.util.Strings
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Sends notification based on artifact deployment status --> deployed successfully, or failed to deploy
 */
@Component
class ArtifactDeploymentNotificationHandler(
  private val slackService: SlackService,
  private val gitDataGenerator: GitDataGenerator,
  @Value("\${spinnaker.baseUrl}") private val spinnakerBaseUrl: String,
) : SlackNotificationHandler<SlackArtifactDeploymentNotification> {

  override val supportedTypes = listOf(ARTIFACT_DEPLOYMENT_SUCCEEDED, ARTIFACT_DEPLOYMENT_FAILED)
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(notification: SlackArtifactDeploymentNotification, channel: String) {
    with(notification) {
      log.debug("Sending artifact deployment notification with status $status for application ${notification.application}")

      val imageUrl = when (status) {
        DeploymentStatus.FAILED -> "https://raw.githubusercontent.com/spinnaker/spinnaker.github.io/master/assets/images/md_icons/deploy_fail.png"
        DeploymentStatus.SUCCEEDED -> "https://raw.githubusercontent.com/spinnaker/spinnaker.github.io/master/assets/images/md_icons/deploy_success.png"
      }

      val env = Strings.toRootUpperCase(targetEnvironment)

      val headerText = when (status) {
        DeploymentStatus.FAILED -> "Deploy failed to $env"
        DeploymentStatus.SUCCEEDED -> "Deployed to $env"
      }

      val blocks = withBlocks {
        header {
          text(headerText, emoji = true)
        }

        section {
          var details = ""
          if (priorVersion != null) {
            val priorArtifactUrl = "$spinnakerBaseUrl/#/applications/${application}/environments/${artifact.reference}/${priorVersion.version}"
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

        section {
          gitDataGenerator.generateScmInfo(this, application, artifact)
        }

      }
      slackService.sendSlackNotification(channel, blocks, application = application, type = supportedTypes, fallbackText = headerText)
    }
  }
}
