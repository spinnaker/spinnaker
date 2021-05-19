package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.keel.api.ScmInfo
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.artifacts.ArtifactVersionLinks
import com.netflix.spinnaker.keel.constraints.ManualJudgementConstraintAttributes
import com.netflix.spinnaker.keel.constraints.OriginalSlackMessageDetail
import com.netflix.spinnaker.keel.notifications.NotificationType.MANUAL_JUDGMENT_AWAIT
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.slack.SlackManualJudgmentNotification
import com.netflix.spinnaker.keel.slack.SlackService
import com.slack.api.methods.response.chat.ChatPostMessageResponse
import com.slack.api.model.block.LayoutBlock
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
  private val repository: KeelRepository,
  private val artifactVersionLinks: ArtifactVersionLinks,
) : SlackNotificationHandler<SlackManualJudgmentNotification> {
  override val supportedTypes = listOf(MANUAL_JUDGMENT_AWAIT)
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(notification: SlackManualJudgmentNotification, channel: String) {
    log.debug("Sending manual judgment await notification for application ${notification.application}")

    val numVersionsToBePromoted = repository.getNumPendingToBePromoted(
      application = notification.application,
      artifactReference = notification.deliveryArtifact.reference,
      environmentName = notification.targetEnvironment,
      version = notification.artifactCandidate.version
    )

    with(notification) {
      val compareLink = artifactVersionLinks.generateCompareLink(artifactCandidate, currentArtifact, deliveryArtifact)
      val headerText = "Awaiting manual judgement"
      val imageUrl = "https://raw.githubusercontent.com/spinnaker/spinnaker.github.io/master/assets/images/md_icons/mj_needed.png"
      val baseBlocks = constructMessageWithoutButtons(
        notification.targetEnvironment,
        notification.application,
        notification.artifactCandidate,
        notification.pinnedArtifact,
        headerText,
        imageUrl,
        "mj_needed",
        gitDataGenerator
      )
      val actionBlocks = withBlocks {
        if (numVersionsToBePromoted > 1) {
          // add info about how many versions will be promoted if there is more than one
          section {
            markdownText(":speaking_head_in_silhouette: _$numVersionsToBePromoted versions ahead of current_")
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
                actionId("button:url:mj-diff-link")
              }
            }
          }
        }
      }
      val response: ChatPostMessageResponse? = slackService
        .sendSlackNotification(channel, baseBlocks + actionBlocks, application = application, type = supportedTypes, fallbackText = headerText)

      if (response?.isOk == true) {
        // save some of the response if we have a constraint uid
        // so that we can update the message if clicked from the UI
        storeMessageDetails(response, notification)
      }
    }
  }

  companion object {
    /**
     * Builds most of the notification in a way that can be re-used across handles
     * so that we can update the notification if a judgement is approved from the api
     */
    fun constructMessageWithoutButtons(
      environment: String,
      application: String,
      artifactCandidate: PublishedArtifact,
      pinnedArtifact: PublishedArtifact?,
      headerText: String,
      imageUrl: String,
      imageAltText: String,
      gitDataGenerator: GitDataGenerator
    ): List<LayoutBlock> {
      val env = Strings.toRootUpperCase(environment)
      return withBlocks {
        header {
          text(headerText, emoji = true)
        }

        section {
          gitDataGenerator.generateCommitInfo(
            this,
            application,
            imageUrl,
            artifactCandidate,
            imageAltText,
            env = env,
          )
        }
        val gitMetadata = artifactCandidate.gitMetadata
        if (gitMetadata != null) {
          gitDataGenerator.conditionallyAddFullCommitMsgButton(this, gitMetadata)
          section {
            gitDataGenerator.generateScmInfo(this, application, gitMetadata, artifactCandidate)
          }
        }

        // Add a warning section in case there's a pinned artifact
        if (pinnedArtifact != null) {
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
      }
    }
  }

  /**
   * Stores the message details in the constraint state repository so that they can be used
   * to update the slack message.
   */
  fun storeMessageDetails(response: ChatPostMessageResponse, notification: SlackManualJudgmentNotification) {
    notification.stateUid?.let { uid ->
      val currentState = repository.getConstraintStateById(uid)
      if (currentState != null) {
        val slackDetails = (currentState.attributes as? ManualJudgementConstraintAttributes)?.slackDetails ?: emptyList()
        val updatedSlackDetails = slackDetails + OriginalSlackMessageDetail(
          timestamp = response.ts,
          channel = response.channel,
          artifactCandidate = notification.artifactCandidate,
          targetEnvironment = currentState.environmentName,
          currentArtifact = notification.currentArtifact,
          deliveryArtifact = notification.deliveryArtifact,
          pinnedArtifact = notification.pinnedArtifact,
        )

        val updatedState = currentState.copy(
          attributes = ManualJudgementConstraintAttributes(
            slackDetails = updatedSlackDetails
          )
        )

        repository.storeConstraintState(updatedState)
      }
    }
  }
}
