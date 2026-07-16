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
import org.springframework.dao.DataIntegrityViolationException
import java.sql.SQLException
import java.util.concurrent.*
import java.util.function.BiConsumer
import java.util.regex.Pattern
import java.util.regex.Pattern.CASE_INSENSITIVE
import com.netflix.spinnaker.cats.agent.LongRunningAgentExecutionState.RUNNING
import org.jooq.exception.SQLStateClass
import java.time.Clock
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.filter
import kotlin.collections.partition
import kotlin.concurrent.withLock

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
  private val shardingFilter: ShardingFilter,
  private val rebalancePercentageThreshold: Int = 50,
  private val clock: Clock = Clock.systemDefaultZone(),
) : CatsModuleAware(), AgentScheduler<AgentLock>, Runnable {

  private val log = LoggerFactory.getLogger(javaClass)

  /**
   * longRunningAgents contains all available long-running caching agents (the same agents on all pods).
   * activeLongRunningAgents contains only running agents on this Clouddriver instance.
   *
   * longRunningAgents is updated by "schedule" and "unschedule" methods.
   * transition longRunningAgents -> activeLongRunningAgents happens only by one background thread ("run" method).
   *
   * scheduled agents don't start immediately after calling the "schedule" method. They start only
   * on the next "run" invocation.
   *
   * "run" method compares active agents with all available agents and reconcile them:
   * 1. stops all outdated agents first.
   * 2. schedules missed agents.
   *
   * The access to both maps happens under longRunningAgentsLock.
   */
  private val longRunningAgentsLock = ReentrantLock()
  private val longRunningAgents: MutableMap<String, AgentExecutionRunnable> = mutableMapOf()
  private val activeLongRunningAgents: MutableMap<String, AgentExecutionRunnable> = mutableMapOf()

  private val agents: MutableMap<String, AgentExecutionAction> = ConcurrentHashMap()
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
      longRunningAgentsLock.withLock {
        val runnable = AgentExecutionRunnable(agent, agentExecution, executionInstrumentation)
        longRunningAgents[agent.agentType] = runnable
      }
    }
    else {
      agents[agent.agentType] = AgentExecutionAction(agent, agentExecution, executionInstrumentation, clock)
    }
  }

  override fun unschedule(agent: Agent) {
    log.info("Unscheduling agent {}", agent.agentType)

    longRunningAgentsLock.withLock {
      longRunningAgents.remove(agent.agentType) // do not release the lock immediately
    }

    agents.remove(agent.agentType)?.let {
      releaseLock(agent.agentType, 0)
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

        val currentTime = clock.millis()
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

      val now = clock.millis()
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

  /**
   * Reconcile long-running caching agent executions.
   * longRunningAgents - all available agents.
   * activeLongRunningAgents - running agents on this instance.
   *
   * Find agents to stop:
   * 1. Deleted agents
   * 2. New agent configuration (same agent type, different execution object)
   * 3. Failed agents (will restart immediately after stopping)
   * 4. Agents that should be running on different instances (only if exceeds rebalancing threshold)
   * 5. Agents for which the lock failed to renew
   *
   * Find agents to start:
   * 1. Agents that are not running on this specific instance yet
   *
   * Process found agents:
   * Stop agents
   * Start agents
   * Save all active agents in activeLongRunningAgents
   *
   * Notes:
   * If some agents are unscheduled during the reconciliation, they will be stopped next run.
   * We don't force stop moved agents until a threshold is reached to avoid constant restarts. Only
   * failed/stopped agents are moved if number of running agents is below the threshold.
   */
  private fun scheduleLongRunningAgents() {
    val schedulingResult = longRunningAgentsLock.withLock {
      // filter "unscheduled" agents. need to stop them
      val (scheduledActiveAgents, unscheduledActiveAgents) = activeLongRunningAgents.values
        .partition {
          val scheduled = longRunningAgents[it.agent.agentType]
          scheduled != null && // agents with the same type
            scheduled === it // the exact same agent configuration (by reference)
        }
      log.info("Stopping {} unscheduled active long running agents: {}", unscheduledActiveAgents.size, unscheduledActiveAgents.agentTypes())

      // filter not running agents (failed to run, failed to start), will restart them
      val (runningActiveAgents, failedActiveAgents) = scheduledActiveAgents
        .partition { it.execution.asLongRunning().state == RUNNING }
      log.info("Stopping {} not running (failed) agents: {}", failedActiveAgents.size, failedActiveAgents.agentTypes())

      val (agentsToKeep, rebalancedAgents) = rebalanceLongRunningAgents(runningActiveAgents)

      val (agentsWithRenewedLock, agentsWithoutLocks) = renewAgents(agentsToKeep)

      // all running/failed agents that has to be stopped
      val agentsToStop = unscheduledActiveAgents + failedActiveAgents + rebalancedAgents + agentsWithoutLocks

      // find new agents that we need to start
      val runningAgentTypes = agentsWithRenewedLock.agentTypes().toSet()
      val agentsToStart = longRunningAgents
        .filter { shardingFilter.filter(it.value.agent) } // should be on this instance
        .filterNot { runningAgentTypes.contains(it.key) } // not running (either new or failed)
        .map { it.value }

      LongRunningAgentSchedulingResult(
        toStop = agentsToStop,
        toKeep = agentsWithRenewedLock,
        toStart = agentsToStart
      )
    }

    stopLongRunningAgents(schedulingResult.toStop)
    val (startedNewAgents, notStartedAgents) = startLongRunningAgents(schedulingResult.toStart)

    // only one thread should access "activeLongRunningAgents", so we don't have to acquire the lock
    // to guarantee visibility of modifications.
    // but do it just in case if the "scheduler" thread dies and another one will be created
    longRunningAgentsLock.withLock {
      val newActiveLongRunningAgents = (startedNewAgents + schedulingResult.toKeep).associateBy { it.agent.agentType }

      this.activeLongRunningAgents.clear()
      this.activeLongRunningAgents += newActiveLongRunningAgents
    }
  }

  private fun renewAgents(runningActiveAgents: List<AgentExecutionRunnable>): RenewAgentLocksResult {
    val now = clock.millis()
    log.debug("{} Long Running Agents Locks to be renewed in {}: {}", runningActiveAgents.size, nodeIdentity.nodeIdentity, runningActiveAgents.agentTypes())

    val (succeeded, failed) = runningActiveAgents.partition {
      renewSingleLongRunning(it.agent.agentType, now, intervalProvider.getInterval(it.agent).interval)
    }

    if (failed.isNotEmpty()) {
      log.warn("Failed to renew locks for {} agents: {}", failed.size, failed.agentTypes())
    }

    return RenewAgentLocksResult(succeeded = succeeded, failed = failed)
  }

  private fun rebalanceLongRunningAgents(runningActiveAgents: List<AgentExecutionRunnable>): RebalancingResult {
    if (rebalancePercentageThreshold <= 0) {
      return RebalancingResult(toKeep = runningActiveAgents)
    }

    val filteredForThisNode = runningActiveAgents.filter { shardingFilter.filter(it.agent) }
    log.debug("{} Long Running Agents filtered for node: {}", filteredForThisNode.size, nodeIdentity.nodeIdentity)

    val expectedForThisNodeOrOne = if (filteredForThisNode.size > 0) filteredForThisNode.size else 1
    log.debug("{} Long Running Agents Currently running in node {}", runningActiveAgents.size, nodeIdentity.nodeIdentity)

    val aboveExpectedPercentageThreshold =
      ((runningActiveAgents.size - filteredForThisNode.size.toDouble()) / expectedForThisNodeOrOne * 100).toInt()
    if (aboveExpectedPercentageThreshold <= rebalancePercentageThreshold) {
      return RebalancingResult(toKeep = runningActiveAgents)
    }

    val (toKeep, toStop) = runningActiveAgents.partition { shardingFilter.filter(it.agent) }
    log.info(
      "Long Running Agents in {} needing to be rebalanced above configured threshold ({}%>{}%). Stopping {}",
      nodeIdentity.nodeIdentity,
      aboveExpectedPercentageThreshold,
      rebalancePercentageThreshold,
      toStop.map { it.agent.agentType })

    return RebalancingResult(toKeep = toKeep, toStop = toStop)
  }

  private fun stopLongRunningAgents(agents: List<AgentExecutionRunnable>) {
    val stopFutures = agents.map {
      val longRunningExecution = it.execution.asLongRunning()
      longRunningExecution.stopExecutingAndCleanup()
        .orTimeout(longRunningExecution.stopTimeoutMillis, TimeUnit.MILLISECONDS)
        .whenComplete(BiConsumer { _: Void?, _: Throwable? ->
          releaseLock(it.agent.agentType, 0)
        })
    }

    CompletableFuture.allOf(*stopFutures.toTypedArray()).join()
  }

  private fun startLongRunningAgents(agents: List<AgentExecutionRunnable>): StartAgentsResult {
    val now = clock.millis()

    val (withLock, withoutLock) = agents
      .partition { tryAcquireSingleLongRunning(it.agent.agentType, now, intervalProvider.getInterval(it.agent).interval) }
    log.debug("{} Long Running Agents filtered for node: {} with lock acquired: {}", withLock.size, nodeIdentity.nodeIdentity, withLock.agentTypes())

    withLock.forEach { agentExecutionPool.submit(it) }

    return StartAgentsResult(
      succeeded = withLock,
      failed = withoutLock,
    )
  }

  private fun cleanupZombieAgents() {
    val zombieAgentThreshold = dynamicConfigService.getConfig(Long::class.java, "sql.agent.zombie-threshold-ms", 3600000)
    activeAgents
      .filter { it.value.currentTime < clock.millis() - zombieAgentThreshold }
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
    } catch (e: Exception) {
      when (e) {
        // Integrity constraint exceptions are ok: It means there was a race condition between us
        // acquiring this lock and some other node updating its lock lease
        is DataIntegrityViolationException -> {
          log.debug("Race condition while trying to acquire agent lock", e)
        }
        is org.jooq.exception.DataAccessException -> {
          if (e.sqlStateClass() == SQLStateClass.C23_INTEGRITY_CONSTRAINT_VIOLATION) {
            log.debug("Race condition while trying to acquire agent lock", e)
          } else {
            log.warn("Unexpected DataAccessException while trying to acquire agent lock", e)
          }
        }
        // any other sql exception are unexpected
        is org.springframework.dao.DataAccessException, is SQLException -> {
          log.warn("Unexpected SQL exception while trying to acquire agent lock", e)
        }
      }
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
    val newTtl = nextExecutionTime - clock.millis()

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
            .set(field("lock_expiry"), clock.millis() + newTtl)
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
  val executionInstrumentation: ExecutionInstrumentation,
  private val clock: Clock,
) {

  fun execute(): Status {
    val startTimeMs = clock.millis()
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

private fun AgentExecution.asLongRunning() = this as LongRunningAgentExecution

private fun List<AgentExecutionRunnable>.agentTypes() = map { it.agent.agentType }

private data class RebalancingResult(
  val toKeep: List<AgentExecutionRunnable> = listOf(),
  val toStop: List<AgentExecutionRunnable> = listOf(),
)

private data class RenewAgentLocksResult(
  val succeeded: List<AgentExecutionRunnable> = listOf(),
  val failed: List<AgentExecutionRunnable> = listOf(),
)

private data class StartAgentsResult(
  val succeeded: List<AgentExecutionRunnable> = listOf(),
  val failed: List<AgentExecutionRunnable> = listOf(),
)

private data class LongRunningAgentSchedulingResult(
  val toKeep: List<AgentExecutionRunnable> = listOf(),
  val toStop: List<AgentExecutionRunnable> = listOf(),
  val toStart: List<AgentExecutionRunnable> = listOf(),
)
