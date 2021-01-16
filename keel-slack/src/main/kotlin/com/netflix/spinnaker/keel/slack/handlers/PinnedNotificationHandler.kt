package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.keel.api.ScmInfo
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.artifacts.getScmBaseLink
import com.netflix.spinnaker.keel.slack.SlackPinnedNotification
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.slack.SlackNotifier
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.apache.logging.log4j.util.Strings
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class PinnedNotificationHandler (
  private val slackNotifier: SlackNotifier,
  private val scmInfo: ScmInfo,
  @Value("\${spinnaker.baseUrl}") private val spinnakerBaseUrl: String,
): SlackNotificationHandler<SlackPinnedNotification> {

  override val type: NotificationType = NotificationType.PINNED_ARTIFACT
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(notification: SlackPinnedNotification) {
    log.debug("Sending pinnedEnvironment notification for application ${notification.application}")

    with(notification) {
      val env = Strings.toRootUpperCase(pin.targetEnvironment)
      val pinnedArtifactUrl = "$spinnakerBaseUrl/#/applications/${application}/environments/${pinnedArtifact?.reference}/${pinnedArtifact?.version}"
      val repoLink = generateRepoLink(currentArtifact?.gitMetadata)
      var details = ""
      val blocks = withBlocks {
        header {
          text("$env is pinned", emoji = true)
        }

        section {
          markdownText("*Version:* ~#${notification.currentArtifact?.buildMetadata?.number}~ → <$pinnedArtifactUrl|#${pinnedArtifact?.buildMetadata?.number}> " +
            "by ${pinnedArtifact?.gitMetadata?.author}\n " +
            "*Where:* $env\n\n " +
            "${pinnedArtifact?.gitMetadata?.commitInfo?.message}")
          accessory {
            image(imageUrl = "https://raw.githubusercontent.com/gcomstock/managed.delivery/master/src/icons/pinned.png", altText = "pinned")
          }
        }

        section {
          with(notification.currentArtifact?.gitMetadata) {
            details +="<$repoLink|${this?.project}/" +
              "${this?.repo?.name}> › " +
              "<$repoLink/branches|${this?.branch}> › "

            if (Strings.isNotEmpty(this?.pullRequest?.number)) {
              details += "<${this?.pullRequest?.url}|PR#${this?.pullRequest?.number}> ›"
            }
            markdownText(details +
              "<${this?.commitInfo?.link}|${this?.commitInfo?.sha?.substring(0,7)}>")
          }
          accessory {
            button {
              text("More...")
                //TODO: figure out which action id to send here
              actionId("button-action")
              url(pinnedArtifactUrl)
            }
          }
        }
        context {
          elements {
            markdownText("${pin.pinnedBy} pinned on ${time}: \"${pin.comment}\"")
          }
        }

      }
      slackNotifier.sendSlackNotification(notification.channel, blocks)
    }
  }

  private fun generateRepoLink(gitMetadata: GitMetadata?): String {
    val baseScmUrl = gitMetadata?.commitInfo?.link?.let { getScmBaseLink(scmInfo, it) }
    return "$baseScmUrl/projects/${gitMetadata?.project}/repos/${gitMetadata?.repo?.name}"
  }


}
