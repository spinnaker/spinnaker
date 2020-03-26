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
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.plugins.api.httpclient.HttpClientConfig
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.verify
import okhttp3.OkHttpClient
import org.springframework.core.env.Environment
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.containsKey
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class Ok3HttpClientRegistryTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    context("configuring clients") {
      before {
        every { okHttp3ClientFactory.create(any(), any()) } returns mockkClass(OkHttpClient::class, relaxed = true)
      }

      test("throws when no client configured") {
        expectThrows<IntegrationException> {
          subject.get("unknown")
        }
      }

      test("creates a new client") {
        val config = HttpClientConfig()

        subject.configure("myClient", "https://example.com", config)

        expectThat(subject.clients).and {
          hasSize(1)
          containsKey("io.spinnaker.example.myClient")
        }

        expectThat(subject.okClients).and {
          hasSize(1)
          containsKey(config)
        }

        verify(exactly = 1) { okHttp3ClientFactory.create(eq("https://example.com"), eq(config)) }
      }

      test("get a configured client") {
        every { okHttp3ClientFactory.normalizeBaseUrl(eq("https://example.com")) } returns "https://example.com"

        subject.configure("myClient", "https://example.com", HttpClientConfig())

        expectThat(subject.get("myClient"))
          .isA<Ok3HttpClient>()
          .and {
            get { name }.isEqualTo("io.spinnaker.example.myClient")
            get { baseUrl }.isEqualTo("https://example.com")
          }
      }
    }

    context("internal services") {
      test("non-existent service") {
        expectThrows<IntegrationException> {
          subject.get("unknown")
        }
      }

      listOf(
        "clouddriver.baseUrl",
        "services.clouddriver.baseUrl"
      ).forEach { property ->
        test("configures client for '$property'") {
          every { environment.getProperty(any()) } returns null
          every { environment.getProperty(eq(property)) } returns "https://clouddriver.com"

          expectThat(subject.getInternalService("clouddriver"))
            .isA<Ok3HttpClient>()
            .and {
              get { name }.isEqualTo("internal.clouddriver")
              get { baseUrl }.isEqualTo("https://clouddriver.com")
            }
        }
      }
    }
  }

  private inner class Fixture {
    val environment: Environment = mockk(relaxed = true)
    val objectMapper: ObjectMapper = mockk(relaxed = true)
    val okHttp3ClientFactory: OkHttp3ClientFactory = mockk(relaxed = true)
    val okHttp3ClientConfiguration: OkHttp3ClientConfiguration = mockk(relaxed = true)
    val internalServicesClient: OkHttpClient = mockk(relaxed = true)

    val subject = Ok3HttpClientRegistry(
      "io.spinnaker.example",
      environment,
      objectMapper,
      okHttp3ClientFactory,
      okHttp3ClientConfiguration
    )

    init {
      val builder: OkHttpClient.Builder = mockk(relaxed = true)
      every { builder.build() } returns internalServicesClient
      every { okHttp3ClientConfiguration.create() } returns builder
    }
  }
}
