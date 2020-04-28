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

package com.netflix.spinnaker.kork.plugins.update.release.source

import com.netflix.spinnaker.kork.plugins.SpringPluginStatusProvider
import com.netflix.spinnaker.kork.plugins.update.release.PluginInfoRelease
import org.pf4j.update.PluginInfo
import org.slf4j.LoggerFactory

/**
 * Sources plugin releases from Spring properties via [SpringPluginStatusProvider].
 */
class SpringPluginInfoReleaseSource(
  private val pluginStatusProvider: SpringPluginStatusProvider
) : PluginInfoReleaseSource {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun getReleases(pluginInfo: List<PluginInfo>): Set<PluginInfoRelease> {
    return pluginInfo.mapNotNull { pluginInfoRelease(it) }.toSet()
  }

  private fun pluginInfoRelease(pluginInfo: PluginInfo): PluginInfoRelease? {
    val pluginVersion = pluginStatusProvider.pluginVersion(pluginInfo.id)
    val release = pluginInfo.releases.firstOrNull { it.version == pluginVersion }
    return if (release != null) {
      log.info("Spring configured release version '{}' for plugin '{}'", release.version, pluginInfo.id)
      PluginInfoRelease(pluginInfo.id, release)
    } else {
      log.info("Spring configured release version not found for plugin '{}'", pluginInfo.id)
      null
    }
  }

  override fun getOrder(): Int = 100
}
