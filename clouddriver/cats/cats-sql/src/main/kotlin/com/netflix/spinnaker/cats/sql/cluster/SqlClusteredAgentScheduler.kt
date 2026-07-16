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

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation.elapsedTimeMs
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider
import com.netflix.spinnaker.cats.cluster.NodeIdentity
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider
import com.netflix.spinnaker.cats.cluster.ShardingFilter
import com.netflix.spinnaker.cats.module.CatsModuleAware
import com.netflix.spinnaker.cats.sql.SqlUtil
import com.netflix.spinnaker.config.ConnectionPools
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.sql.routing.withPool
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import java.sql.SQLException
import java.util.concurrent.*
import java.util.regex.Pattern
import java.util.regex.Pattern.CASE_INSENSITIVE

/**
 * IMPORTANT: Using SQL for locking isn't a good idea. By enabling this scheduler, you'll be adding a fair amount of
 * unnecessary load to your database. This implementation is offered for operational topology simplicity, but is not
 * recommended for real workloads. Instead, use the Redis scheduler (`redis.scheduler.enabled=true` and
 * `sql.scheduler.enabled=false`) or implement a scheduler based on ZooKeeper, etcd, consul, and so-on.
 *
 */
class SqlClusteredAgentScheduler(
  private val jooq: DSLContext,
  private val nodeIdentity: NodeIdentity,
  private val intervalProvider: AgentIntervalProvider,
  private val nodeStatusProvider: NodeStatusProvider,
  private val dynamicConfigService: DynamicConfigService,
  enabledAgentPattern: String,
  private val disabledAgentsConfig: List<String>,
  agentLockAcquisitionIntervalSeconds: Long? = null,
  private val tableNamespace: String? = null,
  private val agentExecutionPool: ExecutorService = Executors.newCachedThreadPool(
    ThreadFactoryBuilder().setNameFormat(AgentExecutionAction::class.java.simpleName + "-%d").build()
  ),
  lockPollingScheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
    ThreadFactoryBuilder().setNameFormat(SqlClusteredAgentScheduler::class.java.simpleName + "-%d").build()
  ),
  private val shardingFilter: ShardingFilter
) : CatsModuleAware(), AgentScheduler<AgentLock>, Runnable {

  private val log = LoggerFactory.getLogger(javaClass)

  private val agents: MutableMap<String, AgentExecutionAction> = ConcurrentHashMap()
  private val longRunningAgents: MutableMap<String, AgentExecutionRunnable> = ConcurrentHashMap()
  private val activeAgents: MutableMap<String, NextAttempt> = ConcurrentHashMap()
  private val activeAgentsFutures: MutableMap<String, Future<*>> = ConcurrentHashMap()
  private val enabledAgents: Pattern

  private val referenceTable = "cats_agent_locks"
  private val lockTable = if (tableNamespace.isNullOrBlank()) {
    referenceTable
  } else {
    "${referenceTable}_$tableNamespace"
  }

  init {
    if (!tableNamespace.isNullOrBlank()) {
      withPool(POOL_NAME) {
        SqlUtil.createTableLike(jooq, lockTable, referenceTable)
      }
    }

    val lockInterval = agentLockAcquisitionIntervalSeconds ?: 1L
    lockPollingScheduler.scheduleAtFixedRate(this, 0, lockInterval, TimeUnit.SECONDS)
    enabledAgents = Pattern.compile(enabledAgentPattern, CASE_INSENSITIVE)
  }

  override fun schedule(
    agent: Agent,
    agentExecution: AgentExecution,
    executionInstrumentation: ExecutionInstrumentation
  ) {
    if (agent is AgentSchedulerAware) {
      agent.agentScheduler = this
    }
    if (agentExecution is LongRunningAgentExecution) {
      val runnable =
        AgentExecutionRunnable(agent, agentExecution, executionInstrumentation)
      longRunningAgents[agent.agentType] = runnable
    }
    else {
      agents[agent.agentType] = AgentExecutionAction(agent, agentExecution, executionInstrumentation)
    }
  }

  override fun unschedule(agent: Agent) {
    releaseLock(agent.agentType, 0) // Release the lock immediately
    if (longRunningAgents.contains(agent.agentType)) {
      val runnable = longRunningAgents[agent.agentType]
      (runnable!!.execution as LongRunningAgentExecution).stopExecutingAndCleanup().join()
      longRunningAgents.remove(agent.agentType)
    }
    else {
      agents.remove(agent.agentType)
    }
  }

  override fun run() {
    if (nodeStatusProvider.isNodeEnabled) {
      try {
        scheduleLongRunningAgents()
        runAgents()
      } catch (t: Throwable) {
        log.error("Failed running cache agents", t)
      }
    }
  }

  private fun runAgents() {
    val acquiredAgents = tryAcquire()
    activeAgents.putAll(acquiredAgents)
    acquiredAgents.forEach { agentType, nextAttempt ->
      val exec = agents[agentType]
      if (exec != null) {
        activeAgentsFutures[agentType] = agentExecutionPool.submit(AgentJob(nextAttempt, exec, this::agentCompleted))
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
    cleanupZombieAgents()
    val skip = HashMap(activeAgents).entries
    val maxConcurrentAgents = dynamicConfigService.getConfig(Int::class.java, "sql.agent.max-concurrent-agents", 100)
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

    val disabledAgents = dynamicConfigService.getConfig(
      String::class.java,
      "sql.agent.disabled-agents",
      disabledAgentsConfig.joinToString(",")
    ).split(",").map { it.trim() }

    val candidateAgentLocks = agents
      .filter { shardingFilter.filter(it.value.agent) }
      .filter { !activeAgents.containsKey(it.key) }
      .filter { enabledAgents.matcher(it.key).matches() }
      .filterNot { disabledAgents.contains(it.key) }
      .toMutableMap()

    log.debug("Agents running: {}, agents disabled: {}. Picking next agents to run from: {}",
      activeAgents.keys, disabledAgents, candidateAgentLocks.keys)

    withPool(POOL_NAME) {
      val existingLocks = jooq.select(field("agent_name"), field("lock_expiry"))
        .from(table(lockTable))
        .fetch()
        .intoResultSet()

      val now = System.currentTimeMillis()
      while (existingLocks.next()) {
        val lockExpiry = existingLocks.getLong("lock_expiry")
        if (now > lockExpiry) {
          try {
            jooq.deleteFrom(table(lockTable))
              .where(
                field("agent_name").eq(existingLocks.getString("agent_name"))
                  .and(field("lock_expiry").eq(lockExpiry))
              )
              .execute()
          } catch (e: SQLException) {
            log.error(
              "Failed deleting agent lock ${existingLocks.getString("agent_name")} with expiry " +
                lockExpiry,
              e
            )

            candidateAgentLocks.remove(existingLocks.getString("agent_name"))
          }
        } else {
          candidateAgentLocks.remove(existingLocks.getString("agent_name"))
        }
      }
    }

    log.debug("Next agents to run: {}, max: {}", candidateAgentLocks.keys, availableAgents)

    val trimmedCandidates = mutableMapOf<String, AgentExecutionAction>()
    candidateAgentLocks.entries
      .shuffled()
      .forEach {
        if (trimmedCandidates.size >= availableAgents) {
          log.warn(
            "Dropping caching agent: {}. Wanted to run {} agents, but a max of {} was configured and there are " +
              "already {} currently running. Consider increasing sql.agent.max-concurrent-agents",
            it.key, candidateAgentLocks.size, maxConcurrentAgents, skip)
          return@forEach
        }
        trimmedCandidates[it.key] = it.value
      }

    return trimmedCandidates
  }

  private fun scheduleLongRunningAgents() {
    renewAgents()
    submitLongRunningAgents()
  }

  private fun renewAgents() {
    val now = System.currentTimeMillis()
    val runningAgentsToBeRenewed = longRunningAgents
      .filter { (it.value.execution as LongRunningAgentExecution).isRunning}
    log.debug("{} Long Running Agents Locks to be renewed in {}", runningAgentsToBeRenewed.size, nodeIdentity.nodeIdentity)

    runningAgentsToBeRenewed.forEach {renewSingleLongRunning(it.key, now, intervalProvider.getInterval(it.value.agent).interval) }
  }

  private fun submitLongRunningAgents() {
    val now = System.currentTimeMillis()

    val filteredForThisNode = longRunningAgents
      .filter { shardingFilter.filter(it.value.agent) }
    log.debug("{} Long Running Agents filtered for node: {}", filteredForThisNode.size, nodeIdentity.nodeIdentity)

    val thisNodeAgentsNotRunning = filteredForThisNode
      .filter { !(it.value.execution as LongRunningAgentExecution).isRunning}
    log.debug("{} Long Running Agents filtered for node: {} not running", thisNodeAgentsNotRunning.size, nodeIdentity.nodeIdentity)

    val candidateAgentLocks = thisNodeAgentsNotRunning
      .filter { tryAcquireSingleLongRunning(it.key, now, intervalProvider.getInterval(it.value.agent).interval) }

    log.debug("{} Long Running Agents filtered for node: {} with lock acquired", candidateAgentLocks.size, nodeIdentity.nodeIdentity)

    log.debug("Long Running Agents to be executed in {}: {}", nodeIdentity.nodeIdentity, candidateAgentLocks.keys)
    candidateAgentLocks.forEach { agentExecutionPool.submit(it.value)}
  }

  private fun cleanupZombieAgents() {
    val zombieAgentThreshold = dynamicConfigService.getConfig(Long::class.java, "sql.agent.zombie-threshold-ms", 3600000)
    activeAgents
      .filter { it.value.currentTime < System.currentTimeMillis() - zombieAgentThreshold }
      .forEach {
        log.warn("Found zombie agent {}, removing it", it.key)
        activeAgents.remove(it.key, it.value)
        // Cancel zombie futures interrupting their AgentExecutionAction threads
        val f = activeAgentsFutures.remove(it.key)
        if (f == null) {
          log.warn("Agent execution without future for cancelling it: {}", it.key)
        } else {
          if (!f.cancel(true) && !f.isCancelled) {
            log.error("Unable to cancel execution for agent {} after {}ms (Future.cancel returned false). This may leak resources!", it.key, zombieAgentThreshold)
          }
        }
      }
  }


  private fun renewSingleLongRunning(agentType: String, now: Long, timeout: Long): Boolean {
    try {
      withPool(POOL_NAME) {
        val renewed = jooq.update(table(lockTable))
          .set(field("lock_acquired"), now)
          .set(field("lock_expiry"), now + timeout)
          .where(field("agent_name").eq(agentType), field("owner_id").eq(nodeIdentity.nodeIdentity))
          .execute() > 0
        log.debug("Agent: {} lock {} renewed in {}", agentType,  if (renewed) "" else "not", nodeIdentity.nodeIdentity)
        return renewed
      }
    } catch (e: SQLException) {
      log.error("Unexpected sql exception while trying to renew agent lock", e)
      return false
    }
  }

  private fun tryAcquireSingleLongRunning(agentType: String, now: Long, timeout: Long): Boolean {
    try {
      withPool(POOL_NAME) {
        if (jooq.update(table(lockTable))
            .set(field("lock_acquired"), now)
            .set(field("lock_expiry"), now + timeout)
            .set(field("owner_id"),nodeIdentity.nodeIdentity)
            .where(field("agent_name").eq(agentType), field("lock_expiry").lt(now))
            .execute() > 0) {
          return true
        }
        jooq.insertInto(table(lockTable))
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
      }
    } catch (e: DataIntegrityViolationException) {
      // Integrity constraint exceptions are ok: It means there was a racecondition between us acquiring this lock
      // and some other node updating its lock lease
      log.debug("Race condition while trying to acquire agent lock", e)
      return false
    } catch (e: DataAccessException) {
      // Integrity constraint exceptions are ok: It means there was a racecondition between us acquiring this lock
      // and some other node updating its lock lease
      log.warn("Race condition while trying to acquire agent lock", e)
      return false
    } catch (e: SQLException) {
      log.error("Unexpected sql exception while trying to acquire agent lock", e)
      return false
    }
    log.debug("Successfully acquired lock for {} in {}", agentType, nodeIdentity.nodeIdentity)
    return true
  }

  private fun tryAcquireSingle(agentType: String, now: Long, timeout: Long): Boolean {
    try {
      withPool(POOL_NAME) {
        jooq.insertInto(table(lockTable))
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
      }
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

    withPool(POOL_NAME) {
      if (newTtl < dynamicConfigService.getConfig(Long::class.java, "sql.agent.release-threshold-ms", 500)) {
        try {
          jooq.delete(table(lockTable)).where(field("agent_name").eq(agentType)).execute()
        } catch (e: SQLException) {
          log.error("Failed to immediately release lock for agent: $agentType", e)
        }
      } else {
        try {
          jooq.update(table(lockTable))
            .set(field("lock_expiry"), System.currentTimeMillis() + newTtl)
            .where(field("agent_name").eq(agentType))
            .execute()
        } catch (e: SQLException) {
          log.error("Failed to update lock TTL for agent: $agentType", e)
        }
      }
    }
  }

  private fun agentCompleted(agentType: String, nextExecutionTime: Long) {
    try {
      releaseLock(agentType, nextExecutionTime)
    } finally {
      activeAgents.remove(agentType)
      activeAgentsFutures.remove(agentType)
    }
  }

  companion object {
    private val POOL_NAME = ConnectionPools.CACHE_WRITER.value
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
    val startTimeMs = System.currentTimeMillis()
    return try {
      executionInstrumentation.executionStarted(agent)
      agentExecution.executeAgent(agent)
      executionInstrumentation.executionCompleted(agent, elapsedTimeMs(startTimeMs))
      Status.SUCCESS
    } catch (t: Throwable) {
      executionInstrumentation.executionFailed(agent, t, elapsedTimeMs(startTimeMs))
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
