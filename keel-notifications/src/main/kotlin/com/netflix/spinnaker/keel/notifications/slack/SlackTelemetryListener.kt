package com.netflix.spinnaker.keel.notifications.slack

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.events.ButtonClickedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Listens for slack telemetry events, emits metrics based on the events
 */
@Component
class SlackTelemetryListener(
  val spectator: Registry
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  companion object {
    private const val SLACK_STASH_LINK_CLICKED = "keel.slack.stash.diff.clicked"
  }

  @EventListener(ButtonClickedEvent::class)
  fun onButtonClickedEvent(event: ButtonClickedEvent){
    spectator.counter(
      SLACK_STASH_LINK_CLICKED
    )
      .runCatching { increment() }
      .onFailure {
        log.error("Exception incrementing {} counter: {}", SLACK_STASH_LINK_CLICKED, it.message)
      }
  }
}
