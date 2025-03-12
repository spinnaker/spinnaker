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

package com.netflix.spinnaker.halyard.config.model.v1.plugins;

import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PluginExtension extends Node {
  private String id;
  private Boolean enabled = true;
  private Map<String, Object> config = new HashMap<>();

  @Override
  public String getNodeName() {
    return id;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> pluginExtensionYaml = new LinkedHashMap<>();
    pluginExtensionYaml.put("enabled", enabled);
    pluginExtensionYaml.put("config", config);
    return pluginExtensionYaml;
  }
}
