package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.slack.callbacks.ManualJudgmentCallbackHandler
import com.slack.api.app_backend.interactive_components.response.ActionResponse
import com.slack.api.bolt.App
import com.slack.api.bolt.servlet.SlackAppServlet
import org.springframework.stereotype.Component
import javax.servlet.annotation.WebServlet

@Component
@WebServlet("/slack/notifications/callbacks")
/**
 * New endpoint for the new slack integration. This will be called from gate directly instead of echo.
 */
class SlackAppController(
  slackApp: App,
  private val mjHandler: ManualJudgmentCallbackHandler
) : SlackAppServlet(slackApp){
  init {
    //the pattern here should match the action id string in the actual button, for example: constraintId:action:Manual_judgment
    val pattern = "^(\\w+):(\\w+):(\\w+)".toPattern()
    slackApp.blockAction(pattern) { req, ctx ->
      if (req.payload.responseUrl != null) {
        mjHandler.updateConstraintState(req.payload)
        val response = ActionResponse.builder()
          .blocks(mjHandler.updateManualJudgementNotification(req.payload))
          .text(mjHandler.fallbackText(req.payload))
          .replaceOriginal(true)
          .build()
        ctx.respond(response)
      }
      ctx.ack()
    }
  }
}
