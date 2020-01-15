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
package com.netflix.spinnaker.kork.plugins

import com.netflix.spinnaker.kork.annotations.Beta
import com.netflix.spinnaker.kork.plugins.config.ConfigResolver
import com.netflix.spinnaker.kork.plugins.finders.SpinnakerPluginDescriptorFinder
import com.netflix.spinnaker.kork.plugins.loaders.SpinnakerDefaultPluginLoader
import com.netflix.spinnaker.kork.plugins.loaders.SpinnakerDevelopmentPluginLoader
import com.netflix.spinnaker.kork.plugins.loaders.SpinnakerJarPluginLoader
import org.pf4j.CompoundPluginLoader
import org.pf4j.DefaultPluginManager
import org.pf4j.ExtensionFactory
import org.pf4j.PluginDescriptorFinder
import org.pf4j.PluginLoader
import org.pf4j.PluginStatusProvider
import org.pf4j.PluginWrapper
import java.nio.file.Path

/**
 * The primary entry-point to the plugins system from a provider-side (services, libs, CLIs, and so-on).
 *
 * @param statusProvider A Spring Environment-aware plugin status provider.
 * @param configResolver The config resolver for extensions.
 * @param pluginsRoot The root path to search for in-process plugin artifacts.
 */
@Beta
open class SpinnakerPluginManager(
  private val statusProvider: PluginStatusProvider,
  private val configResolver: ConfigResolver,
  pluginsRoot: Path
) : DefaultPluginManager(pluginsRoot) {

  private val springExtensionFactory: ExtensionFactory = SpringExtensionFactory(this, configResolver)

  private inner class ExtensionFactoryDelegate : ExtensionFactory {
    override fun <T : Any?> create(extensionClass: Class<T>?): T = springExtensionFactory.create(extensionClass)
  }

  private inner class PluginStatusProviderDelegate : PluginStatusProvider {
    override fun disablePlugin(pluginId: String?) = statusProvider.disablePlugin(pluginId)

    override fun isPluginDisabled(pluginId: String?): Boolean = statusProvider.isPluginDisabled(pluginId)

    override fun enablePlugin(pluginId: String?) = statusProvider.enablePlugin(pluginId)
  }

  override fun createExtensionFactory(): ExtensionFactory = ExtensionFactoryDelegate()

  override fun createPluginStatusProvider(): PluginStatusProvider = PluginStatusProviderDelegate()

  override fun createPluginLoader(): PluginLoader =
    CompoundPluginLoader()
      .add(SpinnakerDevelopmentPluginLoader(this), this::isDevelopment)
      .add(SpinnakerDefaultPluginLoader(this))
      .add(SpinnakerJarPluginLoader(this))

  override fun createPluginDescriptorFinder(): PluginDescriptorFinder =
    SpinnakerPluginDescriptorFinder()

  internal fun setPlugins(specifiedPlugins: Collection<PluginWrapper>) {
    this.plugins = specifiedPlugins.associateBy { it.pluginId }
  }
}
