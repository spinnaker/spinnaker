package com.netflix.spinnaker.keel.scheduled

interface ScheduledAgent {

  val lockTimeoutSeconds: Long

  suspend fun invokeAgent()
}
