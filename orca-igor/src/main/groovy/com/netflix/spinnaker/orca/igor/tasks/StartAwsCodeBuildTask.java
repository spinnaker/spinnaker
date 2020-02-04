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

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.igor.IgorService;
import com.netflix.spinnaker.orca.igor.model.AwsCodeBuildExecution;
import com.netflix.spinnaker.orca.igor.model.AwsCodeBuildStageDefinition;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StartAwsCodeBuildTask implements Task {
  private static final String PROJECT_NAME = "projectName";

  private final IgorService igorService;

  @Override
  @Nonnull
  public TaskResult execute(@Nonnull Stage stage) {
    AwsCodeBuildStageDefinition stageDefinition = stage.mapTo(AwsCodeBuildStageDefinition.class);

    Map<String, Object> requestInput = new HashMap<>();
    requestInput.put(PROJECT_NAME, stageDefinition.getProjectName());

    AwsCodeBuildExecution execution =
        igorService.startAwsCodeBuild(stageDefinition.getAccount(), requestInput);

    Map<String, Object> context = stage.getContext();
    context.put("buildInfo", execution);
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(context).build();
  }
}
