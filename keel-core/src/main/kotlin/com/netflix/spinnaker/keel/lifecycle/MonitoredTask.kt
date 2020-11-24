package com.netflix.spinnaker.keel.lifecycle

/**
 * Represents something that will be monitored.
 * For example, an orca task or a jenkins job.
 * Notes:
 *   This does not have to be an orca task.
 *   The "link" needs to be understandable by the monitor that will
 *     check it, it doesn't have to be a fully formed link.
 *
 * This 'task' is kicked off by a lifecycle event.
 * Events are emitted as the task is monitored based on that
 * initial event
 */
data class MonitoredTask(
  val triggeringEvent: LifecycleEvent,
  val link: String,
  val type: LifecycleEventType = triggeringEvent.type,
  val numFailures: Int = 0
)
