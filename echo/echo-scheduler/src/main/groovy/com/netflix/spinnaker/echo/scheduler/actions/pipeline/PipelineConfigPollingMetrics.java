package com.netflix.spinnaker.echo.scheduler.actions.pipeline;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PipelineConfigPollingMetrics {
  private final Registry registry;

  private final Id triggerCountId;
  private final Id removeCountId;
  private final Id removeFailCountId;
  private final Id failedUpdateCountId;
  private final Id addCountId;
  private final Id syncErrorId;
  private final Id syncTimeId;

  @Autowired
  public PipelineConfigPollingMetrics(Registry registry) {
    this.registry = registry;
    triggerCountId = registry.createId("echo.triggers.count");
    removeCountId = registry.createId("echo.triggers.sync.removeCount");
    removeFailCountId = registry.createId("echo.triggers.sync.removeFailCount");
    failedUpdateCountId = registry.createId("echo.triggers.sync.failedUpdateCount");
    addCountId = registry.createId("echo.triggers.sync.addCount");
    syncErrorId = registry.createId("echo.triggers.sync.error");
    syncTimeId = registry.createId("echo.triggers.sync.executionTimeMillis");
  }

  public void triggerCount(int count) {
    registry.gauge(triggerCountId).set(count);
  }

  public void incrementTriggerSyncError() {
    registry.counter(syncErrorId).increment();
  }

  public void removeCount(int count) {
    registry.gauge(removeCountId).set(count);
  }

  public void failedRemoveCount(int count) {
    registry.gauge(removeFailCountId).set(count);
  }

  public void failedUpdateCount(int count) {
    registry.gauge(failedUpdateCountId).set(count);
  }

  public void addCount(int count) {
    registry.gauge(addCountId).set(count);
  }

  public void recordSyncTime(long elapsedMillis) {
    registry.timer(syncTimeId).record(elapsedMillis, TimeUnit.MILLISECONDS);
  }
}
