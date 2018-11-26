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

import com.netflix.spinnaker.clouddriver.scattergather.client.ScatteredOkHttpCallFactory.Companion.SCATTER_HEADER
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.springframework.mock.web.MockHttpServletRequest
import strikt.api.expectThat
import strikt.assertions.get
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

internal object ScatteredOkHttpCallFactorySpec : Spek({

  describe("creating a scattered request") {
    val okClient = OkHttpClient()
    val targets = mapOf(
      "one" to "http://clouddriver-one",
      "two" to "http://clouddriver-two"
    )

    given("an original servlet request without a body") {
      val servletRequest = MockHttpServletRequest("GET", "/hello").apply {
        addHeader("Accept", "application/json")
      }

      it("creates two requests") {
        val result = ScatteredOkHttpCallFactory(okClient).createCalls("workid", targets, servletRequest)

        expectThat(result) {
          get { size }.isEqualTo(2)
        }
        expectThat(result)[0].and {
          get { request().tag() }.isEqualTo("workid:one")
          get { request().method() }.isEqualTo("GET")
          get { request().url().toString() }.isEqualTo("http://clouddriver-one/hello")
          get { request().header("Accept") }.isEqualTo("application/json")
          get { request().header(SCATTER_HEADER) }.isEqualTo("1")
          get { request().body() }.isNull()
        }
        expectThat(result)[1].and {
          get { request().tag() }.isEqualTo("workid:two")
          get { request().method() }.isEqualTo("GET")
          get { request().url().toString() }.isEqualTo("http://clouddriver-two/hello")
          get { request().header("Accept") }.isEqualTo("application/json")
          get { request().header(SCATTER_HEADER) }.isEqualTo("1")
          get { request().body() }.isNull()
        }
      }
    }

    given("an original servlet request with a body") {
      val body = """{"hello": "world"}"""
      val servletRequest = MockHttpServletRequest("POST", "/ops").apply {
        addHeader("Content-Type", "application/json")
        setContent(body.toByteArray())
      }

      it("creates two requests with bodies") {
        val result = ScatteredOkHttpCallFactory(okClient).createCalls("workid", targets, servletRequest)

        expectThat(result) {
          get { size }.isEqualTo(2)
        }

        expectThat(result)[0].and {
          get { request().tag() }.isEqualTo("workid:one")
          get { request().method() }.isEqualTo("POST")
          get { request().url().toString() }.isEqualTo("http://clouddriver-one/ops")
          get { request().header("Content-Type") }.isEqualTo("application/json")
          get { request().header(SCATTER_HEADER) }.isEqualTo("1")
          get { request().readBody() }.isEqualTo(body)
        }
        expectThat(result)[1].and {
          get { request().tag() }.isEqualTo("workid:two")
          get { request().method() }.isEqualTo("POST")
          get { request().url().toString() }.isEqualTo("http://clouddriver-two/ops")
          get { request().header("Content-Type") }.isEqualTo("application/json")
          get { request().header(SCATTER_HEADER) }.isEqualTo("1")
          get { request().readBody() }.isEqualTo(body)
        }
      }
    }
  }
})

private fun Request.readBody(): String? =
  body()?.let {
    val sink = Buffer()
    it.writeTo(sink)
    sink.readUtf8()
  }
