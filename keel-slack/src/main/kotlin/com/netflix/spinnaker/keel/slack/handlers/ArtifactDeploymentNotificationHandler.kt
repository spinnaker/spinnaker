package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.slack.DeploymentStatus
import com.netflix.spinnaker.keel.slack.SlackArtifactDeploymentNotification
import com.netflix.spinnaker.keel.slack.SlackService
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.apache.logging.log4j.util.Strings
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Sends notification based on artifact deployment status --> deployed successfully, or failed to deployed
 */
@Component
class ArtifactDeploymentNotificationHandler(
  private val slackService: SlackService,
  private val gitDataGenerator: GitDataGenerator,
  @Value("\${spinnaker.baseUrl}") private val spinnakerBaseUrl: String,
) : SlackNotificationHandler<SlackArtifactDeploymentNotification> {

  override val type: NotificationType = NotificationType.ARTIFACT_DEPLOYMENT
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(notification: SlackArtifactDeploymentNotification, channel: String) {
    with(notification) {
      log.debug("Sending artifact deployment $status for application ${notification.application}")

      with(notification) {
        val imageUrl = when (status) {
          DeploymentStatus.FAILED -> "https://raw.githubusercontent.com/gcomstock/managed.delivery/master/src/icons/deploy_fail.png"
          DeploymentStatus.SUCCEEDED -> "https://raw.githubusercontent.com/gcomstock/managed.delivery/master/src/icons/deploy_success.png"
        }

        val headerText = when (status){
          DeploymentStatus.FAILED -> "Deploy failed"
          DeploymentStatus.SUCCEEDED -> "Deployed"

        }

        val env = Strings.toRootUpperCase(targetEnvironment)

        val deployedArtifactUrl = "$spinnakerBaseUrl/#/applications/${application}/environments/${artifact.reference}/${artifact.version}"
        val blocks = withBlocks {
          header {
            text("$headerText to $env", emoji = true)
          }

          section {
            with(artifact) {
              var details = ""
              if (priorVersion != null) {
                val priorArtifactUrl = "$spinnakerBaseUrl/#/applications/${application}/environments/${artifact.reference}/${priorVersion.version}"
                details += "~<$priorArtifactUrl|#${priorVersion.buildMetadata?.number}>~ →"
              }
              if (buildMetadata != null && gitMetadata != null && gitMetadata!!.commitInfo != null) {
                markdownText("*Version:* $details <$deployedArtifactUrl|#${buildMetadata?.number}> " +
                  "by @${gitMetadata?.author}\n " +
                  "*Where:* $env\n\n " +
                  "${gitMetadata?.commitInfo?.message}")
                accessory {
                  image(imageUrl = imageUrl, altText = "artifact_deployment")
                }
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
}
