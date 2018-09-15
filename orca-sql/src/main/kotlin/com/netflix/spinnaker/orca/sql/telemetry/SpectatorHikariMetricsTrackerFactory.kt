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
package com.netflix.spinnaker.orca.sql.telemetry

import com.netflix.spectator.api.Registry
import com.zaxxer.hikari.metrics.MetricsTracker
import com.zaxxer.hikari.metrics.MetricsTrackerFactory
import com.zaxxer.hikari.metrics.PoolStats
import org.springframework.scheduling.annotation.Scheduled

class SpectatorHikariMetricsTrackerFactory(
  private val registry: Registry
) : MetricsTrackerFactory {

  private val trackers: MutableMap<String, SpectatorHikariMetricsTracker> = mutableMapOf()

  @Scheduled(fixedRate = 5000L)
  fun recordConnectionPoolMetrics() {
    trackers.values.forEach { it.recordPoolStats() }
  }

  override fun create(poolName: String, poolStats: PoolStats): MetricsTracker =
    SpectatorHikariMetricsTracker(poolName, poolStats, registry).let {
      trackers.putIfAbsent(poolName, it)
      it
    }
}
