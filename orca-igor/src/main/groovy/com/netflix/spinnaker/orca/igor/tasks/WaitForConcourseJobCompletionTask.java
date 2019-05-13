/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.igor.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.igor.BuildService;
import com.netflix.spinnaker.orca.igor.model.ConcourseStageDefinition;
import com.netflix.spinnaker.orca.pipeline.model.ConcourseBuildInfo;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class WaitForConcourseJobCompletionTask implements OverridableTimeoutRetryableTask {
  private final BuildService buildService;
  private final ObjectMapper mapper;

  private final long backoffPeriod = 15000;
  private final long timeout = TimeUnit.DAYS.toMillis(3);

  @Override
  @Nonnull
  public TaskResult execute(@Nonnull Stage stage) {
    ConcourseStageDefinition stageDefinition = stage.mapTo(ConcourseStageDefinition.class);
    String jobPath = stageDefinition.getTeamName() + "/" + stageDefinition.getPipelineName() + "/" +
      stage.getContext().get("jobName");

    Map<String, Object> buildMap = buildService
      .getBuild((Integer) stage.getContext().get("buildNumber"), stageDefinition.getMaster(), jobPath);

    ConcourseBuildInfo buildInfo = mapper.convertValue(buildMap, ConcourseBuildInfo.class);

    if("SUCCESS".equals(buildInfo.getResult())) {
      return TaskResult.SUCCEEDED;
    }
    else if("BUILDING".equals(buildInfo.getResult())) {
      return TaskResult.RUNNING;
    }
    throw new IllegalStateException("The Concourse job failed.");
  }

  @Override
  public long getBackoffPeriod() {
    return backoffPeriod;
  }

  @Override
  public long getTimeout() {
    return timeout;
  }
}
