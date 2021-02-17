package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.slack.callbacks.ManualJudgmentCallbackHandler
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload
import com.slack.api.app_backend.interactive_components.response.ActionResponse
import com.slack.api.bolt.App
import com.slack.api.bolt.servlet.SlackAppServlet
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
  private val mjHandler: ManualJudgmentCallbackHandler
) : SlackAppServlet(slackApp) {
  init {
    // The pattern here should match the action id field in the actual button.
    // for example, for manual judgment notifications: constraintId:OVERRIDE_PASS:MANUAL_JUDGMENT
    val actionIdPattern = "^(\\w+):(\\w+):(\\w+)".toPattern()
    slackApp.blockAction(actionIdPattern) { req, ctx ->
      if (req.payload.notificationType == "MANUAL_JUDGMENT") {
        // If we want to add more handlers, we can parse here the action id type (i.e MANUAL_JUDGMENT) and use the right handler
        if (req.payload.responseUrl != null) {
          mjHandler.updateConstraintState(req.payload)
          val response = ActionResponse.builder()
            .blocks(mjHandler.updateManualJudgementNotification(req.payload))
            .text(mjHandler.fallbackText(req.payload))
            .replaceOriginal(true)
            .build()
          ctx.respond(response)
        }
      }
      //acknowledge the button anyway, so we won't exceptions from slack when clicking on it
      ctx.ack()
    }
  }

  //action id is consistent of 3 parts, where the last part is the type
  val BlockActionPayload.notificationType
    get() = actions.first().actionId.split(":").last()
}
