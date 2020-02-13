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
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor
import com.netflix.spinnaker.kork.plugins.api.ConfigurableExtension
import com.netflix.spinnaker.kork.plugins.api.SpinnakerExtension
import org.pf4j.PluginWrapper
import org.springframework.core.ResolvableType

/**
 * Provides an extension with a typed configuration class object.
 */
class ExtensionConfigFactory(
  private val configResolver: ConfigResolver
) {
  /**
   * Checks if the given [extensionClass] and [candidate] param type is supported (e.g. is [candidate] a config
   * class that can be injected into the extension?)
   */
  fun supports(extensionClass: Class<*>, candidate: Class<*>): Boolean =
    getConfigurationClass(extensionClass).first == candidate

  /**
   * Provide the configuration for the given [extensionClass] and [pluginWrapper].
   */
  fun provide(extensionClass: Class<*>, pluginWrapper: PluginWrapper?): Any? {
    val annot = extensionClass.getAnnotation(SpinnakerExtension::class.java)

    val coordinates = pluginWrapper
      ?.getCoordinates()
      ?.let { PluginConfigCoordinates(it.id, annot.id) }
      ?: SystemExtensionConfigCoordinates(annot.id)

    val configClass = getConfigurationClass(extensionClass)

    return configClass.first
      ?.let { configResolver.resolve(coordinates, it) }
      ?: throw SystemException("Could not find configuration class " +
        "'${configClass.second}' for extension: ${extensionClass.name}")
  }

  /**
   * Returns the configuration class and a string representation of the config class.
   */
  private fun getConfigurationClass(extensionClass: Class<*>): Pair<Class<*>?, String> {
    return ResolvableType.forClass(extensionClass)
      .apply { resolve() }
      .let { resolvedType ->
        // The ConfigurableExtension interface may be on a supertype, so go find it
        val parentTypes = listOf<ResolvableType>(resolvedType.superType) + resolvedType.interfaces
        val configClass = parentTypes.find { ConfigurableExtension::class.java.isAssignableFrom(it.rawClass!!) }
          ?.let {
            it.resolve()
            it.getGeneric(0).rawClass
          }

        Pair(configClass, resolvedType.getGeneric(0).toString())
      }
  }

  private fun PluginWrapper.getCoordinates(): PluginCoordinates =
    (descriptor as SpinnakerPluginDescriptor).let { PluginCoordinates(it.pluginId) }

  private inner class PluginCoordinates(
    val id: String
  )
}
