/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.gate.api.extension.ProxyConfig
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

internal class Proxy(val config: ProxyConfig) {
  companion object {
    val logger = LoggerFactory.getLogger(ProxyConfig::class.java)
  }

  var okHttpClient = OkHttpClient()

  /**
   * Initialize the underlying [OkHttpClient].
   */
  fun init(okHttpClientProvider: OkHttpClientProvider) : Proxy {
    val okHttpClient = okHttpClientProvider.getClient(DefaultServiceEndpoint(
      "proxy__${config.id}", config.uri, config.additionalAttributes, false, false
    ))

    this.okHttpClient = okHttpClient
      .newBuilder()
      .connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
      .readTimeout(config.readTimeoutMs, TimeUnit.MILLISECONDS)
      .writeTimeout(config.writeTimeoutMs, TimeUnit.MILLISECONDS)
      .build()

    return this
  }
}
