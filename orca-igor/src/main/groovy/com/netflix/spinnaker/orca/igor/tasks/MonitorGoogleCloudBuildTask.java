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

import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.igor.IgorService;
import com.netflix.spinnaker.orca.igor.model.GoogleCloudBuild;
import com.netflix.spinnaker.orca.igor.model.GoogleCloudBuildStageDefinition;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class MonitorGoogleCloudBuildTask extends RetryableIgorTask<GoogleCloudBuildStageDefinition> implements OverridableTimeoutRetryableTask {
  @Getter
  protected long backoffPeriod = 10000;
  @Getter
  protected long timeout = TimeUnit.HOURS.toMillis(2);

  private final IgorService igorService;

  @Override
  @Nonnull
  public TaskResult tryExecute(@Nonnull GoogleCloudBuildStageDefinition stageDefinition) {
    GoogleCloudBuild build = igorService.getGoogleCloudBuild(
      stageDefinition.getAccount(),
      stageDefinition.getBuildInfo().getId()
    );
    Map<String, Object> context = new HashMap<>();
    context.put("buildInfo", build);
    return TaskResult.builder(build.getStatus().getExecutionStatus()).context(context).build();
  }

  @Override
  @Nonnull
  protected GoogleCloudBuildStageDefinition mapStage(@Nonnull Stage stage) {
    return stage.mapTo(GoogleCloudBuildStageDefinition.class);
  }
}
