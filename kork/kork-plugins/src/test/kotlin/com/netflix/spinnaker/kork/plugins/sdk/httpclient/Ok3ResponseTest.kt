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
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import strikt.api.expectThat
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class Ok3ResponseTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("should flag the response as success") {
      expectThat(status200Subject.isError).isFalse()
    }

    test("should flag the response as error if exception received") {
      expectThat(exceptionSubject.isError).isTrue()
    }

    test("should flag the response as error if response received with status > 4xx or 5xx") {
      expectThat(status404Subject.isError).isTrue()
      expectThat(status500Subject.isError).isTrue()
    }
  }

  private inner class Fixture {
    val objectMapper: ObjectMapper = mockk(relaxed = true)
    val response404: Response = buildResponse(404)
    val response500: Response = buildResponse(500)
    val response200: Response = buildResponse(200)
    val exceptionSubject = Ok3Response(objectMapper, null, IOException("error"))
    val status404Subject = Ok3Response(objectMapper, response404, null)
    val status500Subject = Ok3Response(objectMapper, response500, null)
    val status200Subject = Ok3Response(objectMapper, response200, null)
  }

  private fun buildResponse(code: Int): Response {
    return Response.Builder().code(code)
      .request(mockk(relaxed = true))
      .message("OK")
      .protocol(Protocol.HTTP_1_1)
      .header("Content-Type", "plain/text")
      .body("test".toResponseBody(("plain/text").toMediaType()))
      .build()
  }
}
