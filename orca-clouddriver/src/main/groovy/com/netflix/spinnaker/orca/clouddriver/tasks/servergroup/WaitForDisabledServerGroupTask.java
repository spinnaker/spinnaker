package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.annotations.VisibleForTesting;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.SkippableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.clouddriver.utils.ServerGroupDescriptor;
import com.netflix.spinnaker.orca.retrofit.exceptions.RetrofitExceptionHandler;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

@Component
@Slf4j
public class WaitForDisabledServerGroupTask extends AbstractCloudProviderAwareTask
    implements RetryableTask, SkippableTask {
  private final OortService oortService;
  private final ObjectMapper objectMapper;
  private final ServerGroupFetcher serverGroupFetcher;

  @Autowired
  WaitForDisabledServerGroupTask(OortService oortService, ObjectMapper objectMapper) {
    this(oortService, objectMapper, null);
  }

  @VisibleForTesting
  WaitForDisabledServerGroupTask(
      OortService oortService, ObjectMapper objectMapper, ServerGroupFetcher serverGroupFetcher) {
    this.oortService = oortService;
    this.objectMapper = objectMapper;
    this.serverGroupFetcher =
        serverGroupFetcher == null ? new ServerGroupFetcher() : serverGroupFetcher;
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
    val serverGroupDescriptor = getServerGroupDescriptor(stage);
    try {
      var serverGroup = serverGroupFetcher.fetchServerGroup(serverGroupDescriptor);
      return serverGroup.isDisabled() ? TaskResult.SUCCEEDED : TaskResult.RUNNING;
    } catch (RetrofitError e) {
      val retrofitErrorResponse = new RetrofitExceptionHandler().handle(stage.getName(), e);
      log.error("Unexpected retrofit error {}", retrofitErrorResponse, e);
      return TaskResult.builder(ExecutionStatus.RUNNING)
          .context(Collections.singletonMap("lastRetrofitException", retrofitErrorResponse))
          .build();
    } catch (IOException e) {
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

  // separating it out for testing purposes
  class ServerGroupFetcher {
    TargetServerGroup fetchServerGroup(ServerGroupDescriptor serverGroupDescriptor)
        throws IOException {
      val response =
          oortService.getServerGroup(
              serverGroupDescriptor.getAccount(),
              serverGroupDescriptor.getRegion(),
              serverGroupDescriptor.getName());
      var serverGroupData =
          objectMapper.readValue(
              response.getBody().in(), new TypeReference<Map<String, Object>>() {});
      return new TargetServerGroup(serverGroupData);
    }
  }
}
