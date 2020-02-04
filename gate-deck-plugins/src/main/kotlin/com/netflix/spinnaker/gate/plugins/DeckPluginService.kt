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
package com.netflix.spinnaker.gate.plugins

import com.netflix.spectator.api.Registry
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Provides Deck with means of discovering what plugins it needs to download and a standard interface for
 * retrieving plugin assets.
 */
class DeckPluginService(
  private val pluginCache: DeckPluginCache,
  private val registry: Registry
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val assetHitsId = registry.createId("plugins.deckAssets.hits")
  private val assetMissesId = registry.createId("plugins.deckAssets.misses")

  /**
   * Returns a list of all plugin versions that Deck should know how to load.
   *
   * Deck will be responsible for taking this manifest and requesting the `index.js` file inside of a plugin version
   * and dynamically resolving any needed assets as it goes along.
   */
  fun getPluginsManifests(): List<DeckPluginVersion> {
    if (!pluginCache.isCachePopulated()) {
      throw CacheNotReadyException()
    }
    return pluginCache.getCache().map { it.plugin }
  }

  /**
   * Get an individual plugin's asset by version.
   *
   * If the plugin does not exist on the filesystem, it will be downloaded and cached to a standard location so that
   * subsequent asset requests for the plugin version will be faster.
   */
  fun getPluginAsset(pluginId: String, pluginVersion: String, assetPath: String): PluginAsset? {
    if (!pluginCache.isCachePopulated()) {
      throw CacheNotReadyException()
    }

    val sanitizedAssetPath = if (assetPath.startsWith("/")) assetPath.substring(1) else assetPath

    val localAsset = pluginCache.getOrDownload(pluginId, pluginVersion).resolve(sanitizedAssetPath).toFile()
    if (!localAsset.exists()) {
      log.error("Unable to find requested plugin asset '$assetPath' for '$pluginId@$pluginVersion'")
      registry.counter(assetMissesId).increment()
      return null
    }
    registry.counter(assetHitsId).increment()

    return PluginAsset.from(localAsset)
  }

  data class PluginAsset(val contentType: String, val content: String) {

    companion object {
      private val log by lazy { LoggerFactory.getLogger(PluginAsset::class.java) }

      fun from(file: File): PluginAsset {
        return PluginAsset(
            contentType = when {
              file.toString().endsWith(".js") -> { "application/javascript" }
              file.toString().endsWith(".css") -> { "text/css" }
              file.toString().endsWith(".html") -> { "text/html" }
              else -> {
                log.warn("Unhandled file extension to content-type mapping for file `{}`, falling back to text/plain", file.toString())
                "text/plain"
              }
            },
            content = file.readText()
        )
      }
    }
  }
}
