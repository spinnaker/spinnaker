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
package com.netflix.spinnaker.clouddriver.scattergather.reducer

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNullOrEmpty
import strikt.assertions.isTrue

internal object DeepMergeResponseReducerSpec : Spek({

  describe("a deep merge reducer") {
    val subject = DeepMergeResponseReducer()

    given("successful responses with bodies") {
      val response1 = createResponse(200, """
          {
            "obj": {
              "one": "one"
            },
            "list": [
              {
                "from": "one"
              }
            ],
            "nested": {
              "list": [
                {
                  "shared": "shared"
                },
                {
                  "one": "one"
                }
              ],
              "shared": {
                "one": "one"
              }
            },
            "one": "one",
            "conflict": "one"
          }
        """.trimIndent())
      val response2 = createResponse(200, """
          {
            "obj": {
              "two": "two"
            },
            "list": [
              {
                "from": "two"
              }
            ],
            "nested": {
              "list": [
                {
                  "shared": "shared"
                },
                {
                  "two": "two"
                }
              ],
              "shared": {
                "two": "two"
              }
            },
            "two": "two",
            "conflict": "two"
          }
        """.trimIndent())

      it("returns a reduced response") {
        val result = subject.reduce(listOf(response1, response2))

        val expectedBody = normalizeJson("""
          {
            "obj": {
              "one": "one",
              "two": "two"
            },
            "list": [
              {
                "from": "one"
              },
              {
                "from": "two"
              }
            ],
            "nested": {
              "list": [
                {
                  "shared": "shared"
                },
                {
                  "one": "one"
                },
                {
                  "two": "two"
                }
              ],
              "shared": {
                "one": "one",
                "two": "two"
              }
            },
            "one": "one",
            "conflict": "two",
            "two": "two"
          }
        """.trimIndent())

        expectThat(result) {
          get { status }.isEqualTo(200)
          get { headers }.isEqualTo(mapOf())
          get { contentType }.isEqualTo("application/json")
          get { characterEncoding }.isEqualTo("UTF-8")
          get { body }.isEqualTo(expectedBody)
          get { isError }.isEqualTo(false)
        }
      }
    }

    given("highest failure response body is propagated") {
      val response1 = createResponse(200, """
        {
          "obj": {
            "one": "one"
          },
          "list": [
            {
              "from": "one"
            }
          ],
          "nested": {
            "list": [
              {
                "shared": "shared"
              },
              {
                "one": "one"
              }
            ],
            "shared": {
              "one": "one"
            }
          },
          "one": "one",
          "conflict": "one"
        }
        """.trimIndent())
      val response2 = createResponse(500, """
        {
          "isEverythingTheWorst": true
        }
        """.trimIndent())

      it("returns a reduced response") {
        val result = subject.reduce(listOf(response1, response2))

        val expectedBody = normalizeJson("""
            {
              "isEverythingTheWorst": true
            }
        """.trimIndent())

        expectThat(result) {
          get { status }.isEqualTo(502)
          get { headers }.isEqualTo(mapOf())
          get { contentType }.isEqualTo("application/json")
          get { characterEncoding }.isEqualTo("UTF-8")
          get { body }.isEqualTo(expectedBody)
          get { isError }.isEqualTo(true)
        }
      }
    }

    given("successful responses without bodies") {
      val response1 = createResponse(200, null)
      val response2 = createResponse(200, null)

      it("returns a reduced response") {
        val result = subject.reduce(listOf(response1, response2))

        expectThat(result) {
          get { status }.isEqualTo(200)
          get { headers }.isEqualTo(mapOf())
          get { contentType }.isEqualTo("application/json")
          get { characterEncoding }.isEqualTo("UTF-8")
          get { body }.isNullOrEmpty()
          get { isError }.isFalse()
        }
      }
    }

    given("one successful response and a failed response") {
      val response1 = createResponse(200, null)
      val response2 = createResponse(500, null)

      it("returns a proxy error with an error code") {
        val result = subject.reduce(listOf(response1, response2))

        expectThat(result) {
          get { status }.isEqualTo(502)
          get { headers }.isEqualTo(mapOf())
          get { contentType }.isEqualTo("application/json")
          get { characterEncoding }.isEqualTo("UTF-8")
          get { body }.isNullOrEmpty()
          get { isError }.isTrue()
        }
      }
    }

    given("all failed responses") {
      val response1 = createResponse(500, null)
      val response2 = createResponse(500, null)

      it("returns a proxy error with an error code") {
        val result = subject.reduce(listOf(response1, response2))

        expectThat(result) {
          get { status }.isEqualTo(502)
          get { headers }.isEqualTo(mapOf())
          get { contentType }.isEqualTo("application/json")
          get { characterEncoding }.isEqualTo("UTF-8")
          get { body }.isNullOrEmpty()
          get { isError }.isTrue()
        }
      }
    }
  }
})

private fun normalizeJson(json: String): String =
  ObjectMapper().readTree(json).toString()

private fun createResponseBody(body: String): ResponseBody =
  ResponseBody.create(MediaType.parse("application/json"), normalizeJson(body))

private fun createResponse(code: Int, body: String?): Response {
  return Response.Builder()
    .let {
      it.request(Request.Builder()
        .url("http://localhost/hello")
        .build())
      it.protocol(Protocol.HTTP_1_1)
      it.code(code)
      if (body != null) {
        it.body(createResponseBody(body))
      }
      it.message("ca is on fire")
    }
    .build()
}
