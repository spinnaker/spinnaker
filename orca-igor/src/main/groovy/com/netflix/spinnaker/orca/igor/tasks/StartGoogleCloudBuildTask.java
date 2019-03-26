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

import com.google.api.services.cloudbuild.v1.model.Operation;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.igor.IgorService;
import com.netflix.spinnaker.orca.igor.model.GoogleCloudBuildStageDefinition;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

@Component
@RequiredArgsConstructor
public class StartGoogleCloudBuildTask implements Task {
  private final IgorService igorService;

  @Override
  @Nonnull public TaskResult execute(@Nonnull Stage stage) {
    GoogleCloudBuildStageDefinition stageDefinition = stage.mapTo(GoogleCloudBuildStageDefinition.class);
    Operation result = igorService.createGoogleCloudBuild(stageDefinition.getAccount(), stageDefinition.getBuildDefinition());
    return new TaskResult(ExecutionStatus.SUCCEEDED);
  }
}
