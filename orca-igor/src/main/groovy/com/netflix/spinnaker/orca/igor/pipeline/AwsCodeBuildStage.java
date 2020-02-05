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
package com.netflix.spinnaker.orca.igor.pipeline;

import com.netflix.spinnaker.orca.CancellableStage;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.igor.tasks.MonitorAwsCodeBuildTask;
import com.netflix.spinnaker.orca.igor.tasks.StartAwsCodeBuildTask;
import com.netflix.spinnaker.orca.igor.tasks.StopAwsCodeBuildTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AwsCodeBuildStage implements StageDefinitionBuilder, CancellableStage {
  private final StopAwsCodeBuildTask stopAwsCodeBuildTask;

  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    builder
        .withTask("startAwsCodeBuildTask", StartAwsCodeBuildTask.class)
        .withTask("monitorAwsCodeBuildTask", MonitorAwsCodeBuildTask.class);
  }

  @Override
  public Result cancel(Stage stage) {
    log.info(
        String.format(
            "Cancelling stage (stageId: %s, executionId: %s context: %s)",
            stage.getId(), stage.getExecution().getId(), stage.getContext()));

    try {
      TaskResult result = stopAwsCodeBuildTask.execute(stage);
      Map<String, Object> context = new HashMap<>();
      context.put("buildInfo", result.getContext().get("buildInfo"));
      stage.setContext(context);
    } catch (Exception e) {
      log.error(
          String.format(
              "Failed to cancel stage (stageId: %s, executionId: %s), e: %s",
              stage.getId(), stage.getExecution().getId(), e.getMessage()),
          e);
    }
    return new Result(stage, stage.getContext());
  }
}
