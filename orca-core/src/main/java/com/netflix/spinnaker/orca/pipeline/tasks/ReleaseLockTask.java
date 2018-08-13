package com.netflix.spinnaker.orca.pipeline.tasks;

import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.locks.LockManager;
import com.netflix.spinnaker.orca.locks.LockContext;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

@Component
public class ReleaseLockTask implements Task {

  private final LockManager lockManager;

  @Autowired
  public ReleaseLockTask(LockManager lockManager) {
    this.lockManager = lockManager;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    final LockContext lock = stage.mapTo("/lock", LockContext.LockContextBuilder.class).withStage(stage).build();
    lockManager.releaseLock(lock.getLockName(), lock.getLockValue(), lock.getLockHolder());
    return TaskResult.SUCCEEDED;
  }
}
