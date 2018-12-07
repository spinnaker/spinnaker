/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.cats.sql.cluster

import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentExecution
import com.netflix.spinnaker.cats.agent.AgentLock
import com.netflix.spinnaker.cats.agent.AgentScheduler
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation
import com.netflix.spinnaker.cats.module.CatsModuleAware
import com.netflix.spinnaker.cats.redis.cluster.AgentIntervalProvider
import com.netflix.spinnaker.cats.redis.cluster.NodeIdentity
import com.netflix.spinnaker.cats.redis.cluster.NodeStatusProvider
import com.netflix.spinnaker.cats.thread.NamedThreadFactory
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import java.sql.SQLException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class SqlClusteredAgentScheduler(
  private val jooq: DSLContext,
  private val nodeIdentity: NodeIdentity,
  private val intervalProvider: AgentIntervalProvider,
  private val nodeStatusProvider: NodeStatusProvider,
  private val dynamicConfigService: DynamicConfigService,
  enabledAgentPattern: String,
  agentLockAcquisitionIntervalSeconds: Long? = null,
  private val agentExecutionPool: ExecutorService = Executors.newCachedThreadPool(
    NamedThreadFactory(AgentExecutionAction::class.java.simpleName)
  ),
  lockPollingScheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
    NamedThreadFactory(SqlClusteredAgentScheduler::class.java.simpleName)
  )
) : CatsModuleAware(), AgentScheduler<AgentLock>, Runnable {

  private val log = LoggerFactory.getLogger(javaClass)

  private val agents: MutableMap<String, AgentExecutionAction> = mutableMapOf()
  private val activeAgents: MutableMap<String, NextAttempt> = mutableMapOf()
  private val enabledAgents: Pattern

  init {
    val lockInterval = agentLockAcquisitionIntervalSeconds ?: 1L
    lockPollingScheduler.scheduleAtFixedRate(this, 0, lockInterval, TimeUnit.SECONDS)

    enabledAgents = Pattern.compile(enabledAgentPattern)
  }

  override fun schedule(agent: Agent,
                        agentExecution: AgentExecution,
                        executionInstrumentation: ExecutionInstrumentation) {
    if (agent is AgentSchedulerAware) {
      agent.agentScheduler = this
    }
    agents[agent.agentType] = AgentExecutionAction(agent, agentExecution, executionInstrumentation)
  }

  override fun unschedule(agent: Agent) {
    releaseLock(agent.agentType, 0) // Release the lock immediately
    agents.remove(agent.agentType)
  }

  override fun run() {
    if (nodeStatusProvider.isNodeEnabled) {
      try {
        runAgents()
      } catch (t: Throwable) {
        log.error("Failed running cache agents", t)
      }
    }
  }

  private fun runAgents() {
    val acquiredAgents = tryAcquire()
    activeAgents.putAll(acquiredAgents)
    acquiredAgents.forEach { agentType, action ->
      val exec = agents[agentType]
      if (exec != null) {
        agentExecutionPool.submit(AgentJob(action, exec, this::agentCompleted))
      }
    }
  }

  private fun tryAcquire(): Map<String, NextAttempt> {
    return findCandidateAgentLocks()
      .map {
        val agentType = it.key
        val agentExecution = it.value
        val interval = intervalProvider.getInterval(agentExecution.agent)

        val currentTime = System.currentTimeMillis()
        if (tryAcquireSingle(agentType, currentTime, interval.timeout)) {
          Pair(agentType, NextAttempt(currentTime, interval.interval, interval.errorInterval))
        } else {
          null
        }
      }
      .filterNotNull()
      .toMap()
  }

  private fun findCandidateAgentLocks(): Map<String, AgentExecutionAction> {
    val skip = activeAgents.entries
    val maxConcurrentAgents = dynamicConfigService.getConfig(Int::class.java, "sql.agent.maxConcurrentAgents", 100)
    val availableAgents = maxConcurrentAgents - skip.size
    if (availableAgents <= 0) {
      log.debug(
        "Not acquiring more locks (maxConcurrentAgents: {}, activeAgents: {}, runningAgents: {})",
        maxConcurrentAgents,
        skip.size,
        skip.joinToString(",")
      )
      return emptyMap()
    }

    val candidateAgentLocks = agents.filter { !activeAgents.containsKey(it.key) }.toMutableMap()

    val existingLocks = jooq.select(field("agent_name"), field("lock_expiry"))
      .from(table("cats_agent_locks"))
      .fetch()
      .intoResultSet()

    val now = System.currentTimeMillis()
    while (existingLocks.next()) {
      if (now > existingLocks.getLong("lock_expiry")) {
        jooq.deleteFrom(table("cats_agent_locks"))
          .where(field("agent_name").eq(existingLocks.getString("agent_name"))
            .and(field("lock_expiry").eq(existingLocks.getString("lock_expiry"))))
          .execute()
      } else if (candidateAgentLocks.size < availableAgents) {
        candidateAgentLocks.remove(existingLocks.getString("agent_name"))
      }
    }

    return candidateAgentLocks.filter { enabledAgents.matcher(it.key).matches() }
  }

  private fun tryAcquireSingle(agentType: String, now: Long, timeout: Long): Boolean {
    try {
      jooq.insertInto(table("cats_agent_locks"))
        .columns(
          field("agent_name"),
          field("owner_id"),
          field("lock_acquired"),
          field("lock_expiry")
        )
        .values(
          agentType,
          nodeIdentity.nodeIdentity,
          now,
          now + timeout
        )
        .execute()
    } catch (e: DataIntegrityViolationException) {
      // Integrity constraint exceptions are ok: It means another clouddriver grabbed the lock before us.
      return false
    } catch (e: SQLException) {
      log.error("Unexpected sql exception while trying to acquire agent lock", e)
      return false
    }
    return true
  }

  private fun releaseLock(agentType: String, nextExecutionTime: Long) {
    val newTtl = nextExecutionTime - System.currentTimeMillis()

    if (newTtl < 500L) {
      try {
        jooq.delete(table("cats_agent_locks")).where(field("agent_name").eq(agentType)).execute()
      } catch (e: SQLException) {
        log.error("Failed to immediately release lock for agent: $agentType", e)
      }
    } else {
      try {
        jooq.update(table("cats_agent_locks"))
          .set(field("lock_expiry"), System.currentTimeMillis() + newTtl)
          .where(field("agent_name").eq(agentType))
          .execute()
      } catch (e: SQLException) {
        log.error("Failed to update lock TTL for agent: $agentType", e)
      }
    }
  }

  private fun agentCompleted(agentType: String, nextExecutionTime: Long) {
    try {
      releaseLock(agentType, nextExecutionTime)
    } finally {
      activeAgents.remove(agentType)
    }
  }
}

private enum class Status {
  SUCCESS, FAILURE
}

private class AgentExecutionAction(
  val agent: Agent,
  val agentExecution: AgentExecution,
  val executionInstrumentation: ExecutionInstrumentation
) {

  fun execute(): Status {
    return try {
      executionInstrumentation.executionStarted(agent)
      val startTime = System.currentTimeMillis()
      agentExecution.executeAgent(agent)
      executionInstrumentation.executionCompleted(agent, System.currentTimeMillis() - startTime)
      Status.SUCCESS
    } catch (t: Throwable) {
      executionInstrumentation.executionFailed(agent, t)
      Status.FAILURE
    }
  }
}

private class AgentJob(
  private val lockReleaseTime: NextAttempt,
  private val action: AgentExecutionAction,
  private val schedulerCallback: (agentType: String, nextExecutionTime: Long) -> Unit
) : Runnable {

  override fun run() {
    var status = Status.FAILURE
    try {
      status = action.execute()
    } finally {
      schedulerCallback(action.agent.agentType, lockReleaseTime.getNextTime(status))
    }
  }
}

private data class NextAttempt(
  val currentTime: Long,
  val successInterval: Long,
  val errorInterval: Long
) {
  fun getNextTime(status: Status): Long =
    if (status == Status.SUCCESS) {
      currentTime + successInterval
    } else {
      currentTime + errorInterval
    }
}
