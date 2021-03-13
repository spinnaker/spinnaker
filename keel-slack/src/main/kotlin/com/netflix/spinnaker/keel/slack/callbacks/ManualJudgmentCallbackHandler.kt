package com.netflix.spinnaker.keel.slack.callbacks

import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.core.api.parseUID
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.slack.SlackService
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload
import com.slack.api.model.block.LayoutBlock
import com.slack.api.model.block.SectionBlock
import com.slack.api.model.block.composition.MarkdownTextObject
import com.slack.api.model.block.element.ButtonElement
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * This handler will handle slack callbacks coming from Manual Judgment notifications.
 * First, it will update the constraint status based on the user response (either approve/reject)
 * Second, it will construct an updated notification with the action preformed and the user who did it.
 */
@Component
class ManualJudgmentCallbackHandler(
  private val clock: Clock,
  private val repository: KeelRepository,
  private val slackService: SlackService
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  /**
   * Updating the constraint status based on the user response (either approve/reject)
   */
  fun updateConstraintState(slackCallbackResponse: BlockActionPayload) {
    val constraintUid = slackCallbackResponse.constraintId
    val currentState = repository.getConstraintStateById(parseUID(constraintUid))
      ?: throw SystemException("constraint@callbackId=$constraintUid", "constraint not found")

    val user = slackService.getEmailByUserId(slackCallbackResponse.user.id)
    val actionStatus = slackCallbackResponse.actions.first().value

    log.debug(
      "Updating constraint status based on notification interaction: " +
        "user = $user, status = $actionStatus}"
    )

    repository
      .storeConstraintState(
        currentState.copy(
          status = ConstraintStatus.valueOf(actionStatus),
          judgedAt = Instant.now(),
          judgedBy = user
        )
      )
  }

  /**
   * Update an existing manual judgment notification with the user and the action that was performed.
   * For example, if user gyardeni approved the notification, this function will add:
   * "@Gal Yardeni hit approve on 2021-02-12 11:05:57 AM" and will marked the original text with strikethrough.
   */
  fun updateManualJudgementNotification(response: BlockActionPayload): List<LayoutBlock> {
    try {
      val originalCommitText = response.message.blocks[1].getText
      val action = actionsMap[response.actions.first().value]

      val newBlocks = withBlocks {
        header {
          when (action) {
            "approve" -> text("Manual judgement approved", emoji = true)
            else -> text("Manual judgement rejected", emoji = true)
          }
        }
        section {
          markdownText(originalCommitText)
          accessory {
            when (action) {
              "approve" -> image("https://raw.githubusercontent.com/spinnaker/spinnaker.github.io/master/assets/images/md_icons/mj_was_approved.png", altText = "mj_approved")
              else -> image("https://raw.githubusercontent.com/spinnaker/spinnaker.github.io/master/assets/images/md_icons/mj_was_rejected.png", altText = "mj_rejected")
            }
          }
        }

        context {
          elements {
            markdownText(fallbackText(response))
          }
        }
      }

      val originalBlocks = response.message.blocks
      //remove the first two blocks because we're replacing them
      originalBlocks.removeFirstOrNull()
      originalBlocks.removeFirstOrNull()
      return newBlocks + originalBlocks
    } catch (ex: Exception) {
      log.debug("exception occurred while creating updated MJ notification. Will use a fallback text instead: {}", ex)
      return emptyList()
    }
  }

  fun fallbackText(payload: BlockActionPayload): String {
    val action = actionsMap[payload.actions.first().value]
    val emoji = if (action == "approve") {
      ":white_check_mark:"
    } else {
      ":x:"
    }
     return "@${payload.user.name} hit " +
      "$emoji $action on <!date^${clock.instant().epochSecond}^{date_num} {time_secs}|fallback-text-include-PST>"
  }

  val BlockActionPayload.constraintId
    get() = actions.first().actionId.split(":").first()

  val LayoutBlock.getText: String
    get() = ((this as SectionBlock).text as MarkdownTextObject).text

  val LayoutBlock.getUrl: String
    get() = ((this as SectionBlock).accessory as ButtonElement).url

  val actionsMap: Map<String, String> =
    mapOf(
      ConstraintStatus.OVERRIDE_PASS.name to "approve",
      ConstraintStatus.OVERRIDE_FAIL.name to "reject")
}
