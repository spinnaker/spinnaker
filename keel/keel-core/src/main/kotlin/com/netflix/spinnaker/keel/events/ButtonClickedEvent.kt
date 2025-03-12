package com.netflix.spinnaker.keel.events

/**
 * Event sent when a slack button is clicked, with identifying info about the button
 *
 * This is to track the clicks for various slack buttons
 */
data class ButtonClickedEvent(
  val buttonId: String
)
