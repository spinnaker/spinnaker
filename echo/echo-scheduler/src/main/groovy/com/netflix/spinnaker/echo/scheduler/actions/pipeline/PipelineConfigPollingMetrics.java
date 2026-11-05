package com.netflix.spinnaker.echo.scheduler.actions.pipeline;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PipelineConfigPollingMetrics {
  private final MeterRegistry registry;

  private final AtomicInteger triggerCount = new AtomicInteger();
  private final AtomicInteger removeCount = new AtomicInteger();
  private final AtomicInteger removeFailCount = new AtomicInteger();
  private final AtomicInteger failedUpdateCount = new AtomicInteger();
  private final AtomicInteger addCount = new AtomicInteger();

  @Autowired
  public PipelineConfigPollingMetrics(MeterRegistry registry) {
    this.registry = registry;
    registry.gauge("echo.triggers.count", triggerCount);
    registry.gauge("echo.triggers.sync.removeCount", removeCount);
    registry.gauge("echo.triggers.sync.removeFailCount", removeFailCount);
    registry.gauge("echo.triggers.sync.failedUpdateCount", failedUpdateCount);
    registry.gauge("echo.triggers.sync.addCount", addCount);
  }

  public void triggerCount(int count) {
    triggerCount.set(count);
  }

  public void incrementTriggerSyncError() {
    registry.counter("echo.triggers.sync.error").increment();
  }

  public void removeCount(int count) {
    removeCount.set(count);
  }

  public void failedRemoveCount(int count) {
    removeFailCount.set(count);
  }

  public void failedUpdateCount(int count) {
    failedUpdateCount.set(count);
  }

  public void addCount(int count) {
    addCount.set(count);
  }

  public void recordSyncTime(long elapsedMillis) {
    registry
        .timer("echo.triggers.sync.executionTimeMillis")
        .record(elapsedMillis, TimeUnit.MILLISECONDS);
  }
}
