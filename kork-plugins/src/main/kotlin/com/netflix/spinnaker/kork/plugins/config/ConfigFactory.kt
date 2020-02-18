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
package com.netflix.spinnaker.kork.plugins.config

import com.netflix.spinnaker.kork.exceptions.SystemException

/**
 * Provides an extension with a typed configuration class object.
 */
class ConfigFactory(
  private val configResolver: ConfigResolver
) {

  /**
   * Create the extension configuration given the [configClass], [extensionConfigId] and [pluginId].
   */
  fun createExtensionConfig(configClass: Class<*>, extensionConfigId: String, pluginId: String?): Any? {
    val coordinates = if (pluginId != null) {
      ExtensionConfigCoordinates(pluginId, extensionConfigId)
    } else {
      SystemExtensionConfigCoordinates(extensionConfigId)
    }
    return resolveConfiguration(coordinates, configClass)
  }

  /**
   * Create the plugin configuration given the [configClass] and [pluginId].
   */
  fun createPluginConfig(configClass: Class<*>, pluginId: String): Any? {
    val coordinates = PluginConfigCoordinates(pluginId)
    return resolveConfiguration(coordinates, configClass)
  }

  private fun resolveConfiguration(coordinates: ConfigCoordinates, configClass: Class<*>): Any? {
    return configClass
      .let { configResolver.resolve(coordinates, it) }
      ?: throw SystemException("Could not resolve configuration '${configClass.simpleName}' with " +
        "coordinates '${coordinates.toPointer()}'")
  }
}
