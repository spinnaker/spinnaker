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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.deck.DeckDockerProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.DeckService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.DistributedService.DeployPriority;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Delegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Data
@Component
@EqualsAndHashCode(callSuper = true)
public class KubernetesV2DeckService extends DeckService
    implements KubernetesV2Service<DeckService.Deck> {
  final DeployPriority deployPriority = new DeployPriority(0);

  @Delegate @Autowired KubernetesV2ServiceDelegate serviceDelegate;

  @Autowired DeckDockerProfileFactory deckDockerProfileFactory;

  private final String settingsPath = "/opt/spinnaker/config";
  private final String settingsJs = "settings.js";
  private final String settingsJsLocal = "settings-local.js";

  @Override
  public ServiceSettings defaultServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    return new Settings(deploymentConfiguration.getSecurity().getUiSecurity());
  }

  @Override
  public boolean runsOnJvm() {
    return false;
  }

  @Override
  protected Optional<String> customProfileOutputPath(String profileName) {
    if (profileName.equals(settingsJs) || profileName.equals(settingsJsLocal)) {
      return Optional.of(Paths.get(settingsPath, profileName).toString());
    } else {
      return Optional.empty();
    }
  }

  @Override
  public List<Profile> getProfiles(
      DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> result = new ArrayList<>();
    String path = Paths.get(settingsPath, settingsJs).toString();
    result.add(
        deckDockerProfileFactory.getProfile(settingsJs, path, deploymentConfiguration, endpoints));
    return result;
  }

  @Override
  public Optional<String> buildAddress(String namespace) {
    return Optional.empty();
  }
}
