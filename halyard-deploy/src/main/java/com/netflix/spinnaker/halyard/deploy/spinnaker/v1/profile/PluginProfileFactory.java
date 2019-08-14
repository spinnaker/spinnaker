/*
 * Copyright 2019 Armory, Inc.
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
import com.netflix.spinnaker.halyard.config.model.v1.node.Plugins;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.Manifest;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.Plugin;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PluginProfileFactory extends StringBackedProfileFactory {
  @Override
  protected void setProfile(
      Profile profile,
      DeploymentConfiguration deploymentConfiguration,
      SpinnakerRuntimeSettings endpoints) {
    final Plugins plugins = deploymentConfiguration.getPlugins();

    Map<String, List<Map<String, Object>>> fullyRenderedYaml = new HashMap<>();

    List<Map<String, Object>> pluginMetadata =
        plugins.getPlugins().stream()
            .filter(p -> p.getEnabled())
            .filter(p -> !p.getManifestLocation().isEmpty())
            .map(p -> composeMetadata(p, p.generateManifest()))
            .collect(Collectors.toList());

    fullyRenderedYaml.put("plugins", pluginMetadata);

    profile.appendContents(
        yamlToString(deploymentConfiguration.getName(), profile, fullyRenderedYaml));
  }

  private Map<String, Object> composeMetadata(Plugin plugin, Manifest manifest) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("enabled", plugin.getEnabled());
    metadata.put("name", manifest.getName());
    metadata.put("jars", manifest.getJars());
    metadata.put("manifestVersion", manifest.getManifestVersion());
    return metadata;
  }

  @Override
  protected String getRawBaseProfile() {
    return "";
  }

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.SPINNAKER;
  }

  @Override
  protected String commentPrefix() {
    return "## ";
  }
}
