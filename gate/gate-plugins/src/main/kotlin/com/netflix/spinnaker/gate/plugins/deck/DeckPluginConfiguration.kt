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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.plugins.SpringPluginStatusProvider
import com.netflix.spinnaker.kork.plugins.SpringStrictPluginLoaderStatusProvider
import com.netflix.spinnaker.kork.plugins.bundle.PluginBundleExtractor
import com.netflix.spinnaker.kork.plugins.update.SpinnakerUpdateManager
import com.netflix.spinnaker.kork.plugins.update.release.provider.AggregatePluginInfoReleaseProvider
import com.netflix.spinnaker.kork.plugins.update.release.source.LatestPluginInfoReleaseSource
import com.netflix.spinnaker.kork.plugins.update.release.source.SpringPluginInfoReleaseSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.Optional

@Configuration
@ConditionalOnProperty("spinnaker.extensibility.deck-proxy.enabled", matchIfMissing = true)
@EnableScheduling
class DeckPluginConfiguration {

  @Value("\${spinnaker.extensibility.deck-proxy.plugins-path:#{null}}")
  private val pluginsCacheDirectory: String? = null

  @Bean
  fun deckPluginCache(
    updateManager: SpinnakerUpdateManager,
    registry: Registry,
    springStrictPluginLoaderStatusProvider: SpringStrictPluginLoaderStatusProvider,
    dynamicConfigService: DynamicConfigService
  ): DeckPluginCache {
    val springPluginStatusProvider = SpringPluginStatusProvider(
      dynamicConfigService,
      "spinnaker.extensibility.deck-proxy.plugins"
    )

    val sources = listOf(
      LatestPluginInfoReleaseSource(updateManager, "deck"),
      SpringPluginInfoReleaseSource(springPluginStatusProvider)
    )

    return DeckPluginCache(
      updateManager,
      PluginBundleExtractor(springStrictPluginLoaderStatusProvider),
      springPluginStatusProvider,
      AggregatePluginInfoReleaseProvider(sources, springStrictPluginLoaderStatusProvider),
      registry,
      springStrictPluginLoaderStatusProvider,
      Optional.ofNullable(pluginsCacheDirectory)
    )
  }

  @Bean
  fun deckPluginService(
    pluginCache: DeckPluginCache,
    registry: Registry
  ): DeckPluginService = DeckPluginService(pluginCache, registry)
}
