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

import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.pf4j.PluginManager
import org.pf4j.PluginRuntimeException
import org.pf4j.PluginState
import org.pf4j.update.UpdateManager
import org.pf4j.update.UpdateRepository
import org.slf4j.LoggerFactory
import java.io.File

/**
 * TODO(rz): Update [hasPluginUpdate] such that it understands the latest plugin is not always the one desired
 */
class SpinnakerUpdateManager(
  private val pluginManager: PluginManager,
  repositories: List<UpdateRepository>
) : UpdateManager(pluginManager, repositories) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  /**
   * TODO(rz): PF4J made this private, so it's gotta be duplicated here.
   */
  private val systemVersion: String = pluginManager.systemVersion

  /**
   * TODO(rz): Remove once PF4J releases a new version with REPLACE_EXISTING fix.
   */
  @Synchronized
  override fun installPlugin(id: String?, version: String?): Boolean {
    // Download to temporary location
    val downloaded = downloadPlugin(id, version)

    val pluginsRoot = pluginManager.pluginsRoot
    val file = pluginsRoot.resolve(downloaded.fileName)
    File(pluginsRoot.toString()).mkdirs()
    try {
      Files.move(downloaded, file, StandardCopyOption.REPLACE_EXISTING)
    } catch (e: IOException) {
      throw PluginRuntimeException(e, "Failed to write file '{}' to plugins folder", file)
    }

    val pluginId = pluginManager.loadPlugin(file)
    val state = pluginManager.startPlugin(pluginId)

    return PluginState.STARTED == state
  }

  /**
   * TODO(rz): Remove once PF4J releases a new version with REPLACE_EXISTING fix.
   */
  override fun updatePlugin(id: String?, version: String?): Boolean {
    if (pluginManager.getPlugin(id) == null) {
      throw PluginRuntimeException("Plugin {} cannot be updated since it is not installed", id)
    }

    if (!hasPluginUpdate(id)) {
      log.warn("Plugin {} does not have an update available which is compatible with system version {}", id, systemVersion)
      return false
    }

    // Download to temp folder
    val downloaded = downloadPlugin(id, version)

    if (!pluginManager.deletePlugin(id)) {
      return false
    }

    val pluginsRoot = pluginManager.pluginsRoot
    val file = pluginsRoot.resolve(downloaded.fileName)
    try {
      Files.move(downloaded, file, StandardCopyOption.REPLACE_EXISTING)
    } catch (e: IOException) {
      throw PluginRuntimeException("Failed to write plugin file {} to plugin folder", file)
    }

    val newPluginId = pluginManager.loadPlugin(file)
    val state = pluginManager.startPlugin(newPluginId)

    return PluginState.STARTED == state
  }
}
