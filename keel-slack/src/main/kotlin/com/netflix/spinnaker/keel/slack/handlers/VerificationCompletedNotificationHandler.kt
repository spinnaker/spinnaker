package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.FAIL
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.slack.SlackService
import com.netflix.spinnaker.keel.slack.SlackVerificationCompletedNotification
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class VerificationCompletedNotificationHandler(
  private val slackService: SlackService,
  private val gitDataGenerator: GitDataGenerator,
  @Value("\${spinnaker.baseUrl}") private val spinnakerBaseUrl: String,
) : SlackNotificationHandler<SlackVerificationCompletedNotification> {

  override val types = listOf(NotificationType.TEST_FAILED, NotificationType.TEST_PASSED)
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(notification: SlackVerificationCompletedNotification, channel: String) {
    with(notification) {
      log.debug("Sending verification completed notifiaction with $status for application ${notification.application}")


      val imageUrl = when (status) {
        FAIL -> "https://raw.githubusercontent.com/gcomstock/managed.delivery/master/src/icons/test_fail.png"
        PASS -> "https://raw.githubusercontent.com/gcomstock/managed.delivery/master/src/icons/test_pass.png"
        else -> null
      }

      val headerText = when (status) {
        FAIL -> "Test failed"
        PASS -> "Test passed"
        //this is a default text. We shouldn't get here as we checked prior that status is either fail/pass.
        else -> "Test verification completed"
      }

      val artifactUrl = "$spinnakerBaseUrl/#/applications/${application}/environments/${artifact.reference}/${artifact.version}"
      val blocks = withBlocks {
        header {
          text(headerText, emoji = true)
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
      slackService.sendSlackNotification(channel, blocks, application = application, type = types, fallbackText = headerText)
    }
  }
}
