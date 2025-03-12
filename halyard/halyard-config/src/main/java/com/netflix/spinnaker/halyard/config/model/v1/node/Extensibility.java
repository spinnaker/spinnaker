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

package com.netflix.spinnaker.halyard.config.model.v1.node;

import com.netflix.spinnaker.halyard.config.model.v1.plugins.Plugin;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.PluginRepository;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class Extensibility extends Node {
  private Map<String, Plugin> plugins = new HashMap<>();
  private Map<String, PluginRepository> repositories = new HashMap<>();

  @Override
  public String getNodeName() {
    return "extensibility";
  }

  public Map<String, Object> repositoriesConfig() {
    Map<String, Object> repositoriesYaml = new LinkedHashMap<>();
    for (Map.Entry<String, PluginRepository> entry : repositories.entrySet()) {
      repositoriesYaml.put(entry.getKey(), entry.getValue().toMap());
    }
    return repositoriesYaml;
  }

  public Map<String, Object> pluginsConfig() {
    Map<String, Object> pluginsYaml = new LinkedHashMap<>();
    for (Map.Entry<String, Plugin> entry : plugins.entrySet()) {
      pluginsYaml.put(entry.getKey(), entry.getValue().toMap());
    }
    return pluginsYaml;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> extensibilityYaml = new LinkedHashMap<>();
    extensibilityYaml.put("plugins", pluginsConfig());
    extensibilityYaml.put("repositories", repositoriesConfig());
    return extensibilityYaml;
  }
}
