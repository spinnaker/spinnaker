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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.core.job.v1.JobExecutor;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.DeckDockerProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.DeckService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceInterfaceFactory;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Component
@Data
public class KubernetesDeckService extends DeckService implements KubernetesDistributedService<DeckService.Deck> {
  @Autowired
  private String dockerRegistry;

  @Autowired
  KubernetesMonitoringDaemonService monitoringDaemonService;

  @Autowired
  DeckDockerProfileFactory deckDockerProfileFactory;

  @Autowired
  ArtifactService artifactService;

  @Autowired
  ServiceInterfaceFactory serviceInterfaceFactory;

  @Override
  public Settings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    Settings settings = new Settings(deploymentConfiguration.getSecurity().getUiSecurity());
    settings.setArtifactId(getArtifactId(deploymentConfiguration.getName()))
        .setEnabled(true);
    return settings;
  }

  public String getArtifactId(String deploymentName) {
    return KubernetesDistributedService.super.getArtifactId(deploymentName);
  }

  @Override
  public List<Profile> getProfiles(DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> result = new ArrayList<>();
    String settingsPath = "/opt/spinnaker/config";
    String filename = "settings.js";
    String path = Paths.get(settingsPath, filename).toString();
    result.add(deckDockerProfileFactory.getProfile(filename, path, deploymentConfiguration, endpoints));
    return result;
  }

  final DeployPriority deployPriority = new DeployPriority(0);
  final boolean requiredToBootstrap = false;
}
