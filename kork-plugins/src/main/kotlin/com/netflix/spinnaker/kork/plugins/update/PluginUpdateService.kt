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
import org.pf4j.update.UpdateManager
import org.slf4j.LoggerFactory

/**
 * The [PluginUpdateService] is responsible for sourcing plugin updates from a plugin repository on startup.
 *
 * The current behavior is that the latest plugin release (according to semver) will always be selected as the
 * most desirable plugin.
 *
 * All plugins that are returned by the repository will be downloaded and installed.
 *
 * While it's possible that this could be used after the application has started up, there is no guarantee that the
 * extensions a plugin exposes will be correctly refreshed within the Spring context at this point. Therefore, it is
 * strongly advised that if a plugin update needs to occur, the service should be redeployed. If live updates are
 * desired in the future, we'll need to proxy extensions so that the real implementing object is delegated to, allowing
 * Spring injection to continue working.
 *
 * TODO(rz): Look to a local config or front50 (depending on spinnaker setup) to determine if there are specific
 *  plugin versions to use. We may not want the most recent release of a plugin.
 * TODO(rz): The front50 integration will need to be smart enough to understand what service is asking for plugins
 *  from the repository, and return only the plugins that are supposed to be installed on that service.
 */
class PluginUpdateService(
  internal val updateManager: UpdateManager,
  internal val pluginManager: SpinnakerPluginManager
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

    updates.forEach { plugin ->
      log.debug("Found update for plugin '{}'", plugin.id)

      val lastRelease = updateManager.getLastPluginRelease(plugin.id)
      val installedVersion = pluginManager.getPlugin(plugin.id).descriptor.version

      log.debug("Update plugin '{}' from version {} to {}", plugin.id, installedVersion, lastRelease.version)

      val updated = updateManager.updatePlugin(plugin.id, lastRelease.version)
      if (updated) {
        log.debug("Updated plugin '{}'", plugin.id)
      } else {
        log.error("Failed updating plugin '{}'", plugin.id)
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

    availablePlugins.forEach { plugin ->
      log.debug("Found new plugin '{}'", plugin.id)

      val lastRelease = updateManager.getLastPluginRelease(plugin.id)
      log.debug("Installing plugin '{}' with version {}", plugin.id, lastRelease.version)

      val installed = updateManager.installPlugin(plugin.id, lastRelease.version)
      if (installed) {
        log.debug("Installed plugin '{}'", plugin.id)
      } else {
        log.error("Failed installing plugin '{}'", plugin.id)
      }
    }
  }
}
