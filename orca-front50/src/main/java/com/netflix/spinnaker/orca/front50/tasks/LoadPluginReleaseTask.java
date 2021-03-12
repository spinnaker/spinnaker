/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.orca.front50.tasks;

import static java.lang.String.format;

import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.PluginInfo;
import java.time.Duration;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoadPluginReleaseTask implements RetryableTask {

  private static final Logger log = LoggerFactory.getLogger(LoadPluginReleaseTask.class);

  private final Front50Service front50Service;

  public LoadPluginReleaseTask(Front50Service front50Service) {
    this.front50Service = front50Service;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    final String pluginId = Objects.requireNonNull((String) stage.getContext().get("pluginId"));
    final String version = Objects.requireNonNull((String) stage.getContext().get("version"));

    PluginInfo pluginInfo;
    try {
      pluginInfo = front50Service.getPluginInfo(pluginId);
    } catch (Exception e) {
      log.error("Failed to retrieve plugin info for '{}'", pluginId, e);
      return TaskResult.RUNNING;
    }

    return pluginInfo.getReleases().stream()
        .filter(it -> it.getVersion().equals(version))
        .findFirst()
        .map(r -> TaskResult.builder(ExecutionStatus.SUCCEEDED).context("release", r).build())
        .orElse(
            TaskResult.builder(ExecutionStatus.TERMINAL)
                .output("message", format("No release found for version '%s'", version))
                .build());
  }

  @Override
  public long getBackoffPeriod() {
    return Duration.ofSeconds(10).toMillis();
  }

  @Override
  public long getTimeout() {
    return Duration.ofMinutes(2).toMillis();
  }
}
