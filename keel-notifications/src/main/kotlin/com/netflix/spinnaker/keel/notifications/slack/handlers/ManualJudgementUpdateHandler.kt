package com.netflix.spinnaker.keel.notifications.slack.handlers

import com.netflix.spinnaker.keel.api.NotificationDisplay
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.artifacts.ArtifactVersionLinks
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
  private val gitDataGenerator: GitDataGenerator,
  private val artifactVersionLinks: ArtifactVersionLinks
) : SlackNotificationHandler<SlackManualJudgmentUpdateNotification> {
  override val supportedTypes = listOf(MANUAL_JUDGMENT_UPDATE)
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(
    notification: SlackManualJudgmentUpdateNotification,
    channel: String,
    notificationDisplay: NotificationDisplay
  ) {
    log.debug("Updating manual judgment await notification for application ${notification.application} sent at ${notification.timestamp}")

    with(notification) {
      require(status.complete) { "Manual judgment not in complete state (${status.name}) for application ${notification.application} sent at ${notification.timestamp}" }

      val verb = when {
        status.passes() -> "approved"
        else -> "rejected"
      }

      //don't calculate compareLink if the user reject the version
      val compareLink = if (verb == "approved") {
        artifactVersionLinks.generateCompareLink(artifactCandidate, currentArtifact, deliveryArtifact)
      } else null

      //flag if there's more than a single artifact (not including preview env), so we would notify the user which artifact it is
      val moreThanOneArtifact = config.artifacts.size != 1 &&
        config.environments.map {
          environment ->
          deliveryArtifact.isUsedIn(environment) && !environment.isPreview
        }.size > 1

      val baseBlocks = ManualJudgmentNotificationHandler.constructMessageWithoutButtons(
        targetEnvironment,
        application,
        artifactCandidate,
        pinnedArtifact,
        gitDataGenerator,
        0, // clear the num ahead text on resend
        moreThanOneArtifact,
        verb
      )

      val newFooterBlock = withBlocks {
        context {
          elements {
            markdownText(judgedContext(user, status, compareLink))
          }
        }
      }

      val newBlocks = baseBlocks + newFooterBlock

      val fallbackText = "[$application] manual judgement $verb in ${targetEnvironment.toLowerCase()}"
      slackService.updateSlackMessage(notification.channel, timestamp, newBlocks, fallbackText, application)
    }
  }

  fun judgedContext(user: String?, status: ConstraintStatus, compareLink: String?): String {
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

    var text = "$emoji $handle hit $action on <!date^${clock.instant().epochSecond}^{date_num} {time_secs}|fallback-text-include-PST>"

    if (compareLink != null) {
      text += ". <$compareLink|_See deployed code changes_>"
    }

    return text
  }
}
