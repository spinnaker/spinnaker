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
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import lombok.Data;

abstract public class SpringProfile extends SpinnakerProfile {
  @Override
  public String getProfileFileName() {
    String name = getArtifact().getName();
    String extension = getProfileExtension() ;
    if (extension != null) {
      name = name + "-" + extension;
    }
    return name + ".yml";
  }

  protected String getProfileExtension() {
    return null;
  }

  @Override
  public ProfileConfig generateFullConfig(ProfileConfig config, DeploymentConfiguration deploymentConfiguration, SpinnakerEndpoints endpoints) {
    SpectatorConfig spectatorConfig = new SpectatorConfig();
    spectatorConfig
        .getSpectator()
        .getWebEndpoint()
        .setEnabled(deploymentConfiguration.getMetricStores().isEnabled());

    String primaryConfig = config.getPrimaryConfigFile();
    config.extendConfig(primaryConfig, yamlToString(spectatorConfig));
    return config;
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

  @Override
  public String commentPrefix() {
    return "## ";
  }

  @Data
  static class SpringProfileConfig {
    ServerConfig server;
    SpringConfig spring;

    SpringProfileConfig(SpinnakerService service) {
      server = new ServerConfig(service);
    }
  }
}
