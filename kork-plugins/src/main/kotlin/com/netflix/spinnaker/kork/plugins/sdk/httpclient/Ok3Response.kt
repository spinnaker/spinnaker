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
import okhttp3.ResponseBody
import java.io.InputStream
import java.lang.Exception
import java.util.Optional

class Ok3Response(
  private val objectMapper: ObjectMapper,
  private val body: ResponseBody?,
  private val exception: Exception?,
  private val statusCode: Int,
  private val headers: Map<String, String>
) : Response {
  override fun getBody(): InputStream? = body?.byteStream()

  override fun <T : Any> getBody(expectedType: Class<T>): T? {
    if (body == null) {
      return null
    }
    return objectMapper.convertValue(body.byteStream(), expectedType)
  }

  override fun getException(): Optional<Exception> =
    Optional.ofNullable(exception)

  override fun getStatusCode(): Int =
    statusCode

  override fun getHeaders(): Map<String, String> =
    headers
}
