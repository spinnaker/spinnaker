/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.bake;

import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.HasServiceSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface BakeService<T> extends HasServiceSettings<T> {
  String getSpinnakerStagingPath(String deploymentName);

  String installArtifactCommand(DeploymentDetails deploymentDetails);

  StartupPriority getPriority();

  String getStartupCommand();

  default String stageStartupScripts(
      DeploymentDetails details, GenerateService.ResolvedConfiguration resolvedConfiguration) {
    Map<String, Profile> profiles =
        resolvedConfiguration.getProfilesForService(getService().getType());

    List<String> allCommands = new ArrayList<>();
    for (Map.Entry<String, Profile> entry : profiles.entrySet()) {
      Profile profile = entry.getValue();
      String source = profile.getStagedFile(getSpinnakerStagingPath(details.getDeploymentName()));
      String dest = profile.getOutputFile();
      allCommands.add(String.format("mkdir -p $(dirname %s)", dest));
      allCommands.add(String.format("cp -p %s %s", source, dest));

      if (profile.isExecutable()) {
        allCommands.add(String.format("chmod +x %s", dest));
      }
    }

    return String.join("\n", allCommands);
  }

  class StartupPriority {
    public static final int LOW = 0;
    public static final int MODERATE = 5;
    public static final int HIGH = 10;

    final Integer priority;

    public StartupPriority(Integer priority) {
      this.priority = priority;
    }

    public int compareTo(StartupPriority other) {
      return this.priority.compareTo(other.priority);
    }
  }
}
