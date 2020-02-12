package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.persistence.AgentLockRepository
import com.netflix.spinnaker.keel.scheduled.ScheduledAgent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class InMemoryAgentLockRepository(override val agents: List<ScheduledAgent>) : AgentLockRepository {
  override fun getLockedAgents(): List<String> {
    return emptyList()
  }

  private val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  override fun tryAcquireLock(agentName: String, lockTimeoutSeconds: Long): Boolean {
    log.info("No locking implementation for in memory repository")
    return true
  }
}
