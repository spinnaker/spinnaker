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

import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.Front50Service;
import java.time.Duration;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SetPreferredPluginReleaseTask implements RetryableTask {

  private static final Logger log = LoggerFactory.getLogger(SetPreferredPluginReleaseTask.class);

  private final Front50Service front50Service;

  public SetPreferredPluginReleaseTask(Front50Service front50Service) {
    this.front50Service = front50Service;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    final String pluginId = (String) stage.getContext().get("pluginId");
    final String version = (String) stage.getContext().get("preferredVersion");

    Objects.requireNonNull(pluginId);
    Objects.requireNonNull(version);

    try {
      front50Service.setPreferredPluginVersion(pluginId, version, true);
    } catch (Exception e) {
      log.error("Failed setting preferred plugin version '{}' to '{}'", pluginId, version, e);
      return TaskResult.RUNNING;
    }

    return TaskResult.SUCCEEDED;
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
