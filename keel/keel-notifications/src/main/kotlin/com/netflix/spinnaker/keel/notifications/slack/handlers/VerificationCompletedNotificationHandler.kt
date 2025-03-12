package com.netflix.spinnaker.keel.notifications.slack.handlers

import com.netflix.spinnaker.keel.api.NotificationDisplay
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.FAIL
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.notifications.slack.DeploymentStatus
import com.netflix.spinnaker.keel.notifications.slack.SlackService
import com.netflix.spinnaker.keel.notifications.slack.SlackVerificationCompletedNotification
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Sends notification when a verification is completed
 */
@Component
class VerificationCompletedNotificationHandler(
  private val slackService: SlackService,
  private val gitDataGenerator: GitDataGenerator
) : SlackNotificationHandler<SlackVerificationCompletedNotification> {

  override val supportedTypes = listOf(NotificationType.TEST_FAILED, NotificationType.TEST_PASSED)
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(
    notification: SlackVerificationCompletedNotification,
    channel: String,
    notificationDisplay: NotificationDisplay
  ) {
    with(notification) {
      log.debug("Sending verification completed notification with $status for application $application")

      val (emoji, verb)  = when (status) {
        FAIL -> ":x::test_tube:" to "Verification failed"
        PASS -> ":test_tube:" to "Verification passed"
        //this is a default text. We shouldn't get here as we checked prior that status is either fail/pass.
        else -> ":test_tube:" to "Verification completed"
      }

      val blocks = withBlocks {
        gitDataGenerator.notificationBody(this, emoji, application, artifact, verb)

        artifact.gitMetadata?.let { gitMetadata ->
          section {
            gitDataGenerator.generateScmInfo(this, application, gitMetadata, artifact)
          }
        }
      }
      slackService.sendSlackNotification(channel, blocks, application = application, type = supportedTypes, fallbackText = "[$application] $verb in ${targetEnvironment.toLowerCase()} ")
    }
  }
}
