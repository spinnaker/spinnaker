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
import com.netflix.spinnaker.kork.plugins.bundle.PluginBundleExtractor
import com.netflix.spinnaker.kork.plugins.update.SpinnakerUpdateManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@ConditionalOnProperty("spinnaker.extensibility.deck-proxy.enabled", matchIfMissing = true)
@ComponentScan("com.netflix.spinnaker.gate.plugins")
@EnableScheduling
open class DeckPluginConfiguration {
  @Bean
  open fun deckPluginCache(
    updateManager: SpinnakerUpdateManager,
    registry: Registry
  ): DeckPluginCache =
    DeckPluginCache(updateManager, PluginBundleExtractor(), registry)

  @Bean
  open fun deckPluginService(
    pluginCache: DeckPluginCache,
    registry: Registry
  ): DeckPluginService = DeckPluginService(pluginCache, registry)

  @Bean
  open fun deckPluginsController(pluginService: DeckPluginService): DeckPluginsController =
    DeckPluginsController(pluginService)
}
