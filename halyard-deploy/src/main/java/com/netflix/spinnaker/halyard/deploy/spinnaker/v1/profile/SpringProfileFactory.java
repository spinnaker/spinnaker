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
import com.netflix.spinnaker.halyard.config.services.v1.VersionsService;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;

public class SpringProfileFactory extends RegistryBackedProfileFactory {
  @Override
  public SpinnakerArtifact getArtifact() {
    return null;
  }

  @Autowired VersionsService versionsService;

  @Override
  protected void setProfile(
      Profile profile,
      DeploymentConfiguration deploymentConfiguration,
      SpinnakerRuntimeSettings endpoints) {
    SpectatorConfig spectatorConfig = new SpectatorConfig();
    spectatorConfig
        .getSpectator()
        .getWebEndpoint()
        .setEnabled(deploymentConfiguration.getMetricStores().isEnabled());

    profile.appendContents(
        yamlToString(deploymentConfiguration.getName(), profile, spectatorConfig));

    if (addExtensibilityConfigs(deploymentConfiguration)) {
      profile.appendContents(
          yamlToString(
              deploymentConfiguration.getName(),
              profile,
              getSpinnakerYaml(deploymentConfiguration)));
    }
  }

  protected Map<String, Map<String, Object>> getSpinnakerYaml(
      DeploymentConfiguration deploymentConfiguration) {
    Map<String, Map<String, Object>> spinnakerYaml = new LinkedHashMap<>();
    Map<String, Object> extensibilityYaml = new LinkedHashMap<>();
    Map<String, Object> extensibilityContents =
        deploymentConfiguration.getSpinnaker().getExtensibility().toMap();
    extensibilityContents.put(
        "plugins-root-path", "/opt/" + this.getArtifact().toString().toLowerCase() + "/plugins");
    extensibilityContents.put("strict-plugin-loading", false);
    extensibilityYaml.put("extensibility", extensibilityContents);
    spinnakerYaml.put("spinnaker", extensibilityYaml);
    return spinnakerYaml;
  }

  protected boolean spinnakerVersionSupportsPlugins(String version) {
    String[] versionParts = version.split("-");
    if (versionParts.length == 1) {
      return Versions.greaterThanEqual(version, concreteReleaseWithPlugins());
    } else if (versionParts[0].equals("master")) {
      return pluginsDateCheck(versionParts[1]);
    } else if (versionParts[0].equals("release")
        && versionParts.length >= 3
        && Versions.greaterThanEqual(versionParts[1].replace("x", "0"), baseReleaseWithPlugins())) {
      return pluginsDateCheck(versionParts[2]);
    }
    return false;
  }

  protected String baseReleaseWithPlugins() {
    return "1.19.0";
  }

  protected String concreteReleaseWithPlugins() {
    return "1.19.4";
  }

  private boolean pluginsDateCheck(String dateOrLatest) {
    return dateOrLatest.equals("latest") || dateOrLatest.compareTo("20200403040016") > 0;
  }

  protected boolean addExtensibilityConfigs(DeploymentConfiguration deploymentConfiguration) {
    return spinnakerVersionSupportsPlugins(deploymentConfiguration.getVersion());
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
