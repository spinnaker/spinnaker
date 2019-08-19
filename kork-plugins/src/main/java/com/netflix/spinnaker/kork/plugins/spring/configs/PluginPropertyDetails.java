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

package com.netflix.spinnaker.kork.plugins.spring.configs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.kork.plugins.spring.MalformedPluginConfigurationException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PluginPropertyDetails {

  @JsonProperty("pluginConfigurations")
  List<PluginConfiguration> pluginConfigurations;

  @JsonProperty("downloadingEnabled")
  public Boolean downloadingEnabled;

  public void validate() {
    validateUniquePlugins();
    for (PluginConfiguration pluginConfiguration : getPluginConfigurations()) {
      pluginConfiguration.validate();
    }
  }

  public void validateUniquePlugins() {

    List<String> pluginNames =
        getPluginConfigurations().stream()
            .map(PluginConfiguration::getName)
            .collect(Collectors.toList());

    List<String> duplicates =
        pluginNames.stream()
            .filter(e -> Collections.frequency(pluginNames, e) > 1)
            .distinct()
            .collect(Collectors.toList());
    if (duplicates.size() > 0) {
      throw new MalformedPluginConfigurationException(
          String.format("The following plugins have been defined multiple times: %s", duplicates));
    }
  }
}
