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
package com.netflix.spinnaker.kork.plugins.sdk.httpclient.internal

import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.plugins.api.httpclient.HttpClientConfig
import com.netflix.spinnaker.kork.plugins.sdk.httpclient.OkHttp3ClientFactory
import okhttp3.OkHttpClient

/**
 * Allows multiple [OkHttp3ClientFactory]s to be used based on an [HttpClientConfig].
 */
class CompositeOkHttpClientFactory(
  private val factories: List<OkHttp3ClientFactory>
) : OkHttp3ClientFactory {

  override fun supports(baseUrl: String): Boolean = true

  override fun normalizeBaseUrl(baseUrl: String): String {
    return factories
      .firstOrNull { it.supports(baseUrl) }
      ?.normalizeBaseUrl(baseUrl)
      ?: throw IntegrationException("No HttpClientFactory supports the provided baseUrl: $baseUrl")
  }

  override fun create(baseUrl: String, config: HttpClientConfig): OkHttpClient {
    return factories
      .firstOrNull { it.supports(baseUrl) }
      ?.create(baseUrl, config)
      ?: throw IntegrationException("No HttpClientFactory supports the provided baseUrl: $baseUrl")
  }
}
