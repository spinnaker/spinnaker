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

import com.netflix.spinnaker.kork.plugins.update.ServerGroupLocationResolver
import com.netflix.spinnaker.kork.plugins.update.ServerGroupNameResolver
import com.netflix.spinnaker.kork.plugins.update.internal.Front50Service
import com.netflix.spinnaker.kork.plugins.update.internal.PinnedVersions
import com.netflix.spinnaker.kork.plugins.update.internal.SpinnakerPluginInfo
import com.netflix.spinnaker.kork.plugins.update.release.PluginInfoRelease
import java.io.IOException
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered.LOWEST_PRECEDENCE

/**
 * Resolves plugin releases from front50, optionally aligning which releases are returned based on
 * server group version pinning.
 */
class Front50PluginInfoReleaseSource(
  private val front50Service: Front50Service,
  private val serverGroupNameResolver: ServerGroupNameResolver,
  private val serverGroupLocationResolver: ServerGroupLocationResolver,
  private val serviceName: String
) : PluginInfoReleaseSource {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun getReleases(pluginInfo: List<SpinnakerPluginInfo>): Set<PluginInfoRelease> {
    return mutableSetOf()
  }

  override fun processReleases(pluginInfoReleases: Set<PluginInfoRelease>) {
    val serverGroupName = serverGroupNameResolver.get()
    if (serverGroupName == null) {
      log.warn("Could not resolve server group name: Skipping front50 plugin version resolution")
      return
    }

    val serverGroupLocation = serverGroupLocationResolver.get()
    if (serverGroupLocation == null) {
      log.warn("Could not resolve server group location: Skipping front50 plugin version resolution")
      return
    }

    val response = try {
      front50Service.pinVersions(
        serverGroupName,
        serviceName,
        serverGroupLocation,
        pluginInfoReleases.toVersionMap()
      ).execute()
    } catch (e: IOException) {
      log.error("Failed pinning versions in front50, falling back to locally-sourced plugin versions", e)
      return
    }

    if (!response.isSuccessful) {
      log.error(
        "Failed pinning plugin versions in front50, falling back to locally-sourced plugin versions: {}",
        response.message()
      )
      return
    }

    response.body()?.updateReleases(pluginInfoReleases)
  }

  private fun Set<PluginInfoRelease>.toVersionMap(): Map<String, String> =
    map { it.pluginId to it.props.version }.toMap()

  private fun PinnedVersions.updateReleases(pluginInfoReleases: Set<PluginInfoRelease>) {
    pluginInfoReleases.map {
      this[it.pluginId]?.let { release ->
        if (it.props.version != release.version) {
          log.info("Aligning plugin '${it.pluginId}' to pinned version: ${it.props.version} -> ${release.version}")
          it.props = release
        }
      }
    }
  }

  /**
   * Ensures this runs last in
   * [com.netflix.spinnaker.kork.plugins.update.release.provider.AggregatePluginInfoReleaseProvider]
   */
  override fun getOrder(): Int = LOWEST_PRECEDENCE
}
