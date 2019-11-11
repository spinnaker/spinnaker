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
import java.nio.file.Path

/**
 * The primary entry-point to the plugins system from a provider-side (services, libs, CLIs, and so-on).
 *
 * WARNING: Due to how [org.pf4j.AbstractPluginManager] is written, we have to jump through hoops to get injected
 * code to initialize correctly. Unfortunately, PF4J attempts to initialize everything on object creation, so we
 * don't have access to [SpinnakerPluginManager] properties in the `create*` methods. To work around this, the
 * [SpinnakerPluginManager] instance is passed in rather than the actual dependency that is needed. Barf, barf, barf.
 * Unfortunately, there's a lot of logic in [org.pf4j.AbstractPluginManager] that we would need to copy if we wanted
 * to implement the [org.pf4j.PluginManager] interface in a reasonable way, potentially losing out on critical fixes
 * provided upstream. As a result, [statusProvider] and [configResolver] cannot be private, even though they should be.
 *
 * @param statusProvider A Spring Environment-aware plugin status provider.
 * @param configResolver The config resolver for extensions.
 * @param pluginsRoot The root path to search for in-process plugin artifacts.
 */
@Beta
open class SpinnakerPluginManager(
  internal val statusProvider: PluginStatusProvider,
  internal val configResolver: ConfigResolver,
  pluginsRoot: Path
) : DefaultPluginManager(pluginsRoot) {

  override fun createExtensionFactory(): ExtensionFactory {
    return SpringExtensionFactory(this)
  }

  override fun createPluginLoader(): PluginLoader =
    CompoundPluginLoader()
      .add(SpinnakerDevelopmentPluginLoader(this), this::isDevelopment)
      .add(SpinnakerDefaultPluginLoader(this))
      .add(SpinnakerJarPluginLoader(this))

  override fun createPluginDescriptorFinder(): PluginDescriptorFinder =
    SpinnakerPluginDescriptorFinder()

  override fun createPluginStatusProvider(): PluginStatusProvider =
    PluginStatusProviderProxy(this)

  private inner class PluginStatusProviderProxy(
    pluginManager: SpinnakerPluginManager
  ) : PluginStatusProvider by pluginManager.statusProvider
}
