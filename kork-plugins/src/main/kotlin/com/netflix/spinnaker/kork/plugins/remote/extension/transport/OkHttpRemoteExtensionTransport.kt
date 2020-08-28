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

package com.netflix.spinnaker.kork.plugins.remote.extension.transport

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.plugins.remote.extension.RemoteExtensionPayload
import com.netflix.spinnaker.security.AuthenticatedRequest
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

class OkHttpRemoteExtensionTransport(
  private val objectMapper: ObjectMapper,
  private val client: OkHttpClient,
  private val url: String
) : RemoteExtensionTransport {

  override fun invoke(remoteExtensionPayload: RemoteExtensionPayload) {
    AuthenticatedRequest.propagate {
      val headersBuilder: Headers.Builder = Headers.Builder()

      AuthenticatedRequest.getAuthenticationHeaders().forEach { (key, value) ->
        if (value.isPresent) {
          headersBuilder.add(key.toString(), value.get())
        }
      }

      val request = Request.Builder()
        .url(url)
        .headers(headersBuilder.build())
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
}

class OkHttpRemoteExtensionTransportException(
  reason: String
) : IntegrationException("Unable to invoke remote extension due to unexpected error: $reason")
