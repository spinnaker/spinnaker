/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kork.plugins.v2

import com.netflix.spinnaker.kork.plugins.ClassKind
import com.netflix.spinnaker.kork.plugins.api.spring.PrivilegedSpringPlugin
import com.netflix.spinnaker.kork.plugins.config.ConfigFactory
import com.netflix.spinnaker.kork.plugins.createWithConstructor
import com.netflix.spinnaker.kork.plugins.sdk.SdkFactory
import org.pf4j.Plugin
import org.pf4j.PluginFactory
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory
import org.springframework.context.support.GenericApplicationContext

/**
 * Creates a [Plugin].
 *
 * If the plugin is not a [PrivilegedSpringPlugin], a [PluginContainer] will be created instead which
 * initializes and wires up the plugin's Spring ApplicationContext. This [PluginContainer] is an
 * implementation detail of the framework itself and hides the fact that Spring is used for plugin
 * configuration, component discovery and creation, and as well as extension promotion to the service.
 */
class SpringPluginFactory(
  private val sdkFactories: List<SdkFactory>,
  private val configFactory: ConfigFactory,
  private val serviceApplicationContext: GenericApplicationContext,
) : PluginFactory {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun create(pluginWrapper: PluginWrapper): Plugin? {
    val pluginClassName = pluginWrapper.descriptor.pluginClass
    log.debug("Creating plugin '$pluginClassName'")

    val pluginClass = try {
      pluginWrapper.pluginClassLoader.loadClass(pluginClassName)
    } catch (e: ClassNotFoundException) {
      log.error("Failed to load plugin class for '${pluginWrapper.pluginId}'", e)
      return null
    }

    val actualPlugin = pluginClass.createWithConstructor(ClassKind.PLUGIN, sdkFactories, configFactory, pluginWrapper) as Plugin

    // PrivilegedSpringPlugin does _kind of_ the same thing as PluginContainer, but the two are incompatible.
    // PluginContainer attempts to offer some of the convenience that PrivilegedSpringPlugin does, but without using
    // Spring itself as an API contract.
    return if (actualPlugin !is PrivilegedSpringPlugin) {
      PluginContainer(actualPlugin, serviceApplicationContext)
    } else {
      actualPlugin
    }
  }
}
