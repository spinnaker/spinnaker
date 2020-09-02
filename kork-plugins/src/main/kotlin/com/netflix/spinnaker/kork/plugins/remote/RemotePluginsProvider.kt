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

package com.netflix.spinnaker.kork.plugins.remote

import com.netflix.spinnaker.kork.annotations.Beta
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.plugins.remote.extension.RemoteExtension

/**
 * Provides remote plugins based on selected criteria - currently by plugin ID or remote extension
 * type.
 */
@Beta
class RemotePluginsProvider(
  private val remotePluginsCache: RemotePluginsCache
) {

  /**
   * Get a [RemotePlugin] by its [pluginId].
   */
  fun getById(pluginId: String): RemotePlugin {
    val plugin = remotePluginsCache.get(pluginId)

    if (plugin != null) {
      return plugin
    } else {
      throw RemotePluginNotFoundException(pluginId)
    }
  }

  /**
   * Get a list of [RemotePlugin] that have extensions implementing the given [type].
   */
  fun getByExtensionType(type: String): List<RemotePlugin> {
    val plugins: MutableList<RemotePlugin> = mutableListOf()

    remotePluginsCache.getAll().forEach { pluginEntry ->
      if (pluginEntry.value.remoteExtensions.find { it.type == type } != null) {
        plugins.add(pluginEntry.value)
      }
    }

    return plugins
  }

  /** Return remote extensions by type. */
  fun getExtensionsByType(type: String): List<RemoteExtension> {
    val extensions: MutableList<RemoteExtension> = mutableListOf()

    remotePluginsCache.getAll().forEach { pluginEntry ->
      extensions.addAll(pluginEntry.value.remoteExtensions.filter { it.type == type })
    }

    return extensions
  }
}

/**
 * Thrown when a remote plugin is not found.
 */
class RemotePluginNotFoundException(
  pluginId: String
) : IntegrationException("Remote plugin '{}' not found.", pluginId)
