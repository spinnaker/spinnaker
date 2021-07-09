package com.netflix.spinnaker.keel.notifications.slack.handlers

import com.netflix.spinnaker.keel.api.NotificationDisplay
import com.netflix.spinnaker.keel.api.NotificationDisplay.*
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.notifications.NotificationType.MANUAL_JUDGMENT_UPDATE
import com.netflix.spinnaker.keel.notifications.slack.SlackManualJudgmentUpdateNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackService
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * Updates manual judgement notifications that were sent when they
 * were judged from the api
 */
@Component
class ManualJudgementUpdateHandler(
  private val slackService: SlackService,
  private val clock: Clock,
  private val gitDataGenerator: GitDataGenerator
): SlackNotificationHandler<SlackManualJudgmentUpdateNotification> {
  override val supportedTypes = listOf(MANUAL_JUDGMENT_UPDATE)
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(
    notification: SlackManualJudgmentUpdateNotification,
    channel: String,
    notificationDisplay: NotificationDisplay
  ) {
    log.debug("Updating manual judgment await notification for application ${notification.application} sent at ${notification.timestamp}")

    with(notification) {
      require(status.complete) { "Manual judgment not in complete state (${status.name}) for application ${notification.application} sent at ${notification.timestamp}"}

      val verb = when {
        status.passes() -> "approved"
        else -> "rejected"
      }

      val baseBlocks = ManualJudgmentNotificationHandler.constructMessageWithoutButtons(
        targetEnvironment,
        application,
        artifactCandidate,
        pinnedArtifact,
        gitDataGenerator,
        0, // clear the num ahead text on resend
        verb
      )

      val backuptext = fallbackText(user, status)

      val newFooterBlock = withBlocks {
        context {
          elements {
            markdownText(backuptext)
          }
        }
      }

      val newBlocks = baseBlocks + newFooterBlock

      slackService.updateSlackMessage(notification.channel, timestamp, newBlocks, backuptext, application)
    }
  }

  fun fallbackText(user: String?, status: ConstraintStatus): String {
    val handle = user?.let { slackService.getUsernameByEmail(user) }
    val emoji = if (status.passes()) {
      ":white_check_mark:"
    } else {
      ":x:"
    }
    val action = if (status.passes()) {
      "approve"
    } else {
      "reject"
    }
    return "$emoji $handle hit " +
      "$action on <!date^${clock.instant().epochSecond}^{date_num} {time_secs}|fallback-text-include-PST>"
  }
}
