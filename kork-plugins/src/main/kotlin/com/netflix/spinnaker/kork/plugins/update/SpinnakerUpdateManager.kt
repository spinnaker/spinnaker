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

import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.plugins.SpinnakerServiceVersionManager
import com.netflix.spinnaker.kork.plugins.events.PluginDownloaded
import com.netflix.spinnaker.kork.plugins.update.release.PluginInfoRelease
import com.netflix.spinnaker.kork.version.ServiceVersion
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.pf4j.PluginManager
import org.pf4j.PluginRuntimeException
import org.pf4j.update.PluginInfo.PluginRelease
import org.pf4j.update.UpdateManager
import org.pf4j.update.UpdateRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher

/**
 * TODO(jonsie): We have disabled [org.pf4j.update.UpdateManager] update, load, and start plugin
 *  logic here. This is now used only to manage the list of [UpdateRepository] objects, download
 *  the desired artifact, and check version constraints via an implementation of
 *  [org.pf4j.VersionManager]. At some point, we may want to consider removing
 *  [org.pf4j.update.UpdateManager].
 */
class SpinnakerUpdateManager(
  private val applicationEventPublisher: ApplicationEventPublisher,
  private val pluginManager: PluginManager,
  repositories: List<UpdateRepository>
) : UpdateManager(pluginManager, repositories) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  internal fun downloadPluginReleases(pluginInfoReleases: Set<PluginInfoRelease?>): Set<Path> {
    val downloadedPlugins: MutableSet<Path> = mutableSetOf()

    pluginInfoReleases
      .filterNotNull()
      .forEach release@{ release ->

        val loadedPlugin = pluginManager.getPlugin(release.pluginId)
        if (loadedPlugin != null) {
          val loadedPluginVersion = loadedPlugin.descriptor.version

          // If a plugin was built without a version specified (via the Plugin-Version MANIFEST.MF
          // attribute), to be safe we always check for the configured plugin version.
          if (loadedPluginVersion == "unspecified" || pluginManager.versionManager.compareVersions(release.props.version, loadedPluginVersion) > 0) {
            log.debug("Newer version '{}' of plugin '{}' found, deleting previous version '{}'",
              release.props.version, release.pluginId, loadedPluginVersion)
            val deleted = pluginManager.deletePlugin(loadedPlugin.pluginId)

            if (!deleted) {
              throw IntegrationException(
                "Unable to update plugin '${release.pluginId}' to version '${release.props.version}', " +
                  "failed to delete previous version '$loadedPluginVersion}'")
            }
          } else {
            return@release
          }
        }

        log.debug("Downloading plugin '{}' with version '{}'", release.pluginId, release.props.version)
        val tmpPath = downloadPluginRelease(release.pluginId, release.props.version)
        val downloadedPluginPath = pluginManager.pluginsRoot.write(tmpPath)

        log.debug("Downloaded plugin '{}'", release.pluginId)
        applicationEventPublisher.publishEvent(
          PluginDownloaded(this, PluginDownloaded.Status.SUCCEEDED, release.pluginId, release.props.version)
        )

        downloadedPlugins.add(downloadedPluginPath)
    }

    return downloadedPlugins
  }

  /**
   * Supports the scenario wherein we want the latest plugin for the specified service (i.e., not
   * necessarily the service that executes this code).
   *
   * For example, Gate fetches plugins for Deck - so we need to pass in the required service for
   * Deck.
   */
  fun getLastPluginRelease(id: String, serviceName: String): PluginRelease? {
    val pluginInfo = pluginsMap[id]
    if (pluginInfo == null) {
      log.warn("Unable to find plugin info for '{}'", id)
      return null
    }

    val systemVersion = pluginManager.systemVersion
    val versionManager = SpinnakerServiceVersionManager(serviceName)
    val lastPluginRelease: MutableMap<String, PluginRelease> = mutableMapOf()

    // If service version cannot be determined(i.e 0.0.0),
    // just check if the release requirement mentions the service to see if the plugin is applicable. This case should
    // never happen, we should always know the system version but if it happens we can at least give a matching plugin
    // to the service name in question.
    //
    // If version is determined, check the version constraint explicitly to decide if the plugin is applicable or not.
    for (release in pluginInfo.releases) {
      if ((systemVersion == ServiceVersion.DEFAULT_VERSION && release.requires.contains(serviceName, ignoreCase = true)) ||
          versionManager.checkVersionConstraint(systemVersion, release.requires)) {
        if (lastPluginRelease[id] == null) {
          lastPluginRelease[id] = release
        } else if (versionManager.compareVersions(release.version, lastPluginRelease[id]!!.version) > 0) {
          lastPluginRelease[id] = release
        }
      }
    }

    return lastPluginRelease[id]
  }

  /**
   * Exists to expose protected [downloadPlugin] - must remain public.
   *
   * TODO(jonsie): This will call [UpdateManager.getLastPluginRelease] if `version`
   *  is null.  Shouldn't happen, but it could.  That is potentially problematic if the desired
   *  service name is different than the service that executes this code.  Probably another reason
   *  to consider moving away from [UpdateManager].
   */
  fun downloadPluginRelease(pluginId: String, version: String): Path {
    return downloadPlugin(pluginId, version)
  }

  /**
   * Write the plugin, creating the the plugins root directory defined in [pluginManager] if
   * necessary.
   */
  private fun Path.write(downloaded: Path): Path {
    if (pluginManager.pluginsRoot == this) {
      val file = this.resolve(downloaded.fileName)
      File(this.toString()).mkdirs()
      try {
        return Files.move(downloaded, file, StandardCopyOption.REPLACE_EXISTING)
      } catch (e: IOException) {
        throw PluginRuntimeException(e, "Failed to write file '{}' to plugins folder", file)
      }
    } else {
      throw UnsupportedOperationException("This operation is only supported on the specified " +
        "plugins root directory.")
    }
  }

  /**
   * This method is not supported as it calls pluginManager.loadPlugin and pluginManager.startPlugin.
   * Instead, we only want to install the plugins and leave loading and starting to
   * [com.netflix.spinnaker.kork.plugins.ExtensionBeanDefinitionRegistryPostProcessor].
   */
  @Synchronized
  override fun installPlugin(id: String?, version: String?): Boolean {
    throw UnsupportedOperationException("UpdateManager installPlugin is not supported")
  }

  /**
   * This method is not supported as it calls pluginManager.loadPlugin and pluginManager.startPlugin.
   * Instead, we only want to install the plugins and leave loading and starting to
   * [com.netflix.spinnaker.kork.plugins.ExtensionBeanDefinitionRegistryPostProcessor].
   */
  override fun updatePlugin(id: String?, version: String?): Boolean {
    throw UnsupportedOperationException("UpdateManager updatePlugin is not supported")
  }
}
