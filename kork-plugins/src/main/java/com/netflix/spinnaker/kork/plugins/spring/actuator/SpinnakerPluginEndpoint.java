/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.kork.plugins.spring.actuator;

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.List;
import java.util.stream.Collectors;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginWrapper;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

@Endpoint(id = "plugins")
public class SpinnakerPluginEndpoint {

  private final SpinnakerPluginManager pluginManager;

  public SpinnakerPluginEndpoint(SpinnakerPluginManager pluginManager) {
    this.pluginManager = pluginManager;
  }

  @ReadOperation
  public List<PluginDescriptor> plugins() {
    return pluginManager.getPlugins().stream()
        .map(PluginWrapper::getDescriptor)
        .collect(Collectors.toList());
  }

  @ReadOperation
  public PluginDescriptor pluginById(@Selector String pluginId) {
    PluginWrapper pluginWrapper = pluginManager.getPlugin(pluginId);
    if (pluginWrapper == null) {
      throw new NotFoundException("Plugin not found: " + pluginId);
    }
    return pluginWrapper.getDescriptor();
  }
}
