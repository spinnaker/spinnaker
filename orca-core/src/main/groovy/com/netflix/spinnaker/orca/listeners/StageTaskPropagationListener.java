package com.netflix.spinnaker.orca.listeners;

import java.util.Optional;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.Task;
import lombok.extern.slf4j.Slf4j;
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING;
import static java.lang.System.currentTimeMillis;

@Slf4j
public class StageTaskPropagationListener implements StageListener {
  @Override
  public void beforeTask(Persister persister, final Stage stage, final Task task) {
    if (task.getStartTime() == null) {
      task.setStartTime(currentTimeMillis());
      task.setEndTime(null);
      task.setStatus(RUNNING);
      log.info("Setting task status to {} (stageId: {}, taskId: {}) [beforeTask]", task.getStatus(), stage.getId(), task.getId());
      persister.save(stage);
    }
  }

  @Override
  public void afterTask(Persister persister, final Stage stage, final Task task, ExecutionStatus executionStatus, boolean wasSuccessful) {
    task.setStatus(executionStatus);
    task.setEndTime(Optional.ofNullable(task.getEndTime()).orElse(currentTimeMillis()));
    log.info("Setting task status to {} (stageId: {}, taskId: {}, taskName: {}) [afterTask]", task.getStatus(), stage.getId(), task.getId(), task.getName());
    persister.save(stage);
  }
}
