package com.netflix.spinnaker.orca.listeners;

import java.util.Optional;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING;
import static java.lang.System.currentTimeMillis;

public class StageTaskPropagationListener implements StageListener {

  private final Logger log = LoggerFactory.getLogger(getClass());

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
