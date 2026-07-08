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

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.spectator.micrometer.MicrometerRegistry;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.discovery.RemoteStatusChangedEvent;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.TaskScheduler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommonPollingMonitorTest {

  static final String PARTITION_1 = "partition1";
  static final String PARTITION_2 = "partition2";
  static final String MONITOR = "DefaultMonitor";
  static final Integer DELTA_SIZE_PARTITION_1 = 5;
  static final Integer DELTA_SIZE_PARTITION2 = 1500;

  IgorConfigurationProperties properties;
  DynamicConfigService dynamicConfigService;
  DiscoveryStatusListener discoveryStatusListener;
  Optional<LockService> lockService;
  MicrometerRegistry registry;
  TaskScheduler scheduler;
  CommonPollingMonitorInstrumentation instrumentation;
  DefaultPollingMonitor monitor;

  @BeforeEach
  void setup() {
    properties = new IgorConfigurationProperties();
    dynamicConfigService = new DynamicConfigService.NoopDynamicConfig();
    discoveryStatusListener = new DiscoveryStatusListener(true);
    lockService = Optional.empty();
    registry = new MicrometerRegistry(new SimpleMeterRegistry());
    instrumentation = new CommonPollingMonitorInstrumentation(registry);
    scheduler = mock(TaskScheduler.class);

    monitor =
        new DefaultPollingMonitor(
            properties,
            registry,
            dynamicConfigService,
            discoveryStatusListener,
            lockService,
            scheduler);
  }

  @Test
  void testPollGaugesAreRecordedCorrectly() throws InterruptedException {
    Id itemsCachedIdPartitionOne =
        instrumentation
            .getItemsCachedId()
            .withTags("monitor", MONITOR, "partition", PARTITION_1);
    Id itemsCachedIdPartitionTwo =
        instrumentation
            .getItemsCachedId()
            .withTags("monitor", MONITOR, "partition", PARTITION_2);
    Id itemsOverThresholdPartitionOne =
        instrumentation
            .getItemsOverThresholdId()
            .withTags("monitor", MONITOR, "partition", PARTITION_1);
    Id itemsOverThresholdPartitionTwo =
        instrumentation
            .getItemsOverThresholdId()
            .withTags("monitor", MONITOR, "partition", PARTITION_2);

    // scheduler triggers polling for 2 partitions
    monitor.setDeltasMap(
        Map.of(PARTITION_1, DELTA_SIZE_PARTITION_1, PARTITION_2, DELTA_SIZE_PARTITION2));
    monitor.poll(true);
    PolledMeter.update(registry);

    assertEquals(DELTA_SIZE_PARTITION_1, registry.gauge(itemsCachedIdPartitionOne).value());
    assertEquals(0, registry.gauge(itemsCachedIdPartitionTwo).value());
    assertEquals(0, registry.gauge(itemsOverThresholdPartitionOne).value());
    assertEquals(DELTA_SIZE_PARTITION2, registry.gauge(itemsOverThresholdPartitionTwo).value());

    // simulate 2nd trigger in another thread
    Thread thread =
        new Thread(
            () -> {
              monitor.setDeltasMap(Map.of(PARTITION_1, 8, PARTITION_2, 10));
              monitor.poll(false);
            });
    thread.start();
    thread.join();
    PolledMeter.update(registry);

    // latest values are observed
    assertEquals(8, registry.gauge(itemsCachedIdPartitionOne).value());
    assertEquals(10, registry.gauge(itemsCachedIdPartitionTwo).value());
    assertEquals(0, registry.gauge(itemsOverThresholdPartitionOne).value());
    assertEquals(0, registry.gauge(itemsOverThresholdPartitionTwo).value());
  }

  @Test
  void testPollCycleTimingWorks() throws Exception {
    Timer timer =
        registry.timer(
            instrumentation.pollCycleTimingId.withTags("monitor", monitor.getName()));
    assertEquals(0, timer.count());
    assertEquals(0, timer.totalTime());

    // execute the runnable straight away
    when(scheduler.schedule(any(), any()))
        .thenAnswer(
            invocation -> {
              Runnable runnable = invocation.getArgument(0);
              Executors.newSingleThreadExecutor().submit(runnable).get();
              return mock(ScheduledFuture.class);
            });

    // on startup
    monitor.onApplicationEvent(mock(RemoteStatusChangedEvent.class));

    assertEquals(1, timer.count());
    assertTrue(timer.totalTime() > 0);
  }

  @Test
  void testPollCycleFailedWorks() {
    Counter counter =
        registry.counter(
            instrumentation.pollCycleFailedId.withTags(
                "monitor", monitor.getName(), "partition", PARTITION_1));
    assertEquals(0, counter.count());
    monitor.setFailOnCommit(true);

    monitor.poll(true);

    assertEquals(1, counter.count());
  }

  // test, concrete implementation of the base class
  private static class DefaultPollingMonitor extends CommonPollingMonitor {

    private Map<String, Integer> deltasMap = new HashMap<>();
    private boolean failOnCommit = false;

    public DefaultPollingMonitor(
        IgorConfigurationProperties igorProperties,
        Registry registry,
        DynamicConfigService dynamicConfigService,
        DiscoveryStatusListener discoveryStatusListener,
        Optional lockService,
        TaskScheduler scheduler) {
      super(
          igorProperties,
          registry,
          dynamicConfigService,
          discoveryStatusListener,
          lockService,
          scheduler);
    }

    @Override
    protected PollingDelta generateDelta(PollContext ctx) {
      return new PollingDelta() {
        @Override
        public List getItems() {
          Integer deltaSize = deltasMap.get(ctx.partitionName);
          if (deltaSize != null && deltaSize > 0) {
            return IntStream.rangeClosed(1, deltaSize).boxed().collect(Collectors.toList());
          }
          return List.of();
        }
      };
    }

    @Override
    protected void commitDelta(PollingDelta delta, boolean sendEvents) {
      if (failOnCommit) {
        throw new RuntimeException("Can't commit");
      }
      System.out.println("Committing delta");
    }

    @Override
    public void poll(boolean sendEvents) {
      List<String> partitions = List.of(PARTITION_1, PARTITION_2);
      partitions.forEach(partition -> pollSingle(new PollContext(partition)));
    }

    @Override
    public String getName() {
      return MONITOR;
    }

    void setDeltasMap(Map<String, Integer> deltasMap) {
      this.deltasMap = deltasMap;
    }

    void setFailOnCommit(boolean failOnCommit) {
      this.failOnCommit = failOnCommit;
    }
  }
}
