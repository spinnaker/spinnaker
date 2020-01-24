package com.netflix.spinnaker.keel.persistence

interface AgentLockRepository {

  fun tryAcquireLock(agentName: String, lockTimeoutSeconds: Long): Boolean
}
