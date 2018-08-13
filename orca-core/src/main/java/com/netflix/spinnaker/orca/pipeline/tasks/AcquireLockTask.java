package com.netflix.spinnaker.orca.pipeline.tasks;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.exceptions.DefaultExceptionHandler;
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler;
import com.netflix.spinnaker.orca.locks.LockFailureException;
import com.netflix.spinnaker.orca.locks.LockManager;
import com.netflix.spinnaker.orca.locks.LockContext;
import com.netflix.spinnaker.orca.locks.LockingConfigurationProperties;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class AcquireLockTask implements Task {

  private final LockManager lockManager;
  private final LockingConfigurationProperties lockingConfigurationProperties;

  @Autowired
  public AcquireLockTask(LockManager lockManager,
                         LockingConfigurationProperties lockingConfigurationProperties) {
    this.lockManager = lockManager;
    this.lockingConfigurationProperties = lockingConfigurationProperties;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    LockContext lock = stage.mapTo("/lock", LockContext.LockContextBuilder.class).withStage(stage).build();
    try {
      lockManager.acquireLock(lock.getLockName(), lock.getLockValue(), lock.getLockHolder(), lockingConfigurationProperties.getTtlSeconds());
      return new TaskResult(ExecutionStatus.SUCCEEDED, Collections.singletonMap("lock", lock));
    } catch (LockFailureException lfe) {
      Map<String, Object> resultContext = new HashMap<>();
      ExceptionHandler.Response exResult = new DefaultExceptionHandler().handle("acquireLock", lfe);
      exResult.getDetails().put("lockName", lfe.getLockName());
      exResult.getDetails().put("currentLockValue", lfe.getCurrentLockValue());
      resultContext.put("exception", exResult);
      return new TaskResult(ExecutionStatus.TERMINAL, resultContext);
    }
  }
}
