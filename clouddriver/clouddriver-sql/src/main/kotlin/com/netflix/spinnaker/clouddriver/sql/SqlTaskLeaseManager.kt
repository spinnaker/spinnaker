/*
 * Copyright 2026 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.sql

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.RunnableAgent
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import com.netflix.spinnaker.clouddriver.core.ClouddriverHostname
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider
import com.netflix.spinnaker.config.ConnectionPools
import com.netflix.spinnaker.config.SqlTaskLeaseProperties
import com.netflix.spinnaker.kork.sql.routing.withPool
import java.util.concurrent.TimeUnit
import jakarta.annotation.PreDestroy
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.slf4j.LoggerFactory

/** States of a `clouddriver_instances` row. */
internal const val INSTANCE_ACTIVE = "ACTIVE"
internal const val INSTANCE_DRAINING = "DRAINING"
internal const val INSTANCE_DEAD = "DEAD"

/**
 * Registers this clouddriver instance in `clouddriver_instances` and heartbeats its lease
 * (one row per pod, renewed every [SqlTaskLeaseProperties.heartbeatIntervalMs]). Tasks
 * created while the lease is healthy record the instance id and the registration epoch;
 * writes from a superseded registration are rejected with [TaskLeaseLostException] by the
 * repository, and a reaper resolves the tasks of instances whose lease expired.
 *
 * The lease expiry itself is judged against the database clock (`dbNowMillis`), so pod
 * clock skew cannot make a healthy pod look dead or a dead pod look alive.
 */
class SqlTaskLeaseManager(
  private val jooq: DSLContext,
  private val registry: Registry,
  private val properties: SqlTaskLeaseProperties,
  private val retries: SqlRetries = SqlRetries()
) : RunnableAgent, CustomScheduledAgent {

  private val log = LoggerFactory.getLogger(javaClass)

  val instanceId: String = ClouddriverHostname.ID

  /** The registration epoch this pod is currently running under; null before the first
   * successful registration. Tasks remember the epoch they were created under. */
  @Volatile
  var epoch: Long? = null
    private set

  @Volatile
  private var draining = false

  private val heartbeatRenewedId = registry.createId("sql.taskLease.heartbeat.renewed")
  private val heartbeatReregisteredId = registry.createId("sql.taskLease.heartbeat.reregistered")
  private val heartbeatFailedId = registry.createId("sql.taskLease.heartbeat.failed")

  /**
   * Registers the instance, or renews its lease. The first call (and any call after the
   * reaper declared this row DEAD, or after our row vanished) bumps the epoch: writes from
   * a superseded registration then fail the lease check, and the executor abandons the
   * tasks it was running instead of racing the reaper.
   */
  fun registerOrHeartbeat() {
    withPool(ConnectionPools.TASKS.value) {
      jooq.transactional(retries) { ctx ->
        val now = dbNowMillis(ctx)
        val current = epoch
        if (current != null) {
          val renewed =
            ctx.update(clouddriverInstancesTable)
              .set(field("lease_expires_at"), now + properties.leaseTtlMs)
              .set(field("updated_at"), now)
              .where(
                field("instance_id").eq(instanceId)
                  .and(field("epoch").eq(current))
                  .and(field("state").ne(INSTANCE_DEAD))
              )
              .execute()
          if (renewed == 1) {
            registry.counter(heartbeatRenewedId).increment()
            return@transactional
          }
        }
        // First registration, or our row was reaped/vanished: bump the epoch. The upsert is
        // atomic, so a pod restarting repeatedly cannot end up with two live epochs.
        val epochField: org.jooq.Field<Long> =
          org.jooq.impl.DSL.field(org.jooq.impl.DSL.name("clouddriver_instances", "epoch"), Long::class.java)
        val bumped =
          ctx.insertInto(
              clouddriverInstancesTable,
              field("instance_id"),
              field("epoch"),
              field("state"),
              field("lease_expires_at"),
              field("updated_at")
            )
            .values(instanceId, current ?: 0L, INSTANCE_ACTIVE, now + properties.leaseTtlMs, now)
            .onDuplicateKeyUpdate()
            .set(field("epoch"), epochField.plus(1L))
            .set(field("state"), INSTANCE_ACTIVE)
            .set(field("lease_expires_at"), now + properties.leaseTtlMs)
            .set(field("updated_at"), now)
            .execute()
        val fresh =
          ctx.select(field("epoch"))
            .from(clouddriverInstancesTable)
            .where(field("instance_id").eq(instanceId))
            .fetchOne(field("epoch"), Long::class.javaObjectType)
        epoch = fresh
        if (bumped == 1) {
          registry.counter(heartbeatReregisteredId).increment()
          if (current != null && current != fresh) {
            log.warn(
              "Instance {} lost its lease (epoch {} superseded by {}); in-flight tasks were handed to the reaper",
              instanceId,
              current,
              fresh
            )
          }
        } else {
          log.warn("Instance {} failed to register a lease", instanceId)
          registry.counter(heartbeatFailedId).increment()
        }
      }
    }
  }

  /** True when this pod may write to the task: un-leased (legacy) tasks are never fenced;
   * leased tasks are writable only while this pod holds the same registration epoch and is
   * not draining. */
  fun mayWrite(leaseOwner: String?, leaseEpoch: Long?): Boolean {
    if (leaseOwner == null) {
      return true
    }
    if (draining) {
      return false
    }
    val current = epoch ?: return false
    return leaseOwner == instanceId && leaseEpoch == current
  }

  /** Marks the instance DRAINING: heartbeats stop, and no further writes pass the fence
   * once the remaining operations have finished. */
  fun beginDrain() {
    draining = true
    withPool(ConnectionPools.TASKS.value) {
      jooq.transactional(retries) { ctx ->
        ctx.update(clouddriverInstancesTable)
          .set(field("state"), INSTANCE_DRAINING)
          .set(field("updated_at"), dbNowMillis(ctx))
          .where(field("instance_id").eq(instanceId))
          .execute()
      }
    }
  }

  /** Marks the instance DEAD. Only the pod itself (after a graceful drain) or a reaper
   * whose conditional update won may do this. */
  fun markDead() {
    withPool(ConnectionPools.TASKS.value) {
      jooq.transactional(retries) { ctx ->
        ctx.update(clouddriverInstancesTable)
          .set(field("state"), INSTANCE_DEAD)
          .set(field("updated_at"), dbNowMillis(ctx))
          .where(field("instance_id").eq(instanceId))
          .execute()
      }
    }
  }

  override fun run() {
    if (draining) {
      return
    }
    try {
      registerOrHeartbeat()
    } catch (e: Exception) {
      // A missed heartbeat is harmless while the TTL has not passed; the log keeps it visible.
      log.warn("Instance {} failed to renew its lease: {}", instanceId, e.message)
      registry.counter(heartbeatFailedId).increment()
    }
  }

  /** Graceful shutdown: mark DRAINING, let the executor finish what it can, resolve what is
   * left with the reaper's rules, then mark DEAD instead of waiting out the TTL. */
  @PreDestroy
  fun shutdown() {
    beginDrain()
    val waitMs = TimeUnit.SECONDS.toMillis(properties.shutdownWaitSeconds)
    val deadline = System.nanoTime() + waitMs * 1_000_000
    while (System.nanoTime() < deadline) {
      try {
        Thread.sleep(500)
      } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        break
      }
    }
    // Extend the lease for the reap itself so a concurrent reaper cannot race the final writes.
    run()
    epoch?.let { current ->
      reaper.reapInstanceTasks(instanceId, current)
    }
    markDead()
    log.info("Instance {} drained and marked dead", instanceId)
  }

  override fun getAgentType(): String = javaClass.simpleName
  override fun getProviderName(): String = CoreProvider.PROVIDER_NAME
  override fun getPollIntervalMillis(): Long = properties.heartbeatIntervalMs
  override fun getTimeoutMillis(): Long = DEFAULT_TIMEOUT_MILLIS

  lateinit var reaper: TaskReaper
    internal set

  companion object {
    private val DEFAULT_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30)
  }
}
