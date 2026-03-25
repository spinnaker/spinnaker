/*
 * Copyright 2026 Salesforce, Inc.
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
package com.netflix.spinnaker.gate.plugins.web.installed

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.gate.plugins.deck.DeckPluginConfiguration
import com.netflix.spinnaker.gate.plugins.deck.DeckPluginService
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import com.netflix.spinnaker.gate.services.internal.ExtendedFiatService
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager
import com.netflix.spinnaker.kork.plugins.SpringStrictPluginLoaderStatusProvider
import com.netflix.spinnaker.kork.plugins.update.SpinnakerUpdateManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * Tests that DeckPluginConfiguration correctly controls the creation of
 * DeckPluginService based on the spinnaker.extensibility.deck-proxy.enabled property.
 */
class PluginsInstalledControllerTest {

  private val contextRunner = ApplicationContextRunner()
    .withConfiguration(AutoConfigurations.of(DeckPluginConfiguration::class.java))
    .withBean(NoopRegistry::class.java)
    .withBean(SpinnakerUpdateManager::class.java, { mock() })
    .withBean(SpinnakerPluginManager::class.java, { mock() })
    .withBean(SpringStrictPluginLoaderStatusProvider::class.java, { mock() })
    .withBean(DynamicConfigService::class.java, { mock() })
    .withBean(ClouddriverService::class.java, { mock() })
    .withBean(ExtendedFiatService::class.java, { mock() })
    .withBean(Front50Service::class.java, { mock() })
    .withBean(OrcaServiceSelector::class.java, { mock() })
    .withUserConfiguration(PluginsInstalledController::class.java)

  @Test
  fun `DeckPluginService bean is present when deck-proxy is enabled`() {
    contextRunner
      .withPropertyValues("spinnaker.extensibility.deck-proxy.enabled=true")
      .run { context ->
        assertThat(context).hasNotFailed()
        assertThat(context.getBean(DeckPluginService::class.java)).isNotNull()
      }
  }

  @Test
  fun `DeckPluginService bean is absent when deck-proxy is disabled`() {
    contextRunner
      .withPropertyValues("spinnaker.extensibility.deck-proxy.enabled=false")
      .run { context ->
        assertThat(context).hasNotFailed()
        assertThat(context).doesNotHaveBean(DeckPluginService::class.java)
        assertThat(context).hasSingleBean(PluginsInstalledController::class.java)
      }
  }

  @Test
  fun `PluginsInstalledController is present when remote-plugins is enabled`() {
    contextRunner
      .withPropertyValues("spinnaker.extensibility.remote-plugins.enabled=true")
      .run { context ->
        assertThat(context).hasNotFailed()
        assertThat(context).hasSingleBean(PluginsInstalledController::class.java)
      }
  }

  @Test
  fun `PluginsInstalledController is absent when remote-plugins is disabled`() {
    contextRunner
      .withPropertyValues("spinnaker.extensibility.remote-plugins.enabled=false")
      .run { context ->
        assertThat(context).hasNotFailed()
        assertThat(context).doesNotHaveBean(PluginsInstalledController::class.java)
      }
  }

  @Test
  fun `context fails when both deck-proxy and remote-plugins are disabled`() {
    contextRunner
      .withPropertyValues(
        "spinnaker.extensibility.deck-proxy.enabled=false",
        "spinnaker.extensibility.remote-plugins.enabled=false"
      )
      .run { context ->
        assertThat(context).hasNotFailed()
        assertThat(context).doesNotHaveBean(PluginsInstalledController::class.java)
        assertThat(context).doesNotHaveBean(DeckPluginService::class.java)
      }
  }
}
