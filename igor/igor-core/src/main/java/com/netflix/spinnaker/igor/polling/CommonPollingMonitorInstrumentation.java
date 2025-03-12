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

import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.patterns.PolledMeter;
import java.util.concurrent.atomic.AtomicInteger;

public class CommonPollingMonitorInstrumentation {

  private final Registry registry;
  private final Id itemsCachedId;
  private final Id itemsOverThresholdId;
  private final Id pollCycleFailedId;
  private final Id pollCycleTimingId;

  public CommonPollingMonitorInstrumentation(Registry registry) {
    this.registry = registry;
    itemsCachedId = registry.createId("pollingMonitor.newItems");
    itemsOverThresholdId = registry.createId("pollingMonitor.itemsOverThreshold");
    pollCycleFailedId = registry.createId("pollingMonitor.failed");
    pollCycleTimingId = registry.createId("pollingMonitor.pollTiming");
  }

  public void trackItemsCached(AtomicInteger numberOfItems, String monitor, String partition) {
    Gauge gauge =
        (Gauge) registry.get(itemsCachedId.withTags("monitor", monitor, "partition", partition));

    /*
    Spectator gauges are slightly different from Micrometer ones: the polling gauge
    has been deprecated in favour of PolledMeter.
    Previous implementation resulted in NaN most of the times, while we want to store observations
    on Prometheus. We don't need a DistributionSummary, but just a gauge which doesn't get garbage
    collected. For a unique combination of (metricName, tags), PolledMeter will sum up the observed
    values, so to avoid this, we set the gauge ONCE and we pass a strong reference holding the
    latest value to be observed.
    */

    if (gauge == null) {
      PolledMeter.using(registry)
          .withId(itemsCachedId.withTags("monitor", monitor, "partition", partition))
          .monitorValue(numberOfItems);
    }
  }

  public void trackItemsOverThreshold(
      AtomicInteger numberOfItems, String monitor, String partition) {
    Gauge gauge =
        (Gauge)
            registry.get(itemsOverThresholdId.withTags("monitor", monitor, "partition", partition));
    if (gauge == null) {
      PolledMeter.using(registry)
          .withId(itemsOverThresholdId.withTags("monitor", monitor, "partition", partition))
          .monitorValue(numberOfItems);
    }
  }

  public void trackPollCycleTime(String monitor, Runnable lambda) {
    registry.timer(pollCycleTimingId.withTags("monitor", monitor)).record(lambda);
  }

  public void trackPollCycleFailed(String monitor, String partition) {
    registry
        .counter(getPollCycleFailedId().withTags("monitor", monitor, "partition", partition))
        .increment();
  }

  public Id getItemsCachedId() {
    return itemsCachedId;
  }

  public Id getItemsOverThresholdId() {
    return itemsOverThresholdId;
  }

  public Id getPollCycleFailedId() {
    return pollCycleFailedId;
  }

  public Id getPollCycleTimingId() {
    return pollCycleTimingId;
  }
}
