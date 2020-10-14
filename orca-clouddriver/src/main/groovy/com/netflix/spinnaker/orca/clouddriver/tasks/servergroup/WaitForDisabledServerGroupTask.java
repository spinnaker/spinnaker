package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
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
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

@Component
@Slf4j
public class WaitForDisabledServerGroupTask extends AbstractCloudProviderAwareTask
    implements RetryableTask {
  private final OortService oortService;
  private final ObjectMapper objectMapper;

  @Autowired
  WaitForDisabledServerGroupTask(OortService oortService, ObjectMapper objectMapper) {
    this.oortService = oortService;
    this.objectMapper = objectMapper;
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
    val serverGroupDescriptor = getServerGroupDescriptor(stage);
    try {
      var serverGroup = fetchServerGroup(serverGroupDescriptor);
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

  private TargetServerGroup fetchServerGroup(ServerGroupDescriptor serverGroupDescriptor)
      throws IOException {
    val response =
        oortService.getServerGroup(
            serverGroupDescriptor.getAccount(),
            serverGroupDescriptor.getRegion(),
            serverGroupDescriptor.getName());
    var serverGroupData =
        (Map<String, Object>) objectMapper.readValue(response.getBody().in(), Map.class);
    return new TargetServerGroup(serverGroupData);
  }
}
