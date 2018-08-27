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
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import lombok.Data;

public class SpringProfileFactory extends RegistryBackedProfileFactory {
  @Override
  public SpinnakerArtifact getArtifact() {
    return null;
  }

  @Override
  protected void setProfile(Profile profile, DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    SpectatorConfig spectatorConfig = new SpectatorConfig();
    spectatorConfig
        .getSpectator()
        .getWebEndpoint()
        .setEnabled(deploymentConfiguration.getMetricStores().isEnabled());

    profile.appendContents(yamlToString(spectatorConfig));
  }

  @Override
  public String commentPrefix() {
    return "## ";
  }

  @Data
  private static class SpectatorConfig {
    Spectator spectator = new Spectator();
  }

  @Data
  private static class Spectator {
    String applicationName = "${spring.application.name}";
    WebEndpoint webEndpoint = new WebEndpoint();
  }

  @Data
  private static class WebEndpoint {
    boolean enabled;
  }

  @Data
  static class SpringProfileConfig {
    ServerConfig server;
    SecurityConfig security;

    SpringProfileConfig(ServiceSettings settings) {
      this.server = new ServerConfig(settings);
      this.security = new SecurityConfig(settings);
    }
  }
}
