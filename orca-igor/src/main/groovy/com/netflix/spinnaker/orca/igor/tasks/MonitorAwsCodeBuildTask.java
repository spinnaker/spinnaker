/*
 * Copyright 2020 Amazon.com, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.igor.tasks;

import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.igor.IgorService;
import com.netflix.spinnaker.orca.igor.model.AwsCodeBuildExecution;
import com.netflix.spinnaker.orca.igor.model.AwsCodeBuildStageDefinition;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MonitorAwsCodeBuildTask extends RetryableIgorTask<AwsCodeBuildStageDefinition>
    implements OverridableTimeoutRetryableTask {
  @Getter protected long backoffPeriod = TimeUnit.SECONDS.toMillis(10);
  @Getter protected long timeout = TimeUnit.HOURS.toMillis(8); // maximum build timeout

  private final IgorService igorService;

  @Override
  @Nonnull
  public TaskResult tryExecute(@Nonnull AwsCodeBuildStageDefinition stageDefinition) {
    AwsCodeBuildExecution execution =
        igorService.getAwsCodeBuildExecution(
            stageDefinition.getAccount(), getBuildId(stageDefinition.getBuildInfo().getArn()));
    Map<String, Object> context = new HashMap<>();
    context.put("buildInfo", execution);
    return TaskResult.builder(execution.getStatus().getExecutionStatus()).context(context).build();
  }

  @Override
  @Nonnull
  protected AwsCodeBuildStageDefinition mapStage(@Nonnull Stage stage) {
    return stage.mapTo(AwsCodeBuildStageDefinition.class);
  }

  private String getBuildId(String arn) {
    return arn.split("/")[1];
  }
}
