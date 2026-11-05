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

import com.zaxxer.hikari.metrics.IMetricsTracker
import com.zaxxer.hikari.metrics.PoolStats
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Records the metrics of HikariCP into the given Micrometer registry.
 */
class HikariMicrometerMetricsTracker(
  poolName: String,
  private val poolStats: PoolStats,
  private val registry: MeterRegistry
) : IMetricsTracker {

  private val connectionAcquiredName = "sql.pool.$poolName.connectionAcquiredTiming"
  private val connectionUsageName = "sql.pool.$poolName.connectionUsageTiming"
  private val connectionTimeoutName = "sql.pool.$poolName.connectionTimeout"

  private val idleConnections = AtomicLong()
  private val activeConnections = AtomicLong()
  private val totalConnections = AtomicLong()
  private val blockedThreads = AtomicLong()

  init {
    registry.gauge("sql.pool.$poolName.idle", idleConnections) { it.get().toDouble() }
    registry.gauge("sql.pool.$poolName.active", activeConnections) { it.get().toDouble() }
    registry.gauge("sql.pool.$poolName.total", totalConnections) { it.get().toDouble() }
    registry.gauge("sql.pool.$poolName.blocked", blockedThreads) { it.get().toDouble() }
  }

  /**
   * Record the individual pool's statistics.
   */
  fun recordPoolStats() {
    idleConnections.set(poolStats.idleConnections.toLong())
    activeConnections.set(poolStats.activeConnections.toLong())
    totalConnections.set(poolStats.totalConnections.toLong())
    blockedThreads.set(poolStats.pendingThreads.toLong())
  }

  override fun recordConnectionAcquiredNanos(elapsedAcquiredNanos: Long) {
    registry.timer(connectionAcquiredName).record(elapsedAcquiredNanos, TimeUnit.NANOSECONDS)
  }

  override fun recordConnectionUsageMillis(elapsedBorrowedMillis: Long) {
    registry.timer(connectionUsageName).record(elapsedBorrowedMillis, TimeUnit.MILLISECONDS)
  }

  override fun recordConnectionTimeout() {
    registry.counter(connectionTimeoutName).increment()
  }
}
