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

import com.netflix.spinnaker.kork.annotations.Beta;
import java.util.function.Supplier;

@Beta
public interface OnDemandMetricsSupportable {
  String ON_DEMAND_TOTAL_TIME = "onDemand_total";
  String DATA_READ = "onDemand_read";
  String DATA_TRANSFORM = "onDemand_transform";
  String ON_DEMAND_STORE = "onDemand_store";
  String CACHE_WRITE = "onDemand_cache";
  String CACHE_EVICT = "onDemand_evict";
  String ON_DEMAND_ERROR = "onDemand_error";
  String ON_DEMAND_COUNT = "onDemand_count";

  <T> T readData(Supplier<T> closure);

  <T> T transformData(Supplier<T> closure);

  <T> T onDemandStore(Supplier<T> closure);

  <T> T cacheWrite(Supplier<T> closure);

  default void cacheWrite(Runnable closure) {
    cacheWrite(
        () -> {
          closure.run();
          return null;
        });
  }

  <T> T cacheEvict(Supplier<T> closure);

  default void cacheEvict(Runnable closure) {
    cacheEvict(
        () -> {
          closure.run();
          return null;
        });
  }

  void countError();

  void countOnDemand();

  void recordTotalRunTimeNanos(long nanos);
}
