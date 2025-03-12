package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup;

import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.SkippableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.retrofit.exceptions.SpinnakerServerExceptionHandler;
import java.util.Collections;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WaitForDisabledServerGroupTask
    implements CloudProviderAware, RetryableTask, SkippableTask {

  private final CloudDriverService cloudDriverService;

  @Autowired
  WaitForDisabledServerGroupTask(CloudDriverService cloudDriverService) {
    this.cloudDriverService = cloudDriverService;
  }

  @Override
  public long getBackoffPeriod() {
    return 10000;
  }

  @Override
  public long getTimeout() {
    return 1800000;
  }

  @NotNull
  @Override
  public TaskResult execute(@NotNull StageExecution stage) {
    try {
      TaskInput input = stage.mapTo(TaskInput.class);
      input.validate();
      if (isPartialDisable(input)) {
        return TaskResult.builder(ExecutionStatus.SKIPPED).build();
      }
    } catch (IllegalArgumentException e) {
      log.warn("Error mapping task input", e);
      return TaskResult.builder(ExecutionStatus.SKIPPED).build();
    }

    // we have established that this is a full disable, so we need to enforce that the server group
    // is actually disabled
    var serverGroupDescriptor = getServerGroupDescriptor(stage);
    try {
      var serverGroup = cloudDriverService.getServerGroup(serverGroupDescriptor);
      return serverGroup.getDisabled() ? TaskResult.SUCCEEDED : TaskResult.RUNNING;
    } catch (SpinnakerServerException e) {
      var errorResponse = new SpinnakerServerExceptionHandler().handle(stage.getName(), e);
      log.error("Unexpected http error {}", errorResponse, e);
      return TaskResult.builder(ExecutionStatus.RUNNING)
          .context(Collections.singletonMap("lastSpinnakerException", errorResponse))
          .build();
    } catch (Exception e) {
      log.error("Unexpected exception", e);
      return TaskResult.builder(ExecutionStatus.RUNNING)
          .context(Collections.singletonMap("lastException", e))
          .build();
    }
  }

  // RRB and Monitored Deployments do "partial disables", i.e. they run DisableServerGroupTask with
  // a `desiredPercentage` which will only disable some instances, not the entire server group (so
  // this won't set the `disabled` flag on the server group)
  private boolean isPartialDisable(TaskInput input) {
    return input.desiredPercentage != null && input.desiredPercentage < 100;
  }

  private static class TaskInput {
    @Nullable public Integer desiredPercentage;

    void validate() {
      if (desiredPercentage != null && (desiredPercentage < 0 || desiredPercentage > 100)) {
        throw new IllegalArgumentException(
            "desiredPercentage is expected to be in [0, 100] but found " + desiredPercentage);
      }
    }
  }
}
