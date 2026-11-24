/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package preconfiguredjob;

import com.netflix.spinnaker.kork.plugins.api.PluginSdks;
import com.netflix.spinnaker.kork.plugins.api.yaml.YamlResourceLoader;
import com.netflix.spinnaker.orca.api.preconfigured.jobs.PreconfiguredJobConfigurationProvider;
import com.netflix.spinnaker.orca.api.preconfigured.jobs.PreconfiguredJobStageProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.pf4j.Extension;

/**
 * An example of a preconfigured job stage provided by a plugin.
 *
 * <p>The plugin defines its own {@link PreconfiguredJobStageProperties} subclass describing the job
 * to run (here, a Kubernetes Job manifest) and loads its configuration through the Config SDK from
 * a YAML file packaged with the plugin itself.
 */
@Extension
public class PreconfiguredJobStageSample implements PreconfiguredJobConfigurationProvider {
  private final PluginSdks pluginSdks;

  public PreconfiguredJobStageSample(PluginSdks pluginSdks) {
    this.pluginSdks = pluginSdks;
  }

  @Override
  public List<? extends PreconfiguredJobStageProperties> getJobConfigurations() {
    List<ManifestJobProperties> preconfiguredJobProperties = new ArrayList<>();

    YamlResourceLoader yamlResourceLoader = pluginSdks.yamlResourceLoader();
    preconfiguredJobProperties.add(
        yamlResourceLoader.loadResource("publish-assets.yml", ManifestJobProperties.class));

    return preconfiguredJobProperties;
  }

  public static class ManifestJobProperties extends PreconfiguredJobStageProperties {
    private Map<String, Object> manifest = new HashMap<>();

    public Map<String, Object> getManifest() {
      return manifest;
    }

    public void setManifest(Map<String, Object> manifest) {
      this.manifest = manifest;
    }

    @Override
    public List<String> getOverridableFields() {
      List<String> overridableFields = new ArrayList<>(List.of("manifest"));
      overridableFields.addAll(super.getOverridableFields());
      return overridableFields;
    }

    @Override
    public boolean isValid() {
      return super.isValid() && !manifest.isEmpty();
    }
  }
}
