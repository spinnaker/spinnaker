package com.netflix.spinnaker.orca.locks;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskExecutionInterceptor;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.AcquireLockStage;
import com.netflix.spinnaker.orca.pipeline.ReleaseLockStage;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.tasks.AcquireLockTask;
import com.netflix.spinnaker.orca.pipeline.tasks.ReleaseLockTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class LockExtendingTaskExecutionInterceptor implements TaskExecutionInterceptor {
  private final LockManager lockManager;
  private final LockingConfigurationProperties lockingConfigurationProperties;

  @Autowired
  public LockExtendingTaskExecutionInterceptor(LockManager lockManager,
                                               LockingConfigurationProperties lockingConfigurationProperties) {
    this.lockManager = lockManager;
    this.lockingConfigurationProperties = lockingConfigurationProperties;
  }

  @Override
  public long maxTaskBackoff() {
    return Duration.ofSeconds(
      lockingConfigurationProperties.getTtlSeconds() - lockingConfigurationProperties.getBackoffBufferSeconds()
    ).toMillis();
  }

  @Override
  public Stage beforeTaskExecution(Task task, Stage stage) {
    extendLocks(task, stage);
    return stage;
  }

  @Override
  public TaskResult afterTaskExecution(Task task, Stage stage, TaskResult taskResult) {
    extendLocks(task, stage);
    return taskResult;
  }

  private void extendLocks(Task task, Stage stage) {
    if (!lockingConfigurationProperties.isEnabled()) {
      return;
    }
    if (task instanceof AcquireLockTask || task instanceof ReleaseLockTask) {
      return;
    }

    Map<HeldLock, AtomicInteger> heldLocks = new HashMap<>();
    Set<String> lockTypes = new HashSet<>(Arrays.asList(AcquireLockStage.PIPELINE_TYPE, ReleaseLockStage.PIPELINE_TYPE));
    stage.getExecution().getStages()
      .stream()
      .filter(s -> lockTypes.contains(s.getType()) && s.getStatus() == ExecutionStatus.SUCCEEDED)
      .forEach(s -> {
      LockContext lc = s.mapTo("/lock", LockContext.LockContextBuilder.class).withStage(s).build();
      AtomicInteger count = heldLocks.computeIfAbsent(
        new HeldLock(lc.getLockName(), lc.getLockValue()),
        hl -> new AtomicInteger(0));
      if (AcquireLockStage.PIPELINE_TYPE.equals(s.getType())) {
        count.incrementAndGet();
      } else {
        count.decrementAndGet();
      }
    });

    List<HeldLock> toExtend = heldLocks
      .entrySet()
      .stream()
      .filter(me -> me.getValue().get() > 0)
      .map(Map.Entry::getKey)
      .collect(Collectors.toList());
    toExtend.forEach(hl ->
      lockManager.extendLock(
        hl.lockName,
        hl.lockValue,
        lockingConfigurationProperties.getTtlSeconds()));
  }

  private static class HeldLock {
    final String lockName;
    final LockManager.LockValue lockValue;

    HeldLock(String lockName, LockManager.LockValue lockValue) {
      this.lockName = lockName;
      this.lockValue = lockValue;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      HeldLock heldLock = (HeldLock) o;
      return Objects.equals(lockName, heldLock.lockName) &&
        Objects.equals(lockValue, heldLock.lockValue);
    }

    @Override
    public int hashCode() {
      return Objects.hash(lockName, lockValue);
    }
  }
}
