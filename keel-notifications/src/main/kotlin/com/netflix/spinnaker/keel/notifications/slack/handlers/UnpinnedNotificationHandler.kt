package com.netflix.spinnaker.keel.notifications.slack.handlers

import com.netflix.spinnaker.config.BaseUrlConfig
import com.netflix.spinnaker.keel.api.NotificationDisplay
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.notifications.slack.SlackService
import com.netflix.spinnaker.keel.notifications.slack.SlackUnpinnedNotification
import com.slack.api.model.block.LayoutBlock
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.apache.logging.log4j.util.Strings
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Sends notification when unpinning an artifact
 */
@Component
class UnpinnedNotificationHandler(
  private val slackService: SlackService,
  private val gitDataGenerator: GitDataGenerator
) : SlackNotificationHandler<SlackUnpinnedNotification> {

  override val supportedTypes = listOf(NotificationType.ARTIFACT_UNPINNED)
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private fun SlackUnpinnedNotification.headerText(): String {
    val env = Strings.toRootUpperCase(targetEnvironment)
    return ":large_blue_circle: :pin: $application ${pinnedArtifact?.buildNumber ?: pinnedArtifact?.version ?: originalPin.version} pin removed from $env"
  }

  private fun SlackUnpinnedNotification.compactMessage(unpinner: String): List<LayoutBlock> =
    withBlocks {
      val env = Strings.toRootUpperCase(targetEnvironment)
      val previouslyPinned = pinnedArtifact
        ?.let {
          val link = gitDataGenerator.generateArtifactUrl(application, originalPin.artifact.reference, it.version)
          "<$link|#${it.buildNumber ?: it.version}>"
        } ?: originalPin.version

      val header = ":large_blue_circle: :pin: *${gitDataGenerator.linkedApp(application)} pin of build $previouslyPinned removed from $env*"

      var text = "$unpinner unpinned $env"
      if (latestApprovedArtifactVersion != null) {
        val link = gitDataGenerator.generateArtifactUrl(application, originalPin.artifact.reference, latestApprovedArtifactVersion.version)
        text += ", <$link|#${latestApprovedArtifactVersion.buildNumber ?: latestApprovedArtifactVersion.version}> will start deploying shortly."
      }

      section {
        markdownText(header + "\n" + text)
      }
    }

  private fun SlackUnpinnedNotification.normalMessage(unpinner: String): List<LayoutBlock> {
    val env = Strings.toRootUpperCase(targetEnvironment)
    val buildNumberText = when (pinnedArtifact?.buildNumber) {
      null -> ""
      else -> " from #${pinnedArtifact.buildNumber}"
    }
    val headerText = "$env was unpinned${buildNumberText}"
    val imageUrl = "https://raw.githubusercontent.com/spinnaker/spinnaker.github.io/master/assets/images/md_icons/unpinned.png"
    return withBlocks {
      header {
        text(headerText, emoji = true)
      }

      section {
        if (pinnedArtifact != null) {
          gitDataGenerator.generateUnpinCommitInfo(this,
            application,
            imageUrl,
            pinnedArtifact,
            "unpinned",
            env)
        }
      }
      val gitMetadata = pinnedArtifact?.gitMetadata
      if (gitMetadata != null) {
        gitDataGenerator.conditionallyAddFullCommitMsgButton(this, gitMetadata)
        section {
          if (pinnedArtifact != null) {
            gitDataGenerator.generateScmInfo(this, application, gitMetadata, pinnedArtifact)
          }
        }
      }

      context {
        elements {
          markdownText("$unpinner unpinned on <!date^${time.epochSecond}^{date_num} {time_secs}|fallback-text-include-PST>")
        }
      }
    }
  }

  override fun sendMessage(
    notification: SlackUnpinnedNotification,
    channel: String,
    notificationDisplay: NotificationDisplay
  ) {
    with(notification) {
      log.debug("Sending unpinned artifact notification for application $application")
      val unpinner = slackService.getUsernameByEmail(user)
      val pinner: String = if (originalPin.pinnedBy != null ) {
        slackService.getUsernameByEmail(originalPin.pinnedBy!!)
      } else originalPin.pinnedBy!!

      val uniqueBlocks = when(notificationDisplay) {
        NotificationDisplay.NORMAL -> notification.normalMessage(unpinner)
        NotificationDisplay.COMPACT -> notification.compactMessage(unpinner)
      }

      val commonBlocks = withBlocks {
        context {
          elements {
            markdownText("$pinner originally pinned on " +
              "<!date^${originalPin.pinnedAt!!.epochSecond}^{date_num} {time_secs}|fallback-text-include-PST>" +
              ": \"${originalPin.comment}\"")
          }
        }
      }
      slackService.sendSlackNotification(
        channel,
        uniqueBlocks + commonBlocks,
        application = application,
        type = supportedTypes,
        fallbackText = headerText()
      )
    }
  }
}
