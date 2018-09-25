package com.netflix.spinnaker.orca.pipeline.tasks;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.locks.LockContext;
import com.netflix.spinnaker.orca.pipeline.AcquireLockStage;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Optional;

@Component
public class DetermineLockTask implements Task {

  private final StageNavigator stageNavigator;

  @Autowired
  public DetermineLockTask(StageNavigator stageNavigator) {
    this.stageNavigator = stageNavigator;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    Optional<StageNavigator.Result> lockStageResult = stageNavigator
      .ancestors(stage)
      .stream()
      .filter(
        r -> r.getStageBuilder() instanceof AcquireLockStage)
      .filter(r ->
        stage.getParentStageId() == null ? r.getStage().getParentStageId() == null :
          stage.getParentStageId().equals(r.getStage().getParentStageId()))
      .findFirst();

    try {
      final LockContext lockContext;
      if (lockStageResult.isPresent()) {
        final Stage lockStage = lockStageResult.get().getStage();
        lockContext = lockStage.mapTo("/lock", LockContext.LockContextBuilder.class).withStage(lockStage).build();
      } else {
        lockContext = stage.mapTo("/lock", LockContext.LockContextBuilder.class).build();
      }

      return new TaskResult(ExecutionStatus.SUCCEEDED, Collections.singletonMap("lock", lockContext));
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to determine lock from context or previous lock stage", ex);
    }
  }
}
