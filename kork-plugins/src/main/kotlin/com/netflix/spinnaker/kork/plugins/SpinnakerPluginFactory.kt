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
 */
package com.netflix.spinnaker.kork.plugins

import com.netflix.spinnaker.kork.plugins.config.ConfigFactory
import com.netflix.spinnaker.kork.plugins.sdk.SdkFactory
import java.lang.reflect.Modifier
import org.pf4j.Plugin
import org.pf4j.PluginFactory
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory

/**
 * Enables Plugin classes to be injected with SdkFactory and extension configuration.
 *
 * TODO(rz): Add `@PluginConfiguration` annot?
 */
class SpinnakerPluginFactory(
  private val pluginSdkFactories: List<SdkFactory>,
  private val configFactory: ConfigFactory
) : PluginFactory {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun create(pluginWrapper: PluginWrapper): Plugin? {
    val pluginClassName = pluginWrapper.descriptor.pluginClass
    log.debug("Create instance for plugin '$pluginClassName'")

    val pluginClass = try {
      pluginWrapper.pluginClassLoader.loadClass(pluginClassName)
    } catch (e: ClassNotFoundException) {
      log.error(e.message, e)
      return null
    }

    if (!pluginClass.isValidPlugin()) {
      log.error("The plugin class '{}' is not valid", pluginClass.canonicalName)
      return null
    }

    return pluginClass.createWithConstructor(
        ClassKind.PLUGIN,
        pluginSdkFactories,
        configFactory,
        pluginWrapper
      ) as Plugin?
  }

  /**
   * Perform checks on the loaded class to ensure that it is a valid implementation of a plugin.
   */
  private fun Class<*>.isValidPlugin(): Boolean {
    val modifiers = modifiers
    if (Modifier.isAbstract(modifiers) || Modifier.isInterface(modifiers) ||
      !Plugin::class.java.isAssignableFrom(this)) {
      return false
    }
    return true
  }
}
