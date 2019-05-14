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

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.igor.ConcourseService;
import com.netflix.spinnaker.orca.igor.model.ConcourseStageExecution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WaitForConcourseJobStartTask implements OverridableTimeoutRetryableTask {
  private final ConcourseService concourseService;

  private final long backoffPeriod = 15000;
  private final long timeout = TimeUnit.HOURS.toMillis(1);

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    ConcourseStageExecution stageExecution = concourseService.popExecution(stage);
    if (stageExecution != null) {
      Map<String, Object> context = new HashMap<>();
      context.put("jobName", stageExecution.getJobName());
      context.put("buildNumber", stageExecution.getBuildNumber());
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(context).build();
    }
    return TaskResult.RUNNING;
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
