/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.listeners;

import java.util.List;
import java.util.Optional;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.netflix.spinnaker.orca.ExecutionStatus.*;
import static java.lang.System.currentTimeMillis;

public class StageStatusPropagationListener implements StageListener {

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Override
  public void beforeTask(Persister persister, Stage stage, Task task) {
    if (stage.getStatus().isComplete()) {
      return;
    }

    log.debug("***** {} Stage {} starting", stage.getExecution().getId(), stage.getType());
    log.info("Marking Stage as RUNNING (stageId: {})", stage.getId());
    stage.setStartTime(Optional.ofNullable(stage.getStartTime()).orElse(currentTimeMillis()));
    stage.setStatus(RUNNING);

    persister.save(stage);
  }

  @Override
  public void afterTask(Persister persister, Stage stage, Task task, ExecutionStatus executionStatus, boolean wasSuccessful) {
    if (executionStatus != null) {
      List<Task> tasks = stage.getTasks();
      if (executionStatus == SUCCEEDED && !task.isStageEnd()) {
        // mark stage as RUNNING as not all tasks have completed
        stage.setStatus(RUNNING);
        log.info("Task SUCCEEDED but not all other tasks are complete (stageId: {})", stage.getId());
        if (tasks.stream().anyMatch(it -> it.getStatus() == FAILED_CONTINUE)) {
            // task fails and continue pipeline on failure is checked, set stage to the same status.
            stage.setStatus(FAILED_CONTINUE);
        }
      } else {
        if (executionStatus == SUCCEEDED && tasks.stream().anyMatch(it -> it.getStatus() == FAILED_CONTINUE)) {
          stage.setStatus(FAILED_CONTINUE);
        } else {
          stage.setStatus( executionStatus);
        }

        if (executionStatus.isComplete()) {
          log.debug("***** {} Stage {} {}", stage.getExecution().getId(), stage.getType(), executionStatus);
          stage.setEndTime(Optional.ofNullable(stage.getEndTime()).orElse(currentTimeMillis()));
        }
      }
    } else {
      log.debug("***** {} Stage {} terminal due to missing status", stage.getExecution().getId(), stage.getType());
      stage.setEndTime(currentTimeMillis());
      stage.setStatus(TERMINAL);
    }

    persister.save(stage);
  }
}
