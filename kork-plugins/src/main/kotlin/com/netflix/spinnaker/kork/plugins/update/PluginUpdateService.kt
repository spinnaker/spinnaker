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

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager
import com.netflix.spinnaker.kork.plugins.events.PluginDownloaded
import com.netflix.spinnaker.kork.plugins.events.PluginDownloaded.Operation.INSTALL
import com.netflix.spinnaker.kork.plugins.events.PluginDownloaded.Operation.UPDATE
import com.netflix.spinnaker.kork.plugins.events.PluginDownloaded.Status.FAILED
import com.netflix.spinnaker.kork.plugins.events.PluginDownloaded.Status.SUCCEEDED
import org.pf4j.PluginRuntimeException
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import java.io.File
import java.io.IOException
import java.lang.UnsupportedOperationException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The [PluginUpdateService] is responsible for sourcing plugin updates from a plugin repository on startup.
 *
 * The current behavior is that the latest plugin release (according to semver) will always be selected as the
 * most desirable plugin.
 *
 * Plugins that require the Spinnaker service will be downloaded and installed.
 *
 * Plugins will not be loaded or started from this service.  All plugin loading and starting occurs
 * via [com.netflix.spinnaker.kork.plugins.ExtensionBeanDefinitionRegistryPostProcessor].
 *
 */
class PluginUpdateService(
  internal val updateManager: SpinnakerUpdateManager,
  internal val pluginManager: SpinnakerPluginManager,
  private val spinnakerServiceName: String,
  private val applicationEventPublisher: ApplicationEventPublisher
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  fun checkForUpdates() {
    updateExistingPlugins()
    installNewPlugins()
  }

  internal fun updateExistingPlugins() {
    if (!updateManager.hasUpdates()) {
      log.debug("No plugin updates found")
      return
    }

    val updates = updateManager.updates
    log.debug("Found {} plugin updates", updates.size)

    updates.forEach updates@{ plugin ->
      log.debug("Found update for plugin '{}'", plugin.id)

      val lastRelease =
        updateManager.getLastPluginRelease(plugin.id, spinnakerServiceName) ?: return@updates

      val installedVersion = pluginManager.getPlugin(plugin.id).descriptor.version

      log.debug("Update plugin '{}' from version {} to {}", plugin.id, installedVersion, lastRelease.version)

      if (pluginManager.getPlugin(plugin.id) == null) {
        throw PluginRuntimeException("Plugin {} cannot be updated since it is not installed", plugin.id)
      }

      val hasUpdate = updateManager.hasPluginUpdate(plugin.id)
      val updated: Boolean = if (hasUpdate) {
        val downloaded = updateManager.downloadPluginRelease(plugin.id, lastRelease.version)

        if (!pluginManager.deletePlugin(plugin.id)) {
          false
        } else {
          pluginManager.pluginsRoot.write(downloaded)
        }
      } else {
        log.warn("Plugin {} does not have an update available which is compatible with system version {}",
          plugin.id, pluginManager.systemVersion)
        false
      }

      if (updated) {
        log.debug("Updated plugin '{}'", plugin.id)
        applicationEventPublisher.publishEvent(
          PluginDownloaded(this, UPDATE, SUCCEEDED, plugin.id, lastRelease.version)
        )
      } else {
        log.error("Failed updating plugin '{}'", plugin.id)
        applicationEventPublisher.publishEvent(
          PluginDownloaded(this, UPDATE, FAILED, plugin.id, lastRelease.version)
        )
      }
    }
  }

  internal fun installNewPlugins() {
    if (!updateManager.hasAvailablePlugins()) {
      log.debug("No new plugins found to install")
      return
    }

    val availablePlugins = updateManager.availablePlugins
    log.info("Found {} available plugins", availablePlugins.size)

    availablePlugins.forEach availablePlugins@{ plugin ->
      log.debug("Found new plugin '{}'", plugin.id)

      val lastRelease =
        updateManager.getLastPluginRelease(plugin.id, spinnakerServiceName) ?: return@availablePlugins
      log.debug("Installing plugin '{}' with version {}", plugin.id, lastRelease.version)

      // Download to temporary location
      val downloaded = updateManager.downloadPluginRelease(plugin.id, lastRelease.version)

      val installed = pluginManager.pluginsRoot.write(downloaded)

      if (installed) {
        log.debug("Installed plugin '{}'", plugin.id)
        applicationEventPublisher.publishEvent(
          PluginDownloaded(this, INSTALL, SUCCEEDED, plugin.id, lastRelease.version)
        )
      } else {
        log.error("Failed installing plugin '{}'", plugin.id)
        applicationEventPublisher.publishEvent(
          PluginDownloaded(this, INSTALL, FAILED, plugin.id, lastRelease.version)
        )
      }
    }
  }

  /**
   * Write the plugin, creating the the plugins root directory defined in [pluginManager] if
   * necessary.
   */
  private fun Path.write(downloaded: Path): Boolean {
    if (pluginManager.pluginsRoot == this) {
      val file = this.resolve(downloaded.fileName)
      File(this.toString()).mkdirs()
      try {
        return Files.move(downloaded, file, StandardCopyOption.REPLACE_EXISTING)
          .contains(downloaded.fileName)
      } catch (e: IOException) {
        throw PluginRuntimeException(e, "Failed to write file '{}' to plugins folder", file)
      }
    } else {
      throw UnsupportedOperationException("This operation is only supported on the specified plugins root directory.")
    }
  }
}
