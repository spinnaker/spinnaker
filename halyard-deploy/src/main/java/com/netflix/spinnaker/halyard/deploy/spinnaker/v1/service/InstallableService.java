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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service;

import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public interface InstallableService<T> extends HasServiceSettings<T> {
  String getSpinnakerStagingPath();
  String installArtifactCommand(DeploymentDetails deploymentDetails);

  default void stageProfiles(GenerateService.ResolvedConfiguration resolvedConfiguration) {
    Map<String, Profile> profiles = resolvedConfiguration.getServiceProfiles().get(getService().getType());
    
    for (Map.Entry<String, Profile> entry : profiles.entrySet()) {
      Profile profile = entry.getValue();
      Path source = Paths.get(profile.getStagedFile(getSpinnakerStagingPath()));
      Path dest = Paths.get(profile.getOutputFile());
      dest.toFile().getParentFile().mkdirs();
      try {
        Files.copy(source, dest, REPLACE_EXISTING);
      } catch (IOException e) {
        throw new HalException(Problem.Severity.FATAL, "Failed to copy required profile: " + e.getMessage());
      }
    }
  }
}
