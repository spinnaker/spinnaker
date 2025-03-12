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

import com.netflix.spinnaker.kork.plugins.api.httpclient.HttpClientConfig
import okhttp3.OkHttpClient

/**
 * Offers a configuration point to provide custom [OkHttpClient]s.
 *
 * In most cases, the [DefaultOkHttp3ClientFactory] will suffice, unless you need to support highly customized
 * clients (like Netflix and Metatron).
 */
interface OkHttp3ClientFactory {

  /**
   * Returns whether or not the factory supports the provided [baseUrl].
   */
  fun supports(baseUrl: String): Boolean

  /**
   * Allows custom client factories to modify the base URL before being used by the client.
   *
   * This can be handy for when you want to filter [OkHttp3ClientFactory] instances based on a custom (but fake)
   * HTTP scheme, and normalize it back to a URL that will actually work. For example, Netflix uses a custom
   * "metatron://my.base.url" format for differentiating between Metatron-secured instances and regular HTTP
   * services. This method is then used to change the scheme back to "https://".
   */
  fun normalizeBaseUrl(baseUrl: String): String = baseUrl

  /**
   * Creates an [OkHttpClient] with the provided [config].
   */
  fun create(baseUrl: String, config: HttpClientConfig): OkHttpClient
}
