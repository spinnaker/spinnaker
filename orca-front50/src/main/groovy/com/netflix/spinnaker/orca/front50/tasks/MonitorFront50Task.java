/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.front50.tasks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.discovery.converters.Auto;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class MonitorFront50Task implements RetryableTask {
  private final Front50Service front50Service;

  @Autowired
  public MonitorFront50Task(Optional<Front50Service> front50Service) {
    this.front50Service = front50Service.orElse(null);
  }

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(5);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.SECONDS.toMillis(90);
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    if (front50Service == null) {
      throw new UnsupportedOperationException("Front50 was not enabled. Fix this by setting front50.enabled: true");
    }

    StageData stageData = stage.mapTo(StageData.class);
    if (stageData.pipelineId != null) {
      try {
        Optional<Map<String, Object>> pipeline = front50Service
          .getPipelines(stageData.application)
          .stream()
          .filter(p -> stageData.pipelineId.equals(p.get("id")))
          .findFirst();

        if (!pipeline.isPresent()) {
          return TaskResult.SUCCEEDED;
        }

        Long lastModifiedTime = Long.valueOf(pipeline.get().get("updateTs").toString());
        return (lastModifiedTime > stage.getStartTime()) ? TaskResult.SUCCEEDED : TaskResult.RUNNING;
      } catch (Exception e) {
        return TaskResult.RUNNING;
      }
    }

    return new TaskResult(ExecutionStatus.SUCCEEDED);
  }

  private static class StageData {
    public String application;

    @JsonProperty("pipeline.id")
    public String pipelineId;
  }
}
