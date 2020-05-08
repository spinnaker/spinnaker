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
 *
 */

package com.netflix.spinnaker.kork.plugins.update.release.provider

import com.netflix.spinnaker.kork.plugins.SpringStrictPluginLoaderStatusProvider
import com.netflix.spinnaker.kork.plugins.update.internal.SpinnakerPluginInfo
import com.netflix.spinnaker.kork.plugins.update.release.PluginInfoRelease
import com.netflix.spinnaker.kork.plugins.update.release.source.PluginInfoReleaseSource
import org.pf4j.update.PluginInfo

class AggregatePluginInfoReleaseProvider(
  private val pluginInfoReleaseSources: List<PluginInfoReleaseSource>,
  private val strictPluginLoaderStatusProvider: SpringStrictPluginLoaderStatusProvider
) : PluginInfoReleaseProvider {

  override fun getReleases(pluginInfo: List<SpinnakerPluginInfo>): Set<PluginInfoRelease> {
    val pluginInfoReleases: MutableSet<PluginInfoRelease> = mutableSetOf()

    pluginInfoReleaseSources.forEach { source ->
      source.getReleases(pluginInfo).forEach { release ->
        val hit = pluginInfoReleases.find { it.pluginId == release.pluginId }
        if (hit != null) {
          pluginInfoReleases.remove(hit)
          pluginInfoReleases.add(release)
        } else {
          pluginInfoReleases.add(release)
        }
      }

      source.processReleases(pluginInfoReleases)
    }

    pluginInfo.forEach { plugin ->
      if (missingPluginWithStrictLoading(pluginInfoReleases, plugin)) {
        throw PluginReleaseNotFoundException(plugin.id, pluginInfoReleaseSources)
      }
    }

    return pluginInfoReleases
  }

  private fun missingPluginWithStrictLoading(
    pluginInfoReleases: Set<PluginInfoRelease>,
    pluginInfo: PluginInfo
  ): Boolean {
    return pluginInfoReleases.find { it.pluginId == pluginInfo.id } == null &&
      strictPluginLoaderStatusProvider.isStrictPluginLoading()
  }
}
