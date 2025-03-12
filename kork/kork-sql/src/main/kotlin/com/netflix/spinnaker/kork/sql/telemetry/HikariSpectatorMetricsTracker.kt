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
package com.netflix.spinnaker.kork.sql.telemetry

import com.netflix.spectator.api.Registry
import com.zaxxer.hikari.metrics.IMetricsTracker
import com.zaxxer.hikari.metrics.PoolStats
import java.util.concurrent.TimeUnit

/**
 * Records the metrics of HikariCP into the given Spectator registry.
 */
class HikariSpectatorMetricsTracker(
  poolName: String,
  private val poolStats: PoolStats,
  private val registry: Registry
) : IMetricsTracker {

  private val connectionAcquiredId = registry.createId("sql.pool.$poolName.connectionAcquiredTiming")
  private val connectionUsageId = registry.createId("sql.pool.$poolName.connectionUsageTiming")
  private val connectionTimeoutId = registry.createId("sql.pool.$poolName.connectionTimeout")

  private val idleConnectionsGauge = registry.gauge("sql.pool.$poolName.idle")
  private val activeConnectionsGauge = registry.gauge("sql.pool.$poolName.active")
  private val totalConnectionsGauge = registry.gauge("sql.pool.$poolName.total")
  private val blockedThreadsGauge = registry.gauge("sql.pool.$poolName.blocked")

  /**
   * Record the individual pool's statistics.
   */
  fun recordPoolStats() {
    idleConnectionsGauge.set(poolStats.idleConnections.toDouble())
    activeConnectionsGauge.set(poolStats.activeConnections.toDouble())
    totalConnectionsGauge.set(poolStats.totalConnections.toDouble())
    blockedThreadsGauge.set(poolStats.pendingThreads.toDouble())
  }

  override fun recordConnectionAcquiredNanos(elapsedAcquiredNanos: Long) {
    registry.timer(connectionAcquiredId).record(elapsedAcquiredNanos, TimeUnit.NANOSECONDS)
  }

  override fun recordConnectionUsageMillis(elapsedBorrowedMillis: Long) {
    registry.timer(connectionUsageId).record(elapsedBorrowedMillis, TimeUnit.MILLISECONDS)
  }

  override fun recordConnectionTimeout() {
    registry.counter(connectionTimeoutId).increment()
  }
}
