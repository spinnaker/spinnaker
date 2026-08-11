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

package com.netflix.spinnaker.igor.polling

import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener
import com.netflix.spinnaker.kork.discovery.RemoteStatusChangedEvent
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService.NoopDynamicConfig
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.mockito.Mockito
import org.springframework.scheduling.TaskScheduler
import spock.lang.Specification

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

import static org.mockito.Mockito.mock

class CommonPollingMonitorTest extends Specification {

  static final String PARTITION_1 = "partition1"
  static final String PARTITION_2 = "partition2"
  static final String MONITOR = "DefaultMonitor"
  static final Integer DELTA_SIZE_PARTITION_1 = 5
  static final Integer DELTA_SIZE_PARTITION2 = 1500

  IgorConfigurationProperties properties = new IgorConfigurationProperties()
  DynamicConfigService dynamicConfigService = new NoopDynamicConfig();
  DiscoveryStatusListener discoveryStatusListener = new DiscoveryStatusListener(true)
  Optional<LockService> lockService = Optional.empty()
  MeterRegistry registry
  TaskScheduler scheduler
  CommonPollingMonitorInstrumentation instrumentation
  DefaultPollingMonitor monitor

  def setup() {
    registry = new SimpleMeterRegistry()
    instrumentation = new CommonPollingMonitorInstrumentation(registry)
    scheduler =  mock(TaskScheduler.class)

    monitor =
      new DefaultPollingMonitor(
        properties,
        registry,
        dynamicConfigService,
        discoveryStatusListener,
        lockService,
        scheduler)
  }

  def testPollGaugesAreRecordedCorrectly() {
    when: "scheduler triggers polling for 2 partitions"
    monitor.setDeltasMap(
      [(PARTITION_1): DELTA_SIZE_PARTITION_1, (PARTITION_2): DELTA_SIZE_PARTITION2])
    monitor.poll(true)
    then:
    gaugeValue(CommonPollingMonitorInstrumentation.ITEMS_CACHED_METRIC_NAME, PARTITION_1) == DELTA_SIZE_PARTITION_1
    gaugeValue(CommonPollingMonitorInstrumentation.ITEMS_CACHED_METRIC_NAME, PARTITION_2) == 0
    and:
    gaugeValue(CommonPollingMonitorInstrumentation.ITEMS_OVER_THRESHOLD_METRIC_NAME, PARTITION_1) == 0
    gaugeValue(CommonPollingMonitorInstrumentation.ITEMS_OVER_THRESHOLD_METRIC_NAME, PARTITION_2) == DELTA_SIZE_PARTITION2
    // simulate 2nd trigger in another thread
    when: "scheduler triggers again"
    def thread = Thread.start( {
      monitor.setDeltasMap([(PARTITION_1): 8, (PARTITION_2): 10])
      monitor.poll(false)
    })
    thread.join()
    then:
    // latest values are observed
    gaugeValue(CommonPollingMonitorInstrumentation.ITEMS_CACHED_METRIC_NAME, PARTITION_1) == 8
    gaugeValue(CommonPollingMonitorInstrumentation.ITEMS_CACHED_METRIC_NAME, PARTITION_2) == 10
    and:
    gaugeValue(CommonPollingMonitorInstrumentation.ITEMS_OVER_THRESHOLD_METRIC_NAME, PARTITION_1) == 0
    gaugeValue(CommonPollingMonitorInstrumentation.ITEMS_OVER_THRESHOLD_METRIC_NAME, PARTITION_2) == 0
  }

  private double gaugeValue(String metricName, String partition) {
    registry.find(metricName)
      .tags("monitor", MONITOR, "partition", partition)
      .gauge()
      .value()
  }

  def testPollCycleTimingWorks() {
    given:
    assert registry.find(CommonPollingMonitorInstrumentation.POLL_CYCLE_TIMING_METRIC_NAME)
      .tags("monitor", monitor.getName())
      .timer() == null

    // execute the runnable straight away
    Mockito.when(scheduler.schedule(Mockito.any(), Mockito.any()))
      .then({
        def runnable = it.getArgument(0)
        Executors.newSingleThreadExecutor().submit(runnable).get();
        return Mock(ScheduledFuture.class)
      })

    when:"on startup"
    monitor.onApplicationEvent(Mock(RemoteStatusChangedEvent))
    then:
    Timer timer = registry.find(CommonPollingMonitorInstrumentation.POLL_CYCLE_TIMING_METRIC_NAME)
      .tags("monitor", monitor.getName())
      .timer()
    timer.count() == 1
    timer.totalTime(TimeUnit.NANOSECONDS) > 0
  }

  def testPollCycleFailedWorks() {
    given:
    monitor.setFailOnCommit(true)
    when:
    monitor.poll(true)
    then:
    Counter counter = registry.find(CommonPollingMonitorInstrumentation.POLL_CYCLE_FAILED_METRIC_NAME)
      .tags("monitor", monitor.getName(), "partition", PARTITION_1)
      .counter()
    counter.count() == 1
  }

  // test, concrete implementation of the base class
  private class DefaultPollingMonitor extends CommonPollingMonitor {

    private Map<String, Integer> deltasMap = new HashMap<>()
    private boolean failOnCommit = false

    public DefaultPollingMonitor(IgorConfigurationProperties igorProperties,
                                 MeterRegistry registry,
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
        scheduler
      );
    }

    @Override
    protected PollingDelta generateDelta(PollContext ctx) {
      return new PollingDelta() {
        @Override
        public List getItems() {
          return deltasMap.get(ctx.partitionName) > 0 ? 1..deltasMap.get(ctx.partitionName) : []
        }
      };
    }


    protected void commitDelta(PollingDelta delta, boolean sendEvents) {
      if (failOnCommit) {
        throw new RuntimeException("Can't commit")
      }
      System.out.println("Committing delta")
    }

    @Override
    public void poll(boolean sendEvents) {
      def partitions = [PARTITION_1, PARTITION_2]
      partitions.each {String partition -> pollSingle(new PollContext(partition))}
    }

    @Override
    public String getName() {
      return MONITOR
    }

    void setDeltasMap(Map<String, Integer> deltasMap) {
      this.deltasMap = deltasMap
    }

    void setFailOnCommit(boolean failOnCommit) {
      this.failOnCommit = failOnCommit
    }
  }
}
