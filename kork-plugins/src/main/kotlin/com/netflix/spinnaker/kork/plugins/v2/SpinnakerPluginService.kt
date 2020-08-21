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

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager
import com.netflix.spinnaker.kork.plugins.SpringPluginStatusProvider
import com.netflix.spinnaker.kork.plugins.api.spring.PrivilegedSpringPlugin
import com.netflix.spinnaker.kork.plugins.update.SpinnakerUpdateManager
import com.netflix.spinnaker.kork.plugins.update.release.provider.PluginInfoReleaseProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.ApplicationEventPublisher
import org.springframework.util.Assert

/**
 * A service for managing the plugin framework.
 *
 * NOTE: Over time, we should be moving to this class over [SpinnakerPluginManager] and
 * [SpinnakerUpdateManager] as the primary touch points for the plugin framework, decoupling
 * Spinnaker-specific plugin framework logic from PF4J wherever possible.
 */
class SpinnakerPluginService(
  private val pluginManager: SpinnakerPluginManager,
  private val updateManager: SpinnakerUpdateManager,
  private val pluginInfoReleaseProvider: PluginInfoReleaseProvider,
  private val springPluginStatusProvider: SpringPluginStatusProvider
) {

  private val log = LoggerFactory.getLogger(javaClass)

  /**
   * Tracks the initialization state of the plugin framework: It can only be initialized once.
   */
  private var initialized: Boolean = false

  /**
   * Starts the plugin framework and completely initializes extensions for use by the application.
   */
  fun initialize() {
    Assert.isTrue(!initialized, "Plugin framework has already been initialized")

    withTiming("initializing plugins") {
      // Load known plugins prior to downloading so we can resolve what needs to be updated.
      pluginManager.loadPlugins()

      // Find the plugin releases for the currently enabled list of plugins
      val releases = updateManager.plugins
        .filter { springPluginStatusProvider.isPluginEnabled(it.id) }
        .let { enabledPlugins -> pluginInfoReleaseProvider.getReleases(enabledPlugins) }

      // Download releases, if any, updating previously loaded plugins where necessary
      updateManager.downloadPluginReleases(releases).forEach { pluginPath ->
        pluginManager.loadPlugin(pluginPath)
      }
    }
  }

  /**
   * Start the plugins, attaching exported plugin extensions to the provided [registry].
   */
  fun startPlugins(registry: BeanDefinitionRegistry) {
    withTiming("starting plugins") {
      // Start plugins. This should only be called once.
      pluginManager.startPlugins()

      // Perform additional work for Spring plugins; registering internal classes as beans where necessary
      pluginManager.startedPlugins.forEach { pluginWrapper ->
        val p = pluginWrapper.plugin
        if (p is PrivilegedSpringPlugin) {
          p.registerBeanDefinitions(registry)
        }

        if (p is PluginContainer) {
          p.registerInitializer(registry)
        }
      }
    }
  }

  private fun withTiming(task: String, callback: () -> Unit) {
    val start = System.currentTimeMillis()
    log.debug(task.capitalize())

    callback.invoke()

    log.debug("Finished $task in {}ms", System.currentTimeMillis() - start)
  }
}
