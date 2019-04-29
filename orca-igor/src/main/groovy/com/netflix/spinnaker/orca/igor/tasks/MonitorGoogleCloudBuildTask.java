/*
 * Copyright 2019 Google, Inc.
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
import com.netflix.spinnaker.orca.igor.model.GoogleCloudBuild;
import com.netflix.spinnaker.orca.igor.model.GoogleCloudBuildStageDefinition;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

import javax.annotation.Nonnull;

@Component
@RequiredArgsConstructor
@Slf4j
public class MonitorGoogleCloudBuildTask implements Task {
  private final IgorService igorService;

  @Override
  @Nonnull public TaskResult execute(@Nonnull Stage stage) {
    GoogleCloudBuildStageDefinition stageDefinition = stage.mapTo(GoogleCloudBuildStageDefinition.class);
    try {
      GoogleCloudBuild build = igorService.getGoogleCloudBuild(
        stageDefinition.getAccount(),
        stageDefinition.getBuildInfo().getId()
      );
      return new TaskResult(build.getStatus().getExecutionStatus());
    } catch (RetrofitError e) {
      // Log and retry the task
      log.info("Error fetching Google Cloud Build status from igor: {}", e.getMessage());
      return new TaskResult(ExecutionStatus.RUNNING);
    }
  }
}
