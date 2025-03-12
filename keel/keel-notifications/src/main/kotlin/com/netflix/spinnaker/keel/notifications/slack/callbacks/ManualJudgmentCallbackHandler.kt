package com.netflix.spinnaker.keel.notifications.slack.callbacks

import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.auth.PermissionLevel.WRITE
import com.netflix.spinnaker.keel.auth.AuthorizationSupport
import com.netflix.spinnaker.keel.auth.AuthorizationResourceType.APPLICATION
import com.netflix.spinnaker.keel.auth.AuthorizationResourceType.SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.core.api.parseUID
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.notifications.slack.SlackService
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload
import com.slack.api.app_backend.interactive_components.response.ActionResponse
import com.slack.api.bolt.context.builtin.ActionContext
import com.slack.api.bolt.request.builtin.BlockActionRequest
import com.slack.api.model.block.LayoutBlock
import com.slack.api.model.block.SectionBlock
import com.slack.api.model.block.composition.MarkdownTextObject
import com.slack.api.model.block.element.ButtonElement
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
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
  private val slackService: SlackService,
  private val authorizationSupport: AuthorizationSupport,
  private val springEnv: Environment
) {

  private val authorizeManualJudgement: Boolean
    get() = springEnv.getProperty("slack.authorize-manual-judgement", Boolean::class.java, true)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  fun respondToButton(req: BlockActionRequest, ctx: ActionContext) {
    if (req.payload.responseUrl != null) {
      val constraintUid = req.payload.constraintId
      val currentState = repository.getConstraintStateById(parseUID(constraintUid))
        ?: throw SystemException("constraint@callbackId=$constraintUid", "constraint not found")

      val authResponse = validateAuth(req, ctx, currentState)
      log.debug("Manual judgment auth check for $constraintUid: $authResponse")

      val blocks = mutableListOf<LayoutBlock>()
      when (authResponse.authorized) {
        true -> {
          updateConstraintState(req.payload, currentState)
          blocks.addAll(updateMjAuthorized(req.payload))
        }
        false -> blocks.addAll(updateMjUnauthorized(req.payload, authResponse.errorMessage))
      }

      val response = ActionResponse.builder()
        .blocks(blocks)
        .text(fallbackText(req.payload))
        .replaceOriginal(true)
        .build()
      ctx.respond(response)
    }
  }

  /**
   * @return true if the user is able to hit the button, false otherwise, plus a reason if false
   *
   * Checks to see if slack user has application access and service account access
   */
  fun validateAuth(req: BlockActionRequest, ctx: ActionContext, constraintState: ConstraintState): AuthorizationResponse {
    if (!authorizeManualJudgement) {
      return AuthorizationResponse(authorized = true, errorMessage = null)
    }

    log.debug("Checking authorization for manual judgement action: ${req.toLog()}")
    val email = slackService.getEmailByUserId(ctx.requestUserId)
    if (email == ctx.requestUserId) {
      // unable to find email, not authorized
      return AuthorizationResponse(false, "Unable to find email address for slack user ${ctx.requestUserId}. They cannot approve or reject this artifact version.")
    }

    val config = repository.getDeliveryConfig(constraintState.deliveryConfigName)
    val hasAppPermissions = authorizationSupport.hasPermission(email, config.application, APPLICATION, WRITE)
    val hasServiceAccountPermissions = authorizationSupport.hasPermission(email, config.serviceAccount, SERVICE_ACCOUNT, WRITE)

    val errors = mutableListOf<String>()
    if (!hasAppPermissions) {
      errors.add("doesn't have permission to deploy this app")
    }
    if (!hasServiceAccountPermissions) {
      errors.add("doesn't have permissions to use the service account ${config.serviceAccount}")
    }

    return AuthorizationResponse(
      authorized = hasAppPermissions && hasServiceAccountPermissions,
      errorMessage = if (errors.isNotEmpty()) {
        "User ($email) " + errors.joinToString(" and ") + ". They cannot approve or reject this artifact version."
      } else {
        null
      }
    )
  }

  /**
   * Updating the constraint status based on the user response (either approve/reject)
   */
  fun updateConstraintState(slackCallbackResponse: BlockActionPayload, currentState: ConstraintState) {
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
   * Adds an error context block to the bottom of an existing manual judgement with the auth error,
   * or replaces that block with the current auth error.
   */
  fun updateMjUnauthorized(response: BlockActionPayload, errorMessage: String?): List<LayoutBlock> {
    val message = errorMessage ?: "User ${response.user} isn't authorized to approve this deployment."

    val originalBlocks = response.message.blocks
    if (originalBlocks.last().type == "context") {
      // this means there's already an error context message, which we need to remove
      originalBlocks.removeLastOrNull()
    }

    return originalBlocks + withBlocks {
      context {
        elements {
          markdownText(message)
        }
      }
    }
  }

  /**
   * Update an existing manual judgment notification with the user and the action that was performed.
   * For example, if user gyardeni approved the notification, this function will add:
   * "@Gal Yardeni hit approve on 2021-02-12 11:05:57 AM" and will marked the original text with strikethrough.
   */
  fun updateMjAuthorized(response: BlockActionPayload): List<LayoutBlock> {
    try {
      val originalCommitText = response.message.blocks[1].getText
      val action = actionsMap[response.actions.first().value]

      val updatedBlocks = withBlocks {
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
      }

      val newFooterBlock = withBlocks {
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
      if (originalBlocks.last().type == "context") {
        // this means there's an error message, which we need to remove since the judgement was completed
        originalBlocks.removeLastOrNull()
      }
      originalBlocks.removeLast() // removes mj buttons
      return updatedBlocks + originalBlocks + newFooterBlock
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

  private fun BlockActionRequest.toLog() =
    "Slack request by ${payload.user?.username} (${payload.user?.id}) in channel ${payload.channel?.name} (${payload.channel?.id})"
}

data class AuthorizationResponse(
  val authorized: Boolean,
  val errorMessage: String?
)
