package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.keel.api.ScmInfo
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.artifacts.generateCompareLink
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.slack.SlackManualJudgmentNotification
import com.netflix.spinnaker.keel.slack.SlackService
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.apache.logging.log4j.util.Strings
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ManualJudgmentNotificationHandler(
  private val slackService: SlackService,
  private val gitDataGenerator: GitDataGenerator,
  private val scmInfo: ScmInfo,
  @Value("\${spinnaker.baseUrl}") private val spinnakerBaseUrl: String,
) : SlackNotificationHandler<SlackManualJudgmentNotification> {

  override val types = listOf(NotificationType.MANUAL_JUDGMENT_AWAIT)
  private val log by lazy { LoggerFactory.getLogger(javaClass) }
  override fun sendMessage(notification: SlackManualJudgmentNotification, channel: String) {
    log.debug("Sending manual judgment notification for application ${notification.application}")

    with(notification) {
      val compareLink = generateCompareLink(scmInfo, artifactCandidate, currentArtifact, deliveryArtifact)
      val env = Strings.toRootUpperCase(targetEnvironment)
      val headerText = "Awaiting manual judgement"
      val blocks = withBlocks {
        header {
          text(headerText, emoji = true)
        }

        section {
          with(artifactCandidate) {
            val artifactUrl = "$spinnakerBaseUrl/#/applications/${application}/environments/${reference}/${version}"

            if (buildMetadata != null && gitMetadata != null && gitMetadata!!.commitInfo != null) {
              markdownText("*Version:* <$artifactUrl|#${buildMetadata?.number}> " +
                "by @${gitMetadata?.author}\n " +
                "*Where:* $env\n\n " +
                "${gitMetadata?.commitInfo?.message}")
              accessory {
                image(imageUrl = "https://raw.githubusercontent.com/gcomstock/managed.delivery/master/src/icons/mj_needed.png", altText = "mj_needed")
              }
            }
          }
        }

        section {
          gitDataGenerator.generateData(this, application, artifactCandidate)
        }

        actions {
          elements {
            button {
              text("Approve", emoji = true)
              style("primary")
              value(ConstraintStatus.OVERRIDE_PASS.name)
              actionId("${stateUid.toString()}:${ConstraintStatus.OVERRIDE_PASS.name}:MANUAL_JUDGMENT")
              confirm {
                confirm("Do it!")
                deny("Stop, I've changed my mind!")
                title("Are you sure?")
                markdownText("Are you sure you want to promote version ${artifactCandidate.version}?")
              }
            }
            button {
              text("Reject", emoji = true)
              style("danger")
              value(ConstraintStatus.OVERRIDE_FAIL.name)
              actionId("${stateUid.toString()}:${ConstraintStatus.OVERRIDE_FAIL.name}:MANUAL_JUDGMENT")
              confirm {
                markdownText("Are you sure you want to reject version ${artifactCandidate.version}?")
                confirm("Do it!")
                deny("Stop, I've changed my mind!")
                title("Are you sure?")
              }
            }
            if (compareLink != null) {
              button {
                text("See changes", emoji = true)
                url(compareLink)
                actionId("button-action")
              }
            }
          }
        }
      }
      slackService.sendSlackNotification(channel, blocks, application = application, type = types, fallbackText = headerText)
    }
  }
}
