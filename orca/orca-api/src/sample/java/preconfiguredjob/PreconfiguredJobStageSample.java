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
import com.netflix.spinnaker.orca.api.preconfigured.jobs.TitusPreconfiguredJobProperties;
import java.util.ArrayList;
import java.util.List;
import org.pf4j.Extension;

/**
 * A (real life) example of a preconfigured job stage.
 *
 * <p>This stage is used within Netflix and runs on Titus. Treasure is a service for deploying and
 * serving static websites either internally or externally complying with Netflix security and
 * infrastructure paved road practices.
 *
 * <p>This example also exhibits usage of the Config SDK to load standard configuration through a
 * YAML file packaged with the plugin itself.
 */
@Extension
public class PreconfiguredJobStageSample implements PreconfiguredJobConfigurationProvider {
  private final PluginSdks pluginSdks;

  public PreconfiguredJobStageSample(PluginSdks pluginSdks) {
    this.pluginSdks = pluginSdks;
  }

  @Override
  public List<? extends PreconfiguredJobStageProperties> getJobConfigurations() {
    List<TitusPreconfiguredJobProperties> preconfiguredJobProperties = new ArrayList<>();

    YamlResourceLoader yamlResourceLoader = pluginSdks.yamlResourceLoader();
    TitusPreconfiguredJobProperties titusRunJobConfigProps =
        yamlResourceLoader.loadResource("treasure.yml", TitusPreconfiguredJobProperties.class);
    preconfiguredJobProperties.add(titusRunJobConfigProps);

    return preconfiguredJobProperties;
  }
}
