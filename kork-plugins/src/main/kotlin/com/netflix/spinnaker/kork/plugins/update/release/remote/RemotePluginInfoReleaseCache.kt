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

package com.netflix.spinnaker.kork.plugins.update.release.remote

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.netflix.spinnaker.kork.annotations.Beta
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager
import com.netflix.spinnaker.kork.plugins.SpringPluginStatusProvider
import com.netflix.spinnaker.kork.plugins.events.RemotePluginConfigChanged
import com.netflix.spinnaker.kork.plugins.events.RemotePluginConfigChanged.Status.DISABLED
import com.netflix.spinnaker.kork.plugins.events.RemotePluginConfigChanged.Status.ENABLED
import com.netflix.spinnaker.kork.plugins.events.RemotePluginConfigChanged.Status.UPDATED
import com.netflix.spinnaker.kork.plugins.update.SpinnakerUpdateManager
import com.netflix.spinnaker.kork.plugins.update.release.PluginInfoRelease
import com.netflix.spinnaker.kork.plugins.update.release.provider.PluginInfoReleaseProvider
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled

/**
 * Provides a [PluginInfoRelease] cache of enabled plugins that contain remote extensions.
 *
 * Emits [ENABLED], [DISABLED], and [UPDATED] events that contain the plugin ID, version, and remote
 * extensions when a corresponding change is detected in the cache (added, updated, or removed).
 */
@Beta
class RemotePluginInfoReleaseCache(
  private val pluginInfoReleaseProvider: PluginInfoReleaseProvider,
  private val applicationEventPublisher: ApplicationEventPublisher,
  private val updateManager: SpinnakerUpdateManager,
  private val pluginManager: SpinnakerPluginManager,
  private val springPluginStatusProvider: SpringPluginStatusProvider
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  // unbounded cache because we need an in-memory reference to all enabled plugins with remote extensions
  private val pluginCache: Cache<String, PluginInfoRelease> = Caffeine.newBuilder()
    .build<String, PluginInfoRelease>()

  /**
   * Refresh cache process for plugin releases.
   */
  @Scheduled(
    fixedDelayString = "\${spinnaker.extensibility.remote-plugins.cache-refresh-interval-ms:60000}",
    initialDelay = 0
  )
  fun refresh() {
    updateManager.refresh()

    val enabledPlugins = updateManager.plugins
      .filter { springPluginStatusProvider.isPluginEnabled(it.id) }
      .let { enabledPlugins -> pluginInfoReleaseProvider.getReleases(enabledPlugins) }
      .filter { it.props.remoteExtensions.isNotEmpty() }

    remove(enabledPlugins)
    addOrUpdate(enabledPlugins)

    log.info("Cached ${pluginCache.estimatedSize()} remote plugin configurations.")
  }

  /**
   * Get a specific plugin ID from the cache.
   */
  fun get(pluginId: String): PluginInfoRelease? {
    return pluginCache.getIfPresent(pluginId)
  }

  /**
   * Find cached plugins that are no longer enabled - remove from cache and emit disabled
   * event.
   */
  private fun remove(enabledPlugins: List<PluginInfoRelease>) {
    val disabledPlugins = pluginCache.asMap().filterNot { cachedPlugin ->
      enabledPlugins.map { it.pluginId }.contains(cachedPlugin.key)
    }

    if (disabledPlugins.isNotEmpty()) {
      disabledPlugins.forEach { disabledPlugin ->
        log.debug("Removing remote plugin configuration '{}' from cache.", disabledPlugin.key)
        pluginCache.invalidate(disabledPlugin.key)
        applicationEventPublisher.publishEvent(
          RemotePluginConfigChanged(
            this, DISABLED, disabledPlugin.key,
            disabledPlugin.value.props.version, disabledPlugin.value.props.remoteExtensions
          )
        )
      }
    }
  }

  /**
   * Find enabled plugins that are either not in the cache or have a version that does not match
   * a cached version.  Perform a system version constraint check against the running version of the
   * Spinnaker service and if compatible, add/update plugins and emit an enabled or updated event.
   */
  private fun addOrUpdate(enabledPlugins: List<PluginInfoRelease>) {
    enabledPlugins
      .forEach { enabledPlugin ->
        val cachedRelease = pluginCache.getIfPresent(enabledPlugin.pluginId)

        if (cachedRelease == null && versionConstraint(enabledPlugin.pluginId, enabledPlugin.props.requires)) {
          log.debug("Adding remote plugin configuration '{}' to cache.", enabledPlugin.pluginId)
          pluginCache.put(enabledPlugin.pluginId, enabledPlugin)
          applicationEventPublisher.publishEvent(
            RemotePluginConfigChanged(
              this, ENABLED, enabledPlugin.pluginId,
              enabledPlugin.props.version, enabledPlugin.props.remoteExtensions
            )
          )
        } else if (cachedRelease != null && cachedRelease.props.version != enabledPlugin.props.version &&
          versionConstraint(enabledPlugin.pluginId, enabledPlugin.props.requires)
        ) {
          log.debug("Updating remote plugin configuration '{}' in cache.", enabledPlugin.pluginId)
          pluginCache.put(enabledPlugin.pluginId, enabledPlugin)
          applicationEventPublisher.publishEvent(
            RemotePluginConfigChanged(
              this, UPDATED, enabledPlugin.pluginId,
              enabledPlugin.props.version, enabledPlugin.props.remoteExtensions
            )
          )
        } else {
          log.debug("No remote plugin versions found that need to be enabled or updated.")
        }
      }
  }

  private fun versionConstraint(pluginId: String, requires: String): Boolean {
    return if (pluginManager.spinnakerVersionManager.checkVersionConstraint(pluginManager.systemVersion, requires)) {
      true
    } else {
      log.warn("Requested enabled remote plugin '{}' is not compatible with system version '{}', requires '{}'", pluginId, pluginManager.systemVersion, requires)
      false
    }
  }
}
