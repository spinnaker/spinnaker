/*
 * Copyright 2024 Wise, PLC.
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

package com.netflix.spinnaker.igor.polling;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.concurrent.atomic.AtomicInteger;

public class CommonPollingMonitorInstrumentation {

  public static final String ITEMS_CACHED_METRIC_NAME = "pollingMonitor.newItems";
  public static final String ITEMS_OVER_THRESHOLD_METRIC_NAME = "pollingMonitor.itemsOverThreshold";
  public static final String POLL_CYCLE_FAILED_METRIC_NAME = "pollingMonitor.failed";
  public static final String POLL_CYCLE_TIMING_METRIC_NAME = "pollingMonitor.pollTiming";

  private final MeterRegistry registry;

  public CommonPollingMonitorInstrumentation(MeterRegistry registry) {
    this.registry = registry;
  }

  public void trackItemsCached(AtomicInteger numberOfItems, String monitor, String partition) {
    registry.gauge(
        ITEMS_CACHED_METRIC_NAME,
        Tags.of("monitor", monitor, "partition", partition),
        numberOfItems);
  }

  public void trackItemsOverThreshold(
      AtomicInteger numberOfItems, String monitor, String partition) {
    registry.gauge(
        ITEMS_OVER_THRESHOLD_METRIC_NAME,
        Tags.of("monitor", monitor, "partition", partition),
        numberOfItems);
  }

  public void trackPollCycleTime(String monitor, Runnable lambda) {
    registry.timer(POLL_CYCLE_TIMING_METRIC_NAME, "monitor", monitor).record(lambda);
  }

  public void trackPollCycleFailed(String monitor, String partition) {
    registry
        .counter(POLL_CYCLE_FAILED_METRIC_NAME, "monitor", monitor, "partition", partition)
        .increment();
  }
}
