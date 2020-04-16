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
import com.netflix.spinnaker.kork.plugins.api.httpclient.Request
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.containsKey
import strikt.assertions.isEqualTo
import strikt.assertions.message

class Ok3HttpClientTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("responses are mapped to sdk model") {
      val call: Call = mockk(relaxed = true)
      val response = Response.Builder()
        .request(mockk(relaxed = true))
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .header("Content-Type", "plain/text")
        .body(ResponseBody.create(MediaType.parse("plain/text"), "hi"))
        .build()

      every { okHttpClient.newCall(any()) } returns call
      every { call.execute() } returns response
      val map: HashMap<String, String> = hashMapOf("param1" to "value1")
      val request = Request("hello", "/").setQueryParams(map)
      expectThat(subject.get(request)).and {
        get { String(body.readBytes()) }.isEqualTo("hi")
        get { statusCode }.isEqualTo(200)
        get { headers }.containsKey("content-type")
      }
    }

    test("Invalid URL") {
      val request = Request("hello", "/")
      expectThrows<IntegrationException> {
        invalidSubject.get(request)
      }.and {
        message.isEqualTo("Unable to parse url 'smtp://example.net'")
      }
    }
  }

  private inner class Fixture {
    val okHttpClient: OkHttpClient = mockk(relaxed = true)
    val objectMapper: ObjectMapper = mockk(relaxed = true)

    val subject = Ok3HttpClient("foo", "http://example.net", okHttpClient, objectMapper)
    val invalidSubject = Ok3HttpClient("foo", "smtp://example.net", okHttpClient, objectMapper)
  }
}
