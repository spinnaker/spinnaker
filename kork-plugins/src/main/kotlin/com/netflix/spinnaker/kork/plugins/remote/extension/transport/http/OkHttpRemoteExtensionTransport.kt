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

package com.netflix.spinnaker.kork.plugins.remote.extension.transport.http


import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.api.plugins.remote.RemoteExtensionConfig
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.plugins.remote.extension.transport.RemoteExtensionPayload
import com.netflix.spinnaker.kork.plugins.remote.extension.transport.RemoteExtensionQuery
import com.netflix.spinnaker.kork.plugins.remote.extension.transport.RemoteExtensionResponse
import com.netflix.spinnaker.kork.plugins.remote.extension.transport.RemoteExtensionTransport
import com.netflix.spinnaker.security.AuthenticatedRequest
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

/**
 * An HTTP [RemoteExtensionTransport], OkHttp for the client.
 */
class OkHttpRemoteExtensionTransport(
  private val objectMapper: ObjectMapper,
  private val client: OkHttpClient,
  private val httpConfig: RemoteExtensionConfig.RemoteExtensionTransportConfig.Http
) : RemoteExtensionTransport {

  private val url: HttpUrl

  init {
    url = buildUrl(emptyMap())
  }

  override fun invoke(remoteExtensionPayload: RemoteExtensionPayload) {
    AuthenticatedRequest.propagate {
      val request = Request.Builder()
        .url(url)
        .headers(buildHeaders(httpConfig.headers.invokeHeaders))
        .post(
          RequestBody.create(
            MediaType.parse("application/json"),
            objectMapper.writeValueAsString(remoteExtensionPayload)
          )
        )
        .build()

      val response = client.newCall(request).execute()
      if (!response.isSuccessful) {
        val reason = response.body()?.string() ?: "Unknown reason: ${response.code()}"
        throw OkHttpRemoteExtensionTransportException(reason)
      }
    }.call()
  }

  override fun write(remoteExtensionPayload: RemoteExtensionPayload): RemoteExtensionResponse {
    return AuthenticatedRequest.propagate {
      val request = Request.Builder()
        .url(url)
        .headers(buildHeaders(httpConfig.headers.writeHeaders))
        .post(
          RequestBody.create(
            MediaType.parse("application/json"),
            objectMapper.writeValueAsString(remoteExtensionPayload)
          )
        )
        .build()

      val response = client.newCall(request).execute()
      if (!response.isSuccessful) {
        val reason = response.body()?.string() ?: "Unknown reason: ${response.code()}"
        throw OkHttpRemoteExtensionTransportException(reason)
      }

      objectMapper.readValue(response.body()?.string(), RemoteExtensionResponse::class.java)
    }.call()
  }

  override fun read(remoteExtensionQuery: RemoteExtensionQuery): RemoteExtensionResponse {
    return AuthenticatedRequest.propagate {
      val request = Request.Builder()
        .url(buildUrl(remoteExtensionQuery.toParams()))
        .headers(buildHeaders(httpConfig.headers.readHeaders))
        .get()
        .build()

      val response = client.newCall(request).execute()
      if (!response.isSuccessful) {
        val reason = response.body()?.string() ?: "Unknown reason: ${response.code()}"
        throw OkHttpRemoteExtensionTransportException(reason)
      }

      objectMapper.readValue(response.body()?.string(), RemoteExtensionResponse::class.java)
    }.call()
  }

  private fun buildUrl(additionalParams: Map<String, String>): HttpUrl {
    val httpUrlBuilder = HttpUrl.parse(httpConfig.url)?.newBuilder()
      ?: throw IntegrationException("Unable to parse url '${httpConfig.url}'")

    (httpConfig.queryParams + additionalParams).forEach {
      httpUrlBuilder.addQueryParameter(it.key, it.value)
    }

    return httpUrlBuilder.build()
  }

  private fun buildHeaders(headers: Map<String, String>): Headers {
    val headersBuilder: Headers.Builder = Headers.Builder()

    AuthenticatedRequest.getAuthenticationHeaders().forEach { (key, value) ->
      if (value.isPresent) {
        headersBuilder.add(key.toString(), value.get())
      }
    }

    headers.forEach { (key, value) ->
      headersBuilder.add(key, value)
    }

    return headersBuilder.build()
  }

  private fun RemoteExtensionQuery.toParams(): Map<String, String> {
    return objectMapper.convertValue(this, object : TypeReference<Map<String, String>>() {})
  }
}

/**
 * Thrown when there is an issue performing a call to the remote extension.
 */
class OkHttpRemoteExtensionTransportException(
  reason: String
) : IntegrationException("Unable to invoke remote extension due to unexpected error: $reason")
