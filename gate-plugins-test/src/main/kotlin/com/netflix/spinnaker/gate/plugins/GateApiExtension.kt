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

import com.netflix.spinnaker.gate.api.extension.ApiExtension
import com.netflix.spinnaker.gate.api.extension.HttpRequest
import com.netflix.spinnaker.gate.api.extension.HttpResponse
import com.netflix.spinnaker.kork.plugins.api.PluginConfiguration
import com.netflix.spinnaker.kork.plugins.api.PluginSdks
import javax.annotation.Nonnull
import org.pf4j.Extension

@Extension
class GateApiExtension(
  private val pluginSdks: PluginSdks,
  private val apiExtensionConfigProperties: ApiExtensionConfigProperties
) : ApiExtension {

  override fun handles(@Nonnull httpRequest: HttpRequest): Boolean {
    return supportedGet(httpRequest) || supportedPost(httpRequest)
  }

  override fun handle(@Nonnull httpRequest: HttpRequest): HttpResponse {
    if (supportedGet(httpRequest)) {
      return get()
    }
    return echo(httpRequest)
  }

  private fun supportedGet(@Nonnull httpRequest: HttpRequest): Boolean {
    return httpRequest.method.equals("GET", ignoreCase = true) &&
      httpRequest.requestURI.endsWith("")
  }

  private fun supportedPost(@Nonnull httpRequest: HttpRequest): Boolean {
    return httpRequest.method.equals("PUT", ignoreCase = true) &&
      httpRequest.requestURI.endsWith("/echo")
  }

  private fun get(): HttpResponse {
    return HttpResponse.of(204, emptyMap(), null)
  }

  private fun echo(@Nonnull httpRequest: HttpRequest): HttpResponse {
    val echo = httpRequest.parameters["parameter"]
    return HttpResponse.of(200, emptyMap(), echo)
  }

  override fun id(): String {
    return apiExtensionConfigProperties.id
  }
}

@PluginConfiguration("api-extension")
class ApiExtensionConfigProperties {
  var id: String = ""
}
