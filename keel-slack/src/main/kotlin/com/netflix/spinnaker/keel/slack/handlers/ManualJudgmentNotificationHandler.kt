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
import org.springframework.stereotype.Component

/**
 * Sends notification when manual judgment is awaiting
 */
@Component
class ManualJudgmentNotificationHandler(
  private val slackService: SlackService,
  private val gitDataGenerator: GitDataGenerator,
  private val scmInfo: ScmInfo
) : SlackNotificationHandler<SlackManualJudgmentNotification> {

  override val supportedTypes = listOf(NotificationType.MANUAL_JUDGMENT_AWAIT)
  private val log by lazy { LoggerFactory.getLogger(javaClass) }
  override fun sendMessage(notification: SlackManualJudgmentNotification, channel: String) {
    log.debug("Sending manual judgment await notification for application ${notification.application}")

    with(notification) {
      val compareLink = generateCompareLink(scmInfo, artifactCandidate, currentArtifact, deliveryArtifact)
      val env = Strings.toRootUpperCase(targetEnvironment)
      val headerText = "Awaiting manual judgement"
      val imageUrl = "https://raw.githubusercontent.com/spinnaker/spinnaker.github.io/master/assets/images/md_icons/mj_needed.png"
      val blocks = withBlocks {
        header {
          text(headerText, emoji = true)
        }

        section {
          gitDataGenerator.generateCommitInfo(
            this,
            application,
            imageUrl,
            artifactCandidate,
            "mj_needed",
            env = env)
        }
        val gitMetadata = artifactCandidate.gitMetadata
        if (gitMetadata != null) {
          gitDataGenerator.conditionallyAddFullCommitMsgButton(this, gitMetadata)
          section {
            gitDataGenerator.generateScmInfo(this, application, gitMetadata, artifactCandidate)
          }
        }

        // Add a warning section in case there's a pinned artifact
        if (pinnedArtifact != null){
          section {
            markdownText(":warning: Another version is pinned here. You will need to unpin it first to promote this version.")
            accessory {
              button {
                text("See pinned version", emoji = true)
                actionId("button:url:pinned")
                url(gitDataGenerator.generateArtifactUrl(application, pinnedArtifact.reference, pinnedArtifact.version))
              }
            }
          }
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
                // action id will be consisted by 3 sections with ":" between them to keep it consistent
                actionId("button:url:diff")
              }
            }
          }
        }
      }
      slackService.sendSlackNotification(channel, blocks, application = application, type = supportedTypes, fallbackText = headerText)
    }
  }
}
