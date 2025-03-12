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
import com.netflix.spinnaker.kork.plugins.api.httpclient.Response
import java.io.InputStream
import java.lang.Exception
import java.util.Optional
import okhttp3.ResponseBody
import org.slf4j.LoggerFactory

/**
 * An OkHttp-backed HTTP client [Response].
 */
class Ok3Response(
  private val objectMapper: ObjectMapper,
  private val response: okhttp3.Response?,
  private val exception: Exception?
) : Response {

  /**
   * Ok3HttpClient will close the response immediately after creating this response object.
   *
   * Using `peekBody` will copy the response body into a different scope so that the original can be closed
   * and this object can exist as long as it needs to.
   */
  private val responseBody: ResponseBody? = response?.peekBody(Long.MAX_VALUE)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun getBody(): InputStream? {
    return responseBody?.use { it.byteStream() }
  }

  override fun <T : Any> getBody(expectedType: Class<T>): T? {
    val body = responseBody?.use { it.string() } ?: return null
    return objectMapper.readValue(body, expectedType)
  }

  override fun getException(): Optional<Exception> =
    Optional.ofNullable(exception)

  override fun getStatusCode(): Int =
    response?.code ?: -1

  override fun getHeaders(): Map<String, String> =
    response
      ?.headers
      ?.toMultimap()
      ?.map { it.key to it.value.joinToString(",") }
      ?.toMap()
      ?: emptyMap()

  /**
   * This method should not be called by anyone, as it's the Java finalization method and will be used to cleanup
   * any open streams when the object is destroyed.
   */
  fun finalize() {
    try {
      response?.body?.close()
      responseBody?.close()
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
      log.warn("Failed to cleanup resource", e)
    }
  }
}
