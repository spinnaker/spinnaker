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

import com.google.common.annotations.Beta
import com.netflix.spinnaker.kork.plugins.finders.SpinnakerPluginDescriptorFinder
import com.netflix.spinnaker.kork.plugins.loaders.SpinnakerDefaultPluginLoader
import com.netflix.spinnaker.kork.plugins.loaders.SpinnakerDevelopmentPluginLoader
import com.netflix.spinnaker.kork.plugins.loaders.SpinnakerJarPluginLoader
import org.pf4j.CompoundPluginLoader
import org.pf4j.DefaultPluginManager
import org.pf4j.ExtensionFactory
import org.pf4j.PluginDescriptorFinder
import org.pf4j.PluginLoader
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * The primary entry-point to the plugins system from a provider-side (services, libs, CLIs, and so-on).
 *
 * @param enabled Whether or not the PluginManager should do anything. While Plugins are an incubating
 * feature, this flag allows operators to disable the functionality, but keeps integrating services from
 * having to deal with `Optional<PluginManager>` autowiring.
 * @param pluginsRoot The root path to search for in-process plugin artifacts.
 */
@Beta
class SpinnakerPluginManager(
  val enabled: Boolean,
  pluginsRoot: Path
) : DefaultPluginManager(pluginsRoot) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun initialize() {
    if (enabled) {
      log.warn("Spinnaker Plugins enabled! This is a core incubating feature that may cause instability of the service")
      super.initialize()
    }
  }

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
}
