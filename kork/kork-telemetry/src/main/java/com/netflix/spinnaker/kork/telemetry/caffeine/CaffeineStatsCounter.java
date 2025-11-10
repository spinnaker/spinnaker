/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.telemetry.caffeine;

import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.github.benmanes.caffeine.cache.stats.StatsCounter;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import java.util.concurrent.TimeUnit;

public class CaffeineStatsCounter implements StatsCounter {
  private final Counter hitCount;
  private final Counter missCount;
  private final Counter loadSuccessCount;
  private final Counter loadFailureCount;
  private final Timer totalLoadTime;
  private final Counter evictionCount;
  private final Counter evictionWeight;

  /**
   * Constructs an instance for use by a single cache.
   *
   * @param registry the registry of metric instances
   * @param metricsPrefix the prefix name for the metrics
   */
  public CaffeineStatsCounter(Registry registry, String metricsPrefix) {
    hitCount = registry.counter(metricsPrefix + ".hits");
    missCount = registry.counter(metricsPrefix + ".misses");
    totalLoadTime = registry.timer(metricsPrefix + ".loads");
    loadSuccessCount = registry.counter(metricsPrefix + ".loads-success");
    loadFailureCount = registry.counter(metricsPrefix + ".loads-failure");
    evictionCount = registry.counter(metricsPrefix + ".evictions");
    evictionWeight = registry.counter(metricsPrefix + ".evictions-weight");
  }

  @Override
  public void recordHits(int count) {
    hitCount.increment(count);
  }

  @Override
  public void recordMisses(int count) {
    missCount.increment(count);
  }

  @Override
  public void recordLoadSuccess(long loadTime) {
    loadSuccessCount.increment();
    totalLoadTime.record(loadTime, TimeUnit.NANOSECONDS);
  }

  @Override
  public void recordLoadFailure(long loadTime) {
    loadFailureCount.increment();
    totalLoadTime.record(loadTime, TimeUnit.NANOSECONDS);
  }

  @Override
  public void recordEviction(int weight, RemovalCause cause) {
    evictionCount.increment();
    evictionWeight.increment(weight);
  }

  @Override
  public CacheStats snapshot() {
    return CacheStats.of(
        hitCount.count(),
        missCount.count(),
        loadSuccessCount.count(),
        loadFailureCount.count(),
        totalLoadTime.count(),
        evictionCount.count(),
        evictionWeight.count());
  }

  @Override
  public String toString() {
    return snapshot().toString();
  }
}
