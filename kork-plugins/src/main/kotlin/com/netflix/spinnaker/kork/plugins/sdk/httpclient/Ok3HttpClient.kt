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
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.plugins.api.httpclient.HttpClient
import com.netflix.spinnaker.kork.plugins.api.httpclient.Request
import com.netflix.spinnaker.kork.plugins.api.httpclient.Response
import java.io.IOException
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.slf4j.LoggerFactory

/**
 * An OkHttp3 [HttpClient] implementation.
 */
class Ok3HttpClient(
  internal val name: String,
  internal val baseUrl: String,
  private val client: OkHttpClient,
  private val objectMapper: ObjectMapper
) : HttpClient {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun get(request: Request): Response {
    return doRequest(request) {
      it.get()
    }
  }

  override fun post(request: Request): Response {
    return doRequest(request) {
      it.post(request.okHttpRequestBody())
    }
  }

  override fun put(request: Request): Response {
    return doRequest(request) {
      it.put(request.okHttpRequestBody())
    }
  }

  override fun delete(request: Request): Response {
    return doRequest(request) {
      if (request.body == null) {
        it.delete()
      } else {
        it.delete(request.okHttpRequestBody())
      }
    }
  }

  override fun patch(request: Request): Response {
    return doRequest(request) {
      it.patch(request.okHttpRequestBody())
    }
  }

  private fun doRequest(request: Request, builderCallback: (okhttp3.Request.Builder) -> Unit): Response {
    val okRequest = requestBuilder(request)
      .apply(builderCallback)
      .build()

    return try {
      client.newCall(okRequest).execute().use {
        it.toGenericResponse()
      }
    } catch (io: IOException) {
      log.error("${okRequest.tag()} request failed", io)

      Ok3Response(
        objectMapper = objectMapper,
        response = null,
        exception = io
      )
    }
  }

  private fun requestBuilder(request: Request): okhttp3.Request.Builder {
    val url = (baseUrl + request.path).replace("//", "/")
    val httpUrlBuilder = HttpUrl.parse(url)?.newBuilder()
      ?: throw IntegrationException("Unable to parse url '$baseUrl'")
    request.queryParams.forEach {
      httpUrlBuilder.addQueryParameter(it.key, it.value)
    }
    return okhttp3.Request.Builder()
      .tag("$name.${request.name}")
      .url(httpUrlBuilder.build())
      .headers(Headers.of(request.headers))
  }

  private fun Request.okHttpRequestBody(): RequestBody =
    RequestBody.create(MediaType.parse(contentType), objectMapper.writeValueAsString(body))

  private fun okhttp3.Response.toGenericResponse(): Response {
    return Ok3Response(
      objectMapper = objectMapper,
      response = this,
      exception = null
    )
  }
}
