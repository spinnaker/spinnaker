/*
 * Copyright 2018 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.deck.DeckDockerProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.DeckService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.HasServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.DistributedLogCollector;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Delegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
@Component
@Data
public class KubernetesV1DeckService extends DeckService implements KubernetesV1DistributedService<DeckService.Deck> {
  @Delegate
  @Autowired
  KubernetesV1DistributedServiceDelegate distributedServiceDelegate;

  @Autowired
  DeckDockerProfileFactory deckDockerProfileFactory;

  private final String settingsPath = "/opt/spinnaker/config";

  @Delegate(excludes = HasServiceSettings.class)
  public DistributedLogCollector getLogCollector() {
    return getLogCollectorFactory().build(this);
  }

  @Override
  public Settings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    KubernetesV1SharedServiceSettings kubernetesV1SharedServiceSettings = new KubernetesV1SharedServiceSettings(deploymentConfiguration);
    Settings settings = new Settings(deploymentConfiguration.getSecurity().getUiSecurity());
    settings.setArtifactId(getArtifactId(deploymentConfiguration.getName()))
        .setLocation(kubernetesV1SharedServiceSettings.getDeployLocation())
        .setEnabled(true);
    return settings;
  }

  public String getArtifactId(String deploymentName) {
    return KubernetesV1DistributedService.super.getArtifactId(deploymentName);
  }

  @Override
  protected Optional<String> customProfileOutputPath(String profileName) {
    if (profileName.equals("settings.js") || profileName.equals("settings-local.js")) {
      return Optional.of(Paths.get(settingsPath, profileName).toString());
    } else {
      return Optional.empty();
    }
  }

  @Override
  public List<Profile> getProfiles(DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> result = new ArrayList<>();
    String filename = "settings.js";
    String path = Paths.get(settingsPath, filename).toString();
    result.add(deckDockerProfileFactory.getProfile(filename, path, deploymentConfiguration, endpoints));
    return result;
  }

  final DeployPriority deployPriority = new DeployPriority(0);
  final boolean requiredToBootstrap = false;
}
