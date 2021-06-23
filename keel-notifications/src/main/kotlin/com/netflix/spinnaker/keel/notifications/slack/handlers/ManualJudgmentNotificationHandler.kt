package com.netflix.spinnaker.keel.notifications.slack.handlers

import com.netflix.spinnaker.config.BaseUrlConfig
import com.netflix.spinnaker.keel.api.NotificationDisplay
import com.netflix.spinnaker.keel.api.NotificationDisplay.*
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.artifacts.ArtifactVersionLinks
import com.netflix.spinnaker.keel.constraints.ManualJudgementConstraintAttributes
import com.netflix.spinnaker.keel.constraints.OriginalSlackMessageDetail
import com.netflix.spinnaker.keel.notifications.NotificationType.MANUAL_JUDGMENT_AWAIT
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.notifications.slack.SlackManualJudgmentNotification
import com.netflix.spinnaker.keel.notifications.slack.SlackService
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

  private fun SlackManualJudgmentNotification.headerText(): String {
    return "Awaiting manual judgement"
  }

  private fun SlackManualJudgmentNotification.compactMessage(numToBePromoted: Int, author: String?): List<LayoutBlock> {
    return constructCompactMessageWithoutButtons(
      targetEnvironment,
      application,
      artifactCandidate,
      pinnedArtifact,
      headerText(),
      author,
      gitDataGenerator,
      numToBePromoted
    )
  }

  private fun SlackManualJudgmentNotification.normalMessage(numToBePromoted: Int): List<LayoutBlock> {
    val headerText = "Awaiting manual judgement"
    val imageUrl = "https://raw.githubusercontent.com/spinnaker/spinnaker.github.io/master/assets/images/md_icons/mj_needed.png"

    return constructNormalMessageWithoutButtons(
      targetEnvironment,
      application,
      artifactCandidate,
      pinnedArtifact,
      headerText,
      imageUrl,
      "mj_needed",
      gitDataGenerator,
      numToBePromoted
    )
  }

  override fun sendMessage(
    notification: SlackManualJudgmentNotification,
    channel: String,
    notificationDisplay: NotificationDisplay
  ) {
    log.debug("Sending manual judgment await notification for application ${notification.application}")

    with(notification) {
      val numVersionsToBePromoted = repository.getNumPendingToBePromoted(
        application = application,
        artifactReference = deliveryArtifact.reference,
        environmentName = targetEnvironment,
        version = artifactCandidate.version
      )
      val compareLink = artifactVersionLinks.generateCompareLink(artifactCandidate, currentArtifact, deliveryArtifact)
      val headerText = "Awaiting manual judgement"
      val author = if (artifactCandidate.gitMetadata?.commitInfo != null) {
        artifactCandidate.gitMetadata?.author?.let { slackService.getUsernameByEmailPrefix(it) }
      } else {
        null
      }

      val uniqueBlocks = when(notificationDisplay) {
        NORMAL -> notification.normalMessage(numVersionsToBePromoted)
        COMPACT -> notification.compactMessage(numVersionsToBePromoted, author)
      }

      val actionBlocks = withBlocks {
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
        .sendSlackNotification(
          channel,
          uniqueBlocks + actionBlocks,
          application = application,
          type = supportedTypes,
          fallbackText = headerText
        )

      if (response?.isOk == true) {
        // save some of the response if we have a constraint uid
        // so that we can update the message if clicked from the UI
        storeMessageDetails(response, notification, notificationDisplay)
      }
    }
  }

  companion object {

    /**
     * Builds most of the normal notification in a way that can be re-used across handles
     * so that we can update the notification if a judgement is approved from the api
     */
    fun constructCompactMessageWithoutButtons(
      environment: String,
      application: String,
      artifactCandidate: PublishedArtifact,
      pinnedArtifact: PublishedArtifact?,
      headerText: String,
      author: String?,
      gitDataGenerator: GitDataGenerator,
      numToBePromoted: Int,
      action: String = "awaiting judgement"
    ): List<LayoutBlock> {
      val env = Strings.toRootUpperCase(environment)
      val linkedCandidateVersion = "<${gitDataGenerator.generateArtifactUrl(application, artifactCandidate.reference, artifactCandidate.version)}|#${artifactCandidate.buildNumber ?: artifactCandidate.version}>"
      var text = "${gitDataGenerator.linkedApp(application)} build $linkedCandidateVersion"
      if (author != null) {
        text += " by $author"
      }
      text += " $action in $env"
      if (numToBePromoted > 1) {
        text += "\n(:speaking_head_in_silhouette: _$numToBePromoted ahead of current_)"
      }
      if (pinnedArtifact != null) {
        val pinnedUrl = "<${gitDataGenerator.generateArtifactUrl(application, pinnedArtifact.reference, pinnedArtifact.version)}|#${pinnedArtifact.buildNumber ?: pinnedArtifact.version}>"
        text += "\n :warning: Another version ($pinnedUrl) is pinned here. You will need to unpin before this version can be deployed."
      }
      return withBlocks {
        section {
          markdownText(":gavel: *$headerText*\n$text")
        }

        val gitMetadata = artifactCandidate.gitMetadata
        if (gitMetadata != null) {
          section {
            gitDataGenerator.generateScmInfo(this, application, gitMetadata, artifactCandidate)
          }
        }
      }
    }

    /**
     * Builds most of the normal notification in a way that can be re-used across handles
     * so that we can update the notification if a judgement is approved from the api
     */
    fun constructNormalMessageWithoutButtons(
      environment: String,
      application: String,
      artifactCandidate: PublishedArtifact,
      pinnedArtifact: PublishedArtifact?,
      headerText: String,
      imageUrl: String,
      imageAltText: String,
      gitDataGenerator: GitDataGenerator,
      numToBePromoted: Int
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
        if (numToBePromoted > 1) {
          // add warning if this will promote more than one artifact
          section {
            markdownText(":speaking_head_in_silhouette: _$numToBePromoted versions ahead of current_")
          }
        }
      }
    }
  }

  /**
   * Stores the message details in the constraint state repository so that they can be used
   * to update the slack message.
   */
  fun storeMessageDetails(response: ChatPostMessageResponse, notification: SlackManualJudgmentNotification, display: NotificationDisplay) {
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
          display = display
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
