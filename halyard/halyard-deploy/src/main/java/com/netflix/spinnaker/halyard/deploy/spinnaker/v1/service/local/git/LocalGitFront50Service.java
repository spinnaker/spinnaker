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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.git;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.Front50Service;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public class LocalGitFront50Service extends Front50Service
    implements LocalGitService<Front50Service.Front50> {
  String startCommand = "./gradlew";

  @Autowired String gitRoot;

  @Autowired ArtifactService artifactService;

  @Override
  protected String getConfigOutputPath() {
    return "~/.spinnaker";
  }

  @Override
  public List<Profile> getProfiles(
      DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> profiles = super.getProfiles(deploymentConfiguration, endpoints);
    generateAwsProfile(deploymentConfiguration, endpoints, getHomeDirectory())
        .ifPresent(p -> profiles.add(p));
    return profiles;
  }

  @Override
  public ServiceSettings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    return new Settings()
        .setArtifactId(getArtifactId(deploymentConfiguration.getName()))
        .setHost(getDefaultHost())
        .setEnabled(true);
  }

  public String getArtifactId(String deploymentName) {
    return LocalGitService.super.getArtifactId(deploymentName);
  }

  @Override
  public void collectLogs(DeploymentDetails details, SpinnakerRuntimeSettings runtimeSettings) {
    throw new UnsupportedOperationException();
  }
}
