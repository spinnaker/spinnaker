/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.scattergather.client

import okhttp3.Call
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import javax.servlet.http.HttpServletRequest

/**
 * Creates a collection of OkHttp3 [Call] objects from a map of targets and
 * an originating [HttpServletRequest].
 */
class ScatteredOkHttpCallFactory(
  private val okClient: OkHttpClient
) {

  /**
   * Creates a collection of [Call] objects for a particular scatter request.
   *
   * The caller should provide a unique [workId] so that if a [Call] which can
   * be used for request cancellation operations.
   *
   * The [targets] map expects a mapping of `shardName to shardBaseUrl`.
   */
  fun createCalls(
    workId: String,
    targets: Map<String, String>,
    originalRequest: HttpServletRequest
  ): List<Call> {
    val requestBody = getRequestBody(originalRequest)

    return targets.map { (targetName, baseUrl) ->
      val okRequest = Request.Builder()
        .tag("$workId:$targetName")
        .url(originalRequest.toUrl(baseUrl))
        .header(SCATTER_HEADER, "1")

      originalRequest.headerNames.asSequence().forEach {
        okRequest.header(it, originalRequest.getHeader(it))
      }

      okRequest.method(originalRequest.method, requestBody)

      okClient.newCall(okRequest.build())
    }
  }

  private fun getRequestBody(request: HttpServletRequest): RequestBody? =
    if (request.contentLength == -1) {
      null
    } else {
      RequestBody.create(
        MediaType.parse(request.contentType),
        request.reader.readText()
      )
    }

  private fun HttpServletRequest.toUrl(baseUrl: String): String {
    val url = "$baseUrl$requestURI"
    return if (queryString == null) url else "$url?$queryString"
  }

  companion object {
    const val SCATTER_HEADER = "X-Spinnaker-ScatteredRequest"
  }
}
