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
package com.netflix.spinnaker.gate.plugins.deck

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import com.netflix.spinnaker.kork.plugins.SpringPluginStatusProvider
import com.netflix.spinnaker.kork.plugins.SpringStrictPluginLoaderStatusProvider
import com.netflix.spinnaker.kork.plugins.bundle.PluginBundleExtractor
import com.netflix.spinnaker.kork.plugins.update.SpinnakerUpdateManager
import com.netflix.spinnaker.kork.plugins.update.release.provider.PluginInfoReleaseProvider
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.pf4j.PluginRuntimeException
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.nio.file.Paths
import java.util.Optional

/**
 * Responsible for keeping an up-to-date cache of all plugins that Deck needs to know about.
 */
class DeckPluginCache(
  private val updateManager: SpinnakerUpdateManager,
  private val pluginBundleExtractor: PluginBundleExtractor,
  private val springPluginStatusProvider: SpringPluginStatusProvider,
  private val pluginInfoReleaseProvider: PluginInfoReleaseProvider,
  private val registry: MeterRegistry,
  private val springStrictPluginLoaderStatusProvider: SpringStrictPluginLoaderStatusProvider,
  private val pluginsCacheDirectory: Optional<String>
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val cache: MutableSet<PluginCacheEntry> = mutableSetOf()
  private var cachePopulated: Boolean = false

  private val versionsMetricName = "plugins.deckCache.versions"
  private val hitsMetricName = "plugins.deckCache.hits"
  private val missesMetricName = "plugins.deckCache.misses"
  private val downloadDurationMetricName = "plugins.deckCache.downloadDuration"
  private val refreshDurationMetricName = "plugins.deckCache.refreshDuration"
  private val CACHE_ROOT_PATH = if (pluginsCacheDirectory.isPresent) {
    Paths.get(pluginsCacheDirectory.get())
  } else {
    Files.createTempDirectory("downloaded-plugin-cache")
  }

  /**
   * Refreshes the local file cache of _current_ plugins. Should Deck need plugin assets from an older plugin release
   * version, it will be downloaded and cached on-demand.
   *
   * The default refresh interval is every minute.
   */
  @Scheduled(
    fixedDelayString = "\${spinnaker.extensibility.deck-proxy.cache-refresh-interval-ms:60000}",
    initialDelay = 0
  )
  internal fun refresh() {
    registry.timer(refreshDurationMetricName).record(Runnable {
      log.info("Refreshing plugin cache")

      updateManager.refresh()

      val releases = updateManager.plugins
        .filter { springPluginStatusProvider.isPluginEnabled(it.id) }
        .let { enabledPlugins -> pluginInfoReleaseProvider.getReleases(enabledPlugins) }

      val newCache = releases.mapNotNull { release ->
        val plugin = DeckPluginVersion(release.pluginId, release.props.version)
        getOrDownload(plugin.id, plugin.version)?.let {
          path ->
          PluginCacheEntry(plugin, path)
        }
      }

      cache.removeIf { !newCache.contains(it) }
      cache.addAll(newCache)

      cache.forEach {
        registry.counter(versionsMetricName, it.plugin.pluginTags()).increment()
      }

      cachePopulated = true
      log.info("Cached ${cache.size} deck plugins")
    })
  }

  fun isCachePopulated(): Boolean = cachePopulated

  fun getCache(): Set<PluginCacheEntry> {
    return cache.toSet()
  }

  /**
   * Get a previously downloaded plugin path, or download the plugin and cache the artifacts for subsequent requests.
   */
  fun getOrDownload(pluginId: String, pluginVersion: String): Path? {
    val cachePath = CACHE_ROOT_PATH.resolve("$pluginId/$pluginVersion")
    if (!cachePath.toFile().isDirectory) {
      try {
        registry.timer(downloadDurationMetricName, pluginTags(pluginId, pluginVersion)).record(Runnable {
          log.info("Downloading plugin '$pluginId@$pluginVersion'")
          val deckPluginPath = pluginBundleExtractor.extractService(
            updateManager.downloadPluginRelease(pluginId, pluginVersion),
            "deck"
          )

          log.info("Adding plugin '$pluginId@$pluginVersion' to local cache: $cachePath")
          Files.createDirectories(cachePath)
          Files.move(deckPluginPath, cachePath, StandardCopyOption.REPLACE_EXISTING)
        })
      } catch (e: PluginRuntimeException) {
        log.warn("Unable to download plugin {}@{}", pluginId, pluginVersion)
        if (springStrictPluginLoaderStatusProvider.isStrictPluginLoading()) {
          throw PluginRuntimeException(e, "Unable to download plugin {}@{}", pluginId, pluginVersion)
        } else {
          return null
        }
      }
      registry.counter(missesMetricName, pluginTags(pluginId, pluginVersion)).increment()
    } else {
      registry.counter(hitsMetricName, pluginTags(pluginId, pluginVersion)).increment()
    }
    return cachePath
  }

  private fun pluginTags(pluginId: String, version: String) =
    Tags.of("pluginId", pluginId, "version", version)

  private fun DeckPluginVersion.pluginTags() = pluginTags(id, version)

  /**
   * @param plugin The plugin version metadata
   * @param path The path to the local file cache of the plugin
   */
  data class PluginCacheEntry(
    val plugin: DeckPluginVersion,
    val path: Path
  )

  companion object {
    internal const val DECK_REQUIREMENT = "deck"
  }
}
