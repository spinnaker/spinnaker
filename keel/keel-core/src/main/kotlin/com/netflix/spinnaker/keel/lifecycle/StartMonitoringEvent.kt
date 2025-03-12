package com.netflix.spinnaker.keel.lifecycle

data class StartMonitoringEvent(
  val triggeringEventUid: String,
  val triggeringEvent: LifecycleEvent
)
