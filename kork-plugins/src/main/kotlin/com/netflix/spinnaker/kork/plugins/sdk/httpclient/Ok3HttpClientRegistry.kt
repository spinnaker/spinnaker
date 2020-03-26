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
package com.netflix.spinnaker.kork.plugins.sdk.httpclient

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.plugins.api.httpclient.HttpClient
import com.netflix.spinnaker.kork.plugins.api.httpclient.HttpClientConfig
import com.netflix.spinnaker.kork.plugins.api.httpclient.HttpClientRegistry
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import org.springframework.core.env.Environment

/**
 * Configures and provides [HttpClient]s to an extension.
 */
class Ok3HttpClientRegistry(
  private val pluginId: String,
  private val environment: Environment,
  private val objectMapper: ObjectMapper,
  private val okHttp3ClientFactory: OkHttp3ClientFactory,
  okHttp3ClientConfiguration: OkHttp3ClientConfiguration
) : HttpClientRegistry {

  private val internalServicesClient = okHttp3ClientConfiguration.create().build()
  internal val okClients: MutableMap<HttpClientConfig, OkHttpClient> = ConcurrentHashMap()
  internal val clients: MutableMap<String, Ok3HttpClient> = ConcurrentHashMap()

  override fun configure(name: String, baseUrl: String, config: HttpClientConfig) {
    clients.computeIfAbsent("$pluginId.$name") {
      // Try to reduce the number of OkHttpClient instances that are floating around. We'll only create a new client
      // if the config is different from any other OkHttpClient.
      val okClient = okClients.computeIfAbsent(config) { okHttp3ClientFactory.create(baseUrl, config) }
      Ok3HttpClient(
        "$pluginId.$name",
        okHttp3ClientFactory.normalizeBaseUrl(baseUrl),
        okClient,
        objectMapper
      )
    }
  }

  override fun get(name: String): HttpClient {
    return clients["$pluginId.$name"] ?: throw IntegrationException("No client configured for '$name'")
  }

  override fun getInternalService(name: String): HttpClient {
    return clients.computeIfAbsent("internal.$name") {
      Ok3HttpClient("internal.$name", findInternalServiceBaseUrl(name), internalServicesClient, objectMapper)
    }
  }

  private fun findInternalServiceBaseUrl(name: String): String {
    val normalized = name.toLowerCase()
    val paths = baseUrlPaths.map { it.replace(serviceNamePlaceholder, normalized) }

    for (path in paths) {
      val baseUrl = environment.getProperty(path)
      if (baseUrl != null) {
        return baseUrl
      }
    }

    throw IntegrationException("Unknown service '$name': No baseUrl config property set for service")
  }

  companion object {
    private const val serviceNamePlaceholder = "SERVICE_NAME"
    private val baseUrlPaths: List<String> = listOf(
      "$serviceNamePlaceholder.baseUrl",
      "services.$serviceNamePlaceholder.baseUrl"
    )
  }
}
