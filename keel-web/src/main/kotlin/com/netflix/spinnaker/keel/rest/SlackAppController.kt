package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.events.ButtonClickedEvent
import com.netflix.spinnaker.keel.notifications.slack.callbacks.CommitModalCallbackHandler
import com.netflix.spinnaker.keel.notifications.slack.callbacks.ManualJudgmentCallbackHandler
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload
import com.slack.api.bolt.App
import com.slack.api.bolt.context.builtin.ActionContext
import com.slack.api.bolt.request.builtin.BlockActionRequest
import com.slack.api.bolt.servlet.SlackAppServlet
import org.springframework.context.ApplicationEventPublisher
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
  private val publisher: ApplicationEventPublisher
) : SlackAppServlet(slackApp) {
  init {
    // The pattern here should match the action id field in the actual button.
    // for example, for manual judgment notifications: constraintId:OVERRIDE_PASS:MANUAL_JUDGMENT
    val actionIdPattern = "^(\\w+):(\\w+):(\\w+)".toPattern()
    slackApp.blockAction(actionIdPattern) { req: BlockActionRequest, ctx: ActionContext ->
      if (req.payload.notificationType == "MANUAL_JUDGMENT") {
        mjHandler.respondToButton(req, ctx)
      } else if (req.payload.notificationType == "FULL_COMMIT_MODAL") {
        commitModalCallbackHandler.openModal(req, ctx)
      } else if (req.payload.actions.first().actionId == "button:url:mj-diff-link") {
        publisher.publishEvent(ButtonClickedEvent(req.payload.actions.first().actionId))
      }
      // we always need to acknowledge the button within 3 seconds
      ctx.ack()
    }
  }

  //action id is consistent of 3 parts, where the last part is the type
  val BlockActionPayload.notificationType
    get() = actions.first().actionId.split(":").last()
}
