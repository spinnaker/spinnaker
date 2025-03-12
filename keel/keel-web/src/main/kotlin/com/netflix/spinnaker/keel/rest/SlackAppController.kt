package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.notifications.slack.callbacks.CommitModalCallbackHandler
import com.netflix.spinnaker.keel.notifications.slack.callbacks.ManualJudgmentCallbackHandler
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload
import com.slack.api.bolt.App
import com.slack.api.bolt.context.builtin.ActionContext
import com.slack.api.bolt.request.builtin.BlockActionRequest
import com.slack.api.bolt.servlet.SlackAppServlet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import javax.servlet.annotation.WebServlet


@Component
@WebServlet("/slack/notifications/callbacks")
/**
 * New endpoint for the new slack integration. This will be called from gate directly instead of echo.
 * We use Slack Bolt library (https://github.com/slackapi/java-slack-sdk/), which has a native support for handling callbacks from slack.
 */
class SlackAppController(
  slackApp: App,
  private val mjHandler: ManualJudgmentCallbackHandler,
  private val commitModalCallbackHandler: CommitModalCallbackHandler,
) : SlackAppServlet(slackApp) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  init {
    // The pattern here should match the action id field in the actual button.
    // for example, for manual judgment notifications: constraintId:OVERRIDE_PASS:MANUAL_JUDGMENT
    val actionIdPattern = "^(\\w+):(\\w+):(\\w+)".toPattern()
    slackApp.blockAction(actionIdPattern) { req: BlockActionRequest, ctx: ActionContext ->
      if (req.payload.notificationType == "MANUAL_JUDGMENT") {
        log.info(logMessage("'manual judgment' button clicked", req))
        mjHandler.respondToButton(req, ctx)
      } else if (req.payload.notificationType == "FULL_COMMIT_MODAL") {
        log.info(logMessage("'show full commit' button clicked", req))
        commitModalCallbackHandler.openModal(req, ctx)
      } else if (req.payload.actions.first().actionId == "button:url:mj-diff-link") {
        log.info(logMessage("'see changes' button clicked", req))
      } else {
        log.debug(logMessage("Unrecognized action", req))
      }
      // we always need to acknowledge the button within 3 seconds
      ctx.ack()
    }
  }

  fun logMessage(what: String, req: BlockActionRequest) =
    "[slack interaction] $what by ${req.payload.user.username} (${req.payload.user.id}) " +
      "in channel ${req.payload.channel.name} (${req.payload.channel.id})"

  //action id is consistent of 3 parts, where the last part is the type
  val BlockActionPayload.notificationType
    get() = actions.first().actionId.split(":").last()
}
