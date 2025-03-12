package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.scheduled.ScheduledAgent

interface AgentLockRepository {

  val agents: List<ScheduledAgent>
  fun tryAcquireLock(agentName: String, lockTimeoutSeconds: Long): Boolean
  fun getLockedAgents(): List<String>
}
