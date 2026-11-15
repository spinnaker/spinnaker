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
import com.netflix.spinnaker.clouddriver.core.ClouddriverHostname
import com.netflix.spinnaker.config.ConnectionPools
import com.netflix.spinnaker.config.SqlTaskLeaseProperties
import com.netflix.spinnaker.kork.sql.routing.withPool
import jakarta.annotation.PreDestroy
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
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
 * The heartbeat runs on a private per-pod scheduler, NOT on the cats clustered agent
 * scheduler: the clustered scheduler runs a given agent on one pod at a time, which for a
 * lease heartbeat would leave every other pod's lease expired and its tasks reaped while
 * alive. The reaper shares this requirement (any pod may reap; the conditional update
 * picks a single winner), so it keeps its own scheduler too.
 *
 * Lease expiry is judged against the database clock, so pod clock skew cannot make a
 * healthy pod look dead or a dead pod look alive.
 */
class SqlTaskLeaseManager(
  private val jooq: DSLContext,
  private val registry: Registry,
  private val properties: SqlTaskLeaseProperties,
  private val reaper: TaskReaper,
  private val retries: SqlRetries = SqlRetries()
) {
  private val log = LoggerFactory.getLogger(javaClass)

  val instanceId: String = ClouddriverHostname.ID

  /** The registration epoch this pod is currently running under; null before the first
   * successful registration and again after [markDead]. Tasks remember the epoch they
   * were created under. */
  @Volatile
  var epoch: Long? = null
    private set

  /** Set once the pod has drained and marked itself dead; fences all further writes. */
  @Volatile
  private var dead = false

  @Volatile
  private var draining = false

  private var scheduler: ScheduledExecutorService? = null

  private val heartbeatRenewedId = registry.createId("sql.taskLease.heartbeat.renewed")
  private val heartbeatReregisteredId = registry.createId("sql.taskLease.heartbeat.reregistered")
  private val heartbeatFailedId = registry.createId("sql.taskLease.heartbeat.failed")

  /** Registers the instance, or renews its lease. The first call (and any call after the
   * reaper declared this row DEAD, or after our row vanished) bumps the epoch: writes from
   * a superseded registration then fail the lease check, and the executor abandons the
   * tasks it was running instead of racing the reaper. */
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
   * leased tasks are writable only while this pod holds the same registration epoch and
   * has not completed its shutdown. Draining alone does not fence — in-flight writes must
   * keep working through the drain window. */
  fun mayWrite(leaseOwner: String?, leaseEpoch: Long?): Boolean {
    if (leaseOwner == null || dead) {
      return leaseOwner == null
    }
    val current = epoch ?: return false
    return leaseOwner == instanceId && leaseEpoch == current
  }

  /** Marks the instance DRAINING. Observability only: in-flight writes keep passing the
   * fence during the drain window, so the executor can finish what it is running. */
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

  /** Extends this instance's lease by [extraMs] beyond the normal TTL. Used before the
   * shutdown reap so a concurrent reaper cannot race the final writes. Direct UPDATE on
   * the current epoch — unlike a heartbeat it works while draining. */
  private fun extendLease(extraMs: Long) {
    val current = epoch ?: return
    withPool(ConnectionPools.TASKS.value) {
      jooq.transactional(retries) { ctx ->
        ctx.update(clouddriverInstancesTable)
          .set(field("lease_expires_at"), dbNowMillis(ctx) + properties.leaseTtlMs + extraMs)
          .set(field("updated_at"), dbNowMillis(ctx))
          .where(field("instance_id").eq(instanceId).and(field("epoch").eq(current)))
          .execute()
      }
    }
  }

  /** Marks the instance DEAD and supersedes the local epoch, so any straggler write from
   * this pod after the shutdown reap is fenced. */
  internal fun markDead() {
    dead = true
    epoch = null
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

  /** Starts eager registration and the per-pod heartbeat. Called by the configuration
   * after the bean is constructed; the first registration is best-effort so a transient
   * DB hiccup at startup does not block the application (the tick retries). */
  fun start() {
    try {
      registerOrHeartbeat()
    } catch (e: Exception) {
      log.warn("Instance {} could not register its lease at startup: {}", instanceId, e.message)
    }
    val exec =
      Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "sql-task-lease-heartbeat").apply { isDaemon = true }
      }
    scheduler = exec
    exec.scheduleWithFixedDelay(
      {
        if (!dead && !draining) {
          try {
            registerOrHeartbeat()
          } catch (e: Exception) {
            log.warn("Instance {} failed to renew its lease: {}", instanceId, e.message)
            registry.counter(heartbeatFailedId).increment()
          }
        }
      },
      properties.heartbeatIntervalMs,
      properties.heartbeatIntervalMs,
      TimeUnit.MILLISECONDS
    )
  }

  /** Graceful shutdown: mark DRAINING, let the executor finish what it can, extend the
   * lease past the reap, apply the reaper's rules to what is left, then mark DEAD —
   * instead of leaving the pod's tasks for TTL expiry. */
  @PreDestroy
  fun shutdown() {
    draining = true
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
    scheduler?.shutdownNow()
    // Extend the lease past the reap window so a concurrent reaper cannot race the
    // final writes; extendLease works on the current epoch even while draining.
    extendLease(properties.drainLeaseExtensionMs)
    epoch?.let { current -> reaper.reapInstanceTasks(instanceId, current) }
    markDead()
    log.info("Instance {} drained and marked dead", instanceId)
  }

}
