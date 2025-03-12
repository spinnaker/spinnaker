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
import com.netflix.spinnaker.kork.plugins.sdk.IdResolver
import com.netflix.spinnaker.kork.plugins.sdk.SdkFactory
import com.netflix.spinnaker.kork.plugins.sdk.httpclient.internal.CompositeOkHttpClientFactory
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.springframework.core.env.Environment

/**
 * Creates [Ok3HttpClientRegistry]s for a given extension.
 */
class HttpClientSdkFactory(
  private val okHttpClientFactory: CompositeOkHttpClientFactory,
  private val environment: Environment,
  private val objectMapper: ObjectMapper,
  private val okHttp3ClientConfiguration: OkHttp3ClientConfiguration
) : SdkFactory {

  override fun create(pluginClass: Class<*>, pluginWrapper: PluginWrapper?): Any {
    return Ok3HttpClientRegistry(
      IdResolver.pluginOrExtensionId(pluginClass, pluginWrapper),
      environment,
      objectMapper,
      okHttpClientFactory,
      okHttp3ClientConfiguration
    )  }
}
