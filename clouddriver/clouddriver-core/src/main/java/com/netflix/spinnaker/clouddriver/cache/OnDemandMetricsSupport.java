/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.cache;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class OnDemandMetricsSupport implements OnDemandMetricsSupportable {

  private final Timer onDemandTotal;
  private final Timer dataRead;
  private final Timer dataTransform;
  private final Timer onDemandStore;
  private final Timer cacheWrite;
  private final Timer cacheEvict;
  private final Counter onDemandErrors;
  private final Counter onDemandCount;

  public OnDemandMetricsSupport(Registry registry, OnDemandAgent agent, String onDemandType) {
    final String[] tags =
        new String[] {
          "providerName",
          agent.getProviderName(),
          "agentType",
          agent.getOnDemandAgentType(),
          "onDemandType",
          onDemandType
        };
    this.onDemandTotal = registry.timer(ON_DEMAND_TOTAL_TIME, tags);
    this.dataRead = registry.timer(DATA_READ, tags);
    this.dataTransform = registry.timer(DATA_TRANSFORM, tags);
    this.onDemandStore = registry.timer(ON_DEMAND_STORE, tags);
    this.cacheWrite = registry.timer(CACHE_WRITE, tags);
    this.cacheEvict = registry.timer(CACHE_EVICT, tags);
    this.onDemandErrors = registry.counter(ON_DEMAND_ERROR, tags);
    this.onDemandCount = registry.counter(ON_DEMAND_COUNT, tags);
  }

  private <T> T record(Timer timer, Supplier<T> closure) {
    final long start = System.nanoTime();
    try {
      return closure.get();
    } finally {
      final long elapsed = System.nanoTime() - start;
      timer.record(elapsed, TimeUnit.NANOSECONDS);
    }
  }

  @Override
  public <T> T readData(Supplier<T> closure) {
    return record(dataRead, closure);
  }

  @Override
  public <T> T transformData(Supplier<T> closure) {
    return record(dataTransform, closure);
  }

  @Override
  public <T> T onDemandStore(Supplier<T> closure) {
    return record(onDemandStore, closure);
  }

  @Override
  public <T> T cacheWrite(Supplier<T> closure) {
    return record(cacheWrite, closure);
  }

  @Override
  public <T> T cacheEvict(Supplier<T> closure) {
    return record(cacheEvict, closure);
  }

  @Override
  public void countError() {
    onDemandErrors.increment();
  }

  @Override
  public void countOnDemand() {
    onDemandCount.increment();
  }

  @Override
  public void recordTotalRunTimeNanos(long nanos) {
    onDemandTotal.record(nanos, TimeUnit.NANOSECONDS);
  }
}
