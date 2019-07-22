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
package com.netflix.spinnaker.kork.plugins.spring;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PluginProperties {

  @JsonProperty("plugins")
  List<PluginConfiguration> pluginConfigurationList;

  public void validate() {
    validateUniquePlugins();
    for (PluginConfiguration pluginConfiguration : pluginConfigurationList) {
      pluginConfiguration.validate();
    }
  }

  public void validateUniquePlugins() {

    List<String> pluginNames =
        pluginConfigurationList.stream()
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

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class PluginConfiguration {

    public String name;
    List<String> jars;
    public boolean enabled;

    static final String regex = "^[a-zA-Z0-9]+\\/[\\w-]+$";
    static final Pattern pattern = Pattern.compile(regex);

    public void validate() {
      Matcher matcher = pattern.matcher(name);
      if (!matcher.find()) {
        throw new MalformedPluginConfigurationException(
            String.format("Invalid plugin name: %s", name));
      }
    }

    @Override
    public String toString() {
      return new StringBuilder()
          .append("Plugin: " + name + ", ")
          .append("Jars: " + String.join(", ", jars))
          .toString();
    }
  }
}
