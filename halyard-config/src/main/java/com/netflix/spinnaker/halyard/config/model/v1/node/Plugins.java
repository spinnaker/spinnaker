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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.Plugin;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties({"pluginConfigurations"})
public class Plugins extends Node {
  private List<Plugin> plugins = new ArrayList<>();
  private boolean enabled;
  private boolean downloadingEnabled;

  @Override
  public String getNodeName() {
    return "plugins";
  }

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeListIterator(
        plugins.stream().map(a -> (Node) a).collect(Collectors.toList()));
  }

  public Map<String, Object> pluginConfigurations() {
    Map<String, Object> fullyRenderedYaml = new LinkedHashMap<>();
    Map<String, Object> pluginMetadata =
        plugins.stream()
            .filter(p -> p.getEnabled())
            .filter(p -> !p.getManifestLocation().isEmpty())
            .collect(
                Collectors.toMap(p -> p.generateManifest().getName(), p -> p.getCombinedOptions()));
    fullyRenderedYaml.put("plugins", pluginMetadata);
    return fullyRenderedYaml;
  }
}
