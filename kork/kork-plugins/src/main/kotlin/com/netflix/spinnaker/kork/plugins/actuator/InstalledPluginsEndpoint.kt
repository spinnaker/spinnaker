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
 *
 */
package com.netflix.spinnaker.kork.plugins.actuator

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import java.util.stream.Collectors
import org.pf4j.PluginDescriptor
import org.pf4j.PluginWrapper
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.boot.actuate.endpoint.annotation.Selector

/**
 * An endpoint that exposes [PluginDescriptor] information about the service's installed plugins.
 */
@Endpoint(id = "installedPlugins")
class InstalledPluginsEndpoint(
  private val pluginManager: SpinnakerPluginManager
) {

  /**
   * Returns a list of all installed plugins' [PluginDescriptor].
   */
  @ReadOperation
  fun plugins(): List<PluginDescriptor> {
    return pluginManager.plugins.stream()
      .map { obj: PluginWrapper -> obj.descriptor }
      .collect(Collectors.toList())
  }

  /**
   * Returns the [PluginDescriptor] for a single plugin by [pluginId].
   */
  @ReadOperation
  fun pluginById(@Selector pluginId: String): PluginDescriptor {
    val pluginWrapper = pluginManager.getPlugin(pluginId) ?: throw NotFoundException("Plugin not found: $pluginId")
    return pluginWrapper.descriptor
  }
}
