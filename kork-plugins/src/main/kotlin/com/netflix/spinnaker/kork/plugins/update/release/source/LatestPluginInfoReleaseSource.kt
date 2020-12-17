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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.spinnaker.kork.plugins.update.SpinnakerUpdateManager
import com.netflix.spinnaker.kork.plugins.update.internal.SpinnakerPluginInfo
import com.netflix.spinnaker.kork.plugins.update.release.PluginInfoRelease
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered.HIGHEST_PRECEDENCE

/**
 * Source the last published plugin info release.
 */
class LatestPluginInfoReleaseSource(
  private val updateManager: SpinnakerUpdateManager,
  private val serviceName: String? = null
) : PluginInfoReleaseSource {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun getReleases(pluginInfo: List<SpinnakerPluginInfo>): Set<PluginInfoRelease> {
    return pluginInfo.mapNotNull { pluginInfoRelease(it) }.toSet()
  }

  private fun pluginInfoRelease(pluginInfo: SpinnakerPluginInfo): PluginInfoRelease? {
    val latestRelease = if (serviceName == null)
      updateManager.getLastPluginRelease(pluginInfo.id) else
      updateManager.getLastPluginRelease(pluginInfo.id, serviceName)

    return if (latestRelease != null) {
      log.debug("Latest release version '{}' for plugin '{}'", latestRelease.version, pluginInfo.id)
      PluginInfoRelease(
        pluginInfo.id,
        objectMapper.convertValue(latestRelease, SpinnakerPluginInfo.SpinnakerPluginRelease::class.java)
      )
    } else {
      log.debug("Latest release version not found for plugin '{}'", pluginInfo.id)
      null
    }
  }

  /**
   * Ensures this runs first in
   * [com.netflix.spinnaker.kork.plugins.update.release.provider.AggregatePluginInfoReleaseProvider]
   */
  override fun getOrder(): Int = HIGHEST_PRECEDENCE

  companion object {
    private val objectMapper = jacksonObjectMapper()
  }
}
