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

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.configureFor
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.netflix.spinnaker.kork.plugins.api.httpclient.Request
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import okhttp3.OkHttpClient
import strikt.api.expectThat
import strikt.assertions.containsKey
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNotNull

class Ok3HttpClientIntegrationTest : JUnit5Minutests {

  fun test() = rootContext<Fixture> {
    fixture { Fixture() }

    before {
      wiremock.start()
      configureFor(wiremock.port())
      configure()
    }

    test("can read response") {
      stubFor(get("/hello")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody("{\"message\": \"hello world!\"}")
        ))

      expectThat(subject.get(Request("helloworld", "/hello")))
        .and {
          get { isError }.isFalse()
          get { statusCode }.isEqualTo(200)
          get { headers }.and {
            containsKey("content-type")
            get { get("content-type") }.isEqualTo("application/json")
          }
          get { getBody(Response::class.java) }.and {
            isNotNull()
            get { message }.isEqualTo("hello world!")
          }
        }
    }
  }

  private data class Response(@JsonProperty("message") val message: String)

  private inner class Fixture {
    val wiremock = WireMockServer(options().dynamicPort())

    lateinit var subject: Ok3HttpClient

    fun configure() {
      subject = Ok3HttpClient(
        "test",
        wiremock.url(""),
        OkHttpClient(),
        ObjectMapper().apply {
          registerModule(KotlinModule())
        }
      )
    }
  }
}
