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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.plugins.SpinnakerServiceVersionManager
import com.netflix.spinnaker.kork.plugins.events.PluginDownloaded
import com.netflix.spinnaker.kork.plugins.update.internal.SpinnakerPluginInfo
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

  override fun getPlugins(): List<SpinnakerPluginInfo> {
    val plugins: MutableList<SpinnakerPluginInfo> = mutableListOf()

    for (repository in getRepositories()) {
      repository.plugins.values
        .map { objectMapper.convertValue(it, SpinnakerPluginInfo::class.java) }
        .toCollection(plugins)
    }

    return plugins
  }

  internal fun downloadPluginReleases(pluginInfoReleases: Set<PluginInfoRelease>): Set<Path> {
    return pluginInfoReleases.mapNotNull { it.download() }.toSet()
  }

  private fun PluginInfoRelease.download(): Path? {
    // This is a remote plugin only, do nothing here.
    if (props.url == null && props.remoteExtensions.isNotEmpty()) {
      log.info("Nothing to download - plugin '{}' is a remote plugin and there is no in-process plugin binary.", pluginId)
      return null
    }

    val loadedPlugin = pluginManager.getPlugin(pluginId)
    if (loadedPlugin != null) {
      val loadedPluginVersion = loadedPlugin.descriptor.version

      // If a plugin was built without a version specified (via the Plugin-Version MANIFEST.MF
      // attribute), to be safe we always check for the configured plugin version.
      if (loadedPluginVersion == "unspecified" || pluginManager.versionManager.compareVersions(props.version, loadedPluginVersion) > 0) {
        log.debug(
          "Newer version '{}' of plugin '{}' found, deleting previous version '{}'",
          props.version, pluginId, loadedPluginVersion
        )
        val deleted = pluginManager.deletePlugin(loadedPlugin.pluginId)

        if (!deleted) {
          throw IntegrationException(
            "Unable to update plugin '${pluginId}' to version '${props.version}', " +
              "failed to delete previous version '$loadedPluginVersion}'"
          )
        }
      } else {
        return null
      }
    }

    log.debug("Downloading plugin '{}' with version '{}'", pluginId, props.version)
    val tmpPath = downloadPluginRelease(pluginId, props.version)
    val downloadedPluginPath = pluginManager.pluginsRoot.write(pluginId, tmpPath)

    log.debug("Downloaded plugin '{}'", pluginId)
    applicationEventPublisher.publishEvent(
      PluginDownloaded(this@SpinnakerUpdateManager, PluginDownloaded.Status.SUCCEEDED, pluginId, props.version)
    )

    return downloadedPluginPath
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

    val versionManager = pluginManager.versionManager
    val lastPluginRelease: MutableMap<String, PluginRelease> = mutableMapOf()

    // We're loading a plugin for a different service and we don't know the other service's version
    // (e.g., Gate doesn't know Deck's version).
    // We check if the release requirement mentions the service to see if the plugin is applicable,
    // then pick the latest version.
    for (release in pluginInfo.releases) {
      if (release.requires.contains(serviceName, ignoreCase = true)) {
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
  private fun Path.write(pluginId: String, downloaded: Path): Path {
    if (pluginManager.pluginsRoot == this) {
      val file = this.resolve(pluginId + "-" + downloaded.fileName.toString())
      File(this.toString()).mkdirs()
      try {
        return Files.move(downloaded, file, StandardCopyOption.REPLACE_EXISTING)
      } catch (e: IOException) {
        throw PluginRuntimeException(e, "Failed to write file '{}' to plugins folder", file)
      }
    } else {
      throw UnsupportedOperationException(
        "This operation is only supported on the specified " +
          "plugins root directory."
      )
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

  companion object {
    private val objectMapper = jacksonObjectMapper()
  }
}
