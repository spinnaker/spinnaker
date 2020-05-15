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

import com.netflix.spinnaker.kork.plugins.CanonicalPluginId;
import com.netflix.spinnaker.kork.plugins.VersionRequirementsParser;
import com.netflix.spinnaker.kork.plugins.VersionRequirementsParser.VersionRequirements;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.model.PluginInfo;
import java.util.*;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ExtractRequiredPluginDependenciesTask implements Task {

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    final PluginInfo.Release release = stage.mapTo("/release", PluginInfo.Release.class);

    List<String> services = new ArrayList<>();
    List<String> plugins = new ArrayList<>();

    VersionRequirementsParser.INSTANCE
        .parseAll(release.getRequires())
        .forEach(
            requirements -> {
              if (isServiceRequirement(requirements)) {
                services.add(requirements.getService());
              } else {
                plugins.add(requirements.getService());
              }
            });

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("requiredServices", services);
    outputs.put("requiredPlugins", plugins);

    services.forEach(
        service -> outputs.put(format("requires%s", StringUtils.capitalize(service)), true));

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).outputs(outputs).build();
  }

  /**
   * If the service is not a valid plugin ID, then we can assume it is indeed a service and not a
   * plugin dependency.
   */
  private static boolean isServiceRequirement(VersionRequirements requirements) {
    return !CanonicalPluginId.Companion.isValid(requirements.getService());
  }
}
