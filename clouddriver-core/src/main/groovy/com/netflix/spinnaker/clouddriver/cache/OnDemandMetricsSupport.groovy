/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.cache

import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Timer

import java.util.concurrent.TimeUnit
import java.util.function.Supplier

class OnDemandMetricsSupport {
  public static final String ON_DEMAND_TOTAL_TIME = "onDemand_total"
  public static final String DATA_READ = "onDemand_read"
  public static final String DATA_TRANSFORM = "onDemand_transform"
  public static final String ON_DEMAND_STORE = "onDemand_store"
  public static final String CACHE_WRITE = "onDemand_cache"
  public static final String CACHE_EVICT = "onDemand_evict"
  public static final String ON_DEMAND_ERROR = "onDemand_error"
  public static final String ON_DEMAND_COUNT = "onDemand_count"

  private final Timer onDemandTotal
  private final Timer dataRead
  private final Timer dataTransform
  private final Timer onDemandStore
  private final Timer cacheWrite
  private final Timer cacheEvict
  private final Counter onDemandErrors
  private final Counter onDemandCount

  public OnDemandMetricsSupport(Registry registry, OnDemandAgent agent, String onDemandType) {
    final String[] tags = ["providerName", agent.providerName, "agentType", agent.onDemandAgentType, "onDemandType", onDemandType]
    this.onDemandTotal = registry.timer(ON_DEMAND_TOTAL_TIME, tags)
    this.dataRead = registry.timer(DATA_READ, tags)
    this.dataTransform = registry.timer(DATA_TRANSFORM, tags)
    this.onDemandStore = registry.timer(ON_DEMAND_STORE, tags)
    this.cacheWrite = registry.timer(CACHE_WRITE, tags)
    this.cacheEvict = registry.timer(CACHE_EVICT, tags)
    this.onDemandErrors = registry.counter(ON_DEMAND_ERROR, tags)
    this.onDemandCount = registry.counter(ON_DEMAND_COUNT, tags)
  }

  private <T> T record(Timer timer, Supplier<T> closure) {
    final long start = System.nanoTime()
    try {
      return closure.get()
    } finally {
      final long elapsed = System.nanoTime() - start
      timer.record(elapsed, TimeUnit.NANOSECONDS)
    }
  }

  public <T> T readData(Supplier<T> closure) {
    record(dataRead, closure)
  }

  public <T> T transformData(Supplier<T> closure) {
    record(dataTransform, closure)
  }

  public <T> T onDemandStore(Supplier<T> closure) {
    record(onDemandStore, closure)
  }

  public <T> T cacheWrite(Supplier<T> closure) {
    record(cacheWrite, closure)
  }

  public <T> T cacheEvict(Supplier<T> closure) {
    record(cacheEvict, closure)
  }

  public void countError() {
    onDemandErrors.increment()
  }

  public void countOnDemand() {
    onDemandCount.increment()
  }

  public void recordTotalRunTimeNanos(long nanos) {
    onDemandTotal.record(nanos, TimeUnit.NANOSECONDS)
  }

}
