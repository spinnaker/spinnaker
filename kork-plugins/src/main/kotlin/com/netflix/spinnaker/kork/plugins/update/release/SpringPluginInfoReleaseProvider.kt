/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.kork.plugins.update.release

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager
import com.netflix.spinnaker.kork.plugins.SpringPluginStatusProvider
import com.netflix.spinnaker.kork.plugins.update.SpinnakerUpdateManager
import org.pf4j.VersionManager
import org.pf4j.update.PluginInfo
import org.slf4j.LoggerFactory

/**
 * Determines plugin releases based on Spring properties via [SpringPluginStatusProvider].
 */
class SpringPluginInfoReleaseProvider(
  private val pluginStatusProvider: SpringPluginStatusProvider,
  private val versionManager: VersionManager,
  private val updateManager: SpinnakerUpdateManager,
  private val pluginManager: SpinnakerPluginManager
) : PluginInfoReleaseProvider {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun getReleases(pluginInfo: List<PluginInfo>): Set<PluginInfoRelease?> {
    return pluginInfo.map { pluginInfoRelease(it) }.toSet()
  }

  override fun getRelease(pluginInfo: PluginInfo): PluginInfoRelease? {
      return pluginInfoRelease(pluginInfo)
  }

  private fun pluginInfoRelease(pluginInfo: PluginInfo): PluginInfoRelease? {
    if (!pluginStatusProvider.isPluginDisabled(pluginInfo.id)) {
      val pluginVersion = pluginStatusProvider.pluginVersion(pluginInfo.id)

      val release = if (pluginVersion == null || pluginVersion.isEmpty()) {
        val fallbackRelease = updateManager.getLastPluginRelease(pluginInfo.id)
          ?: throw PluginReleaseNotFoundException(pluginInfo.id, pluginVersion)

        log.warn("'{}' is enabled but does not have a configured version, falling back to " +
          "version '{}'.", pluginInfo.id, fallbackRelease.version)

        fallbackRelease
      } else {
        pluginInfo.releases
          .filter { it.version == pluginVersion }
          .firstOrNull { release ->
            versionManager.checkVersionConstraint(pluginManager.systemVersion, release.requires)
          } ?: throw PluginReleaseNotFoundException(pluginInfo.id, pluginVersion)
      }
      return PluginInfoRelease(pluginInfo.id, release)
    }
    return null
  }
}
