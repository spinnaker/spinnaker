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
package com.netflix.spinnaker.kork.plugins.update

import org.pf4j.PluginManager
import org.pf4j.update.PluginInfo
import org.pf4j.update.UpdateManager
import org.pf4j.update.UpdateRepository
import org.slf4j.LoggerFactory
import java.lang.UnsupportedOperationException
import java.nio.file.Path

/**
 * TODO(rz): Update [hasPluginUpdate] such that it understands the latest plugin is not always the one desired
 */
class SpinnakerUpdateManager(
  pluginManager: PluginManager,
  repositories: List<UpdateRepository>
) : UpdateManager(pluginManager, repositories) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  /**
   * This method is not supported as it calls pluginManager.loadPlugin and pluginManager.startPlugin.
   * Instead, we only want to install the plugins (see [PluginUpdateService]) and leave loading
   * and starting to [com.netflix.spinnaker.kork.plugins.ExtensionBeanDefinitionRegistryPostProcessor].
   */
  @Synchronized
  override fun installPlugin(id: String?, version: String?): Boolean {
    throw UnsupportedOperationException("UpdateManager installPlugin is not supported")
  }

  /**
   * This method is not supported as it calls pluginManager.loadPlugin and pluginManager.startPlugin.
   * Instead, we only want to install the plugins (see [PluginUpdateService]) and leave loading
   * and starting to [com.netflix.spinnaker.kork.plugins.ExtensionBeanDefinitionRegistryPostProcessor].
   */
  override fun updatePlugin(id: String?, version: String?): Boolean {
    throw UnsupportedOperationException("UpdateManager updatePlugin is not supported")
  }

  /**
   * This is the current strategy to select a plugin for release; find the latest release
   * version and check if that release requires the specified Spinnaker service.
   *
   * TODO(jonsie): We will eventually want to filter based on a configured plugin version and the
   *  required service version (i.e. echo>=1.0.0).
   */
  fun getLastPluginRelease(pluginId: String, serviceName: String): PluginInfo.PluginRelease? {
    val lastRelease = getLastPluginRelease(pluginId)
    if (lastRelease.requires.contains(serviceName)) return lastRelease
    return null
  }

  /**
   * Exists to expose protected [downloadPlugin]
   */
  fun downloadPluginRelease(pluginId: String, version: String): Path {
    return downloadPlugin(pluginId, version)
  }
}
