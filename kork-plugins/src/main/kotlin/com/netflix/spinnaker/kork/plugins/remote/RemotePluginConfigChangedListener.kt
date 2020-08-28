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

package com.netflix.spinnaker.kork.plugins.remote

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.kork.annotations.Beta
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.plugins.events.RemotePluginConfigChanged
import com.netflix.spinnaker.kork.plugins.events.RemotePluginConfigChanged.Status.DISABLED
import com.netflix.spinnaker.kork.plugins.events.RemotePluginConfigChanged.Status.ENABLED
import com.netflix.spinnaker.kork.plugins.events.RemotePluginConfigChanged.Status.UPDATED
import com.netflix.spinnaker.kork.plugins.remote.extension.RemoteExtension
import com.netflix.spinnaker.kork.plugins.remote.extension.RemoteExtensionPointDefinition
import com.netflix.spinnaker.kork.plugins.remote.extension.transport.OkHttpRemoteExtensionTransport
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationListener
import javax.inject.Provider

/**
 * Listen for remote plugin configuration changes, instantiate [RemotePlugin] and [RemoteExtension]
 * objects as necessary, and add or remove the plugins from the remote plugin cache.
 */
@Beta
class RemotePluginConfigChangedListener(
  private val objectMapper: Provider<ObjectMapper>,
  private val okHttpClientProvider: Provider<OkHttpClientProvider>,
  private val remotePluginsCache: RemotePluginsCache,
  private val remoteExtensionPointDefinitions: List<RemoteExtensionPointDefinition>
) : ApplicationListener<RemotePluginConfigChanged> {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun onApplicationEvent(event: RemotePluginConfigChanged) {
    when (event.status) {
      ENABLED -> put(event)
      UPDATED -> put(event)
      DISABLED -> remotePluginsCache.remove(event.pluginId)
    }
  }

  private fun put(event: RemotePluginConfigChanged) {
    val remoteExtensions: MutableSet<RemoteExtension> = mutableSetOf()

    event.remoteExtensionConfigs.forEach { remoteExtensionConfig ->

      // TODO(jonsie): Support enabling/disabling transports in the config.
      // Configure HTTP if it is available since it is the only configurable transport right now.
      val remoteExtensionTransport = if (remoteExtensionConfig.transport.http.url.isNotEmpty()) {
        val client = okHttpClientProvider.get().getClient(
          DefaultServiceEndpoint(
            remoteExtensionConfig.id,
            remoteExtensionConfig.transport.http.url,
            remoteExtensionConfig.transport.http.config
          )
        )
        OkHttpRemoteExtensionTransport(
          objectMapper.get(),
          client,
          remoteExtensionConfig.transport.http.url
        )
      } else {
        throw RemoteExtensionTransportConfigurationException(event.pluginId)
      }

      val remoteExtensionDefinition = remoteExtensionPointDefinitions
        .find { it.type() == remoteExtensionConfig.type }
        ?: throw RemoteExtensionDefinitionNotFound(remoteExtensionConfig.type)

      val configType = remoteExtensionDefinition.configType()

      remoteExtensions.add(
        RemoteExtension(
          remoteExtensionConfig.id,
          event.pluginId,
          remoteExtensionDefinition.type(),
          objectMapper.get().convertValue(remoteExtensionConfig.config, configType),
          remoteExtensionTransport
        )
      )
    }

    val remotePlugin = RemotePlugin(event.pluginId, event.version, remoteExtensions)
    remotePluginsCache.put(remotePlugin)
    log.debug("Remote plugin '{}' added to cache due to '{}' event", event.pluginId, event.status)
  }
}

class RemoteExtensionTransportConfigurationException(
  pluginId: String
) : IntegrationException("No transport configuration for remote plugin '{}'", pluginId)

class RemoteExtensionDefinitionNotFound(
  type: String
) : IntegrationException("No remote extension definition found for type '{}'", type)
