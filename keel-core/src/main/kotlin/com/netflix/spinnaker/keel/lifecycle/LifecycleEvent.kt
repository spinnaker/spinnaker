package com.netflix.spinnaker.keel.lifecycle

import java.time.Instant

/**
 * A lifecycle event is emitted for an artifact and version.
 * Lifecycle events are meant to capture things that we want to surface
 * to a user. This surfacing is done through a [LifecycleStep].
 *
 * Events are read in cronological order to determine the current status of
 * the overall step, and the current status is presented as a [LifecycleStep].
 *
 * [id] groups events for the same [artifactRef] and [artifactVersion].
 * [scope] and [type] control where the event is shown-
 *    right now there is only one of each.
 * [link] stores a link that the monitor for the [type] of event will use to
 *   monitor the status. It can be a fully qualified link or an id, it doesn't
 *   matter, as long as the monitor knows how to query for status.
 * [text] is the text for the even that is shown to the user. This can be updated
 *   in subsequent events, and only the latest will show when surfaced to
 *   the user.
 * [timestamp] is nullable so that the repository can insert the timestamp when
 *   the event is stored.
 * [startMonitoring] true if this event is the first in the chain and needs to be monitored.
 *    Follow up events should leave it set to false.
 * [data] contains any extra data needed for monitoring the event
 */
data class LifecycleEvent(
  val scope: LifecycleEventScope,
  val artifactRef: String,
  val artifactVersion: String,
  val type: LifecycleEventType,
  val id: String,
  val status: LifecycleEventStatus,
  val text: String? = null,
  val link: String? = null,
  val timestamp: Instant? = null,
  val startMonitoring: Boolean = false,
  val data: Map<String, Any?> = emptyMap()
) {
  fun toStep(): LifecycleStep =
    LifecycleStep(
      scope = scope,
      type = type,
      id = id,
      status = status,
      text = text,
      link = link,
      startedAt = timestamp
      // if we're using this, it's the first event we have,
      // so the timestamp will be the start time
    )
}
