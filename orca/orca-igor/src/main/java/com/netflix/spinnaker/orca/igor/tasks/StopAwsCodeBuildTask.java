package com.netflix.spinnaker.orca.igor.tasks;

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.igor.IgorService;
import com.netflix.spinnaker.orca.igor.model.AwsCodeBuildExecution;
import com.netflix.spinnaker.orca.igor.model.AwsCodeBuildStageDefinition;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StopAwsCodeBuildTask implements Task {
  private final IgorService igorService;

  @Override
  @Nonnull
  public TaskResult execute(@Nonnull StageExecution stage) {
    AwsCodeBuildStageDefinition stageDefinition = stage.mapTo(AwsCodeBuildStageDefinition.class);
    AwsCodeBuildExecution execution = stageDefinition.getBuildInfo();
    if (execution != null) {
      AwsCodeBuildExecution latestDetails =
          Retrofit2SyncCall.execute(
              igorService.stopAwsCodeBuild(
                  stageDefinition.getAccount(), getBuildId(execution.getArn())));
      Map<String, Object> context = new HashMap<>();
      context.put("buildInfo", latestDetails);
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(context).build();
    }
    return TaskResult.SUCCEEDED;
  }

  private String getBuildId(String arn) {
    return arn.split("/")[1];
  }
}
