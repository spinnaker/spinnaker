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

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.api.extension.ApiExtension
import com.netflix.spinnaker.gate.api.extension.HttpRequest
import com.netflix.spinnaker.gate.api.extension.HttpResponse
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import org.springframework.beans.factory.ObjectProvider
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEqualTo

class ApiExtensionControllerTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("basic validation") {
      test("raises SystemException when duplicate api extension identifiers are detected") {
        expectThrows<SystemException> {
          ApiExtensionController(ApiExtensionsProvider(listOf(firstApiExtension, firstApiExtension)))
        }
      }

      test("raises SystemException when duplicate api extension identifiers are detected") {
        ApiExtensionController(ApiExtensionsProvider(listOf(firstApiExtension)))
        ApiExtensionController(ApiExtensionsProvider(listOf(firstApiExtension, secondApiExtension)))
      }
    }

    context("basic request handling") {
      test("raises NotFoundException when api extension does not exist") {
        expectThrows<NotFoundException> {
          subject.any(
            "does-not-exist", emptyMap(), mockk(relaxed = true)
          )
        }
      }

      test("returns expected response") {
        expectThat(
          subject.any(
            firstApiExtension.id(), emptyMap(), mockk(relaxed = true)
          ).body
        ).isEqualTo("This is a result from ${firstApiExtension.id()}")

        expectThat(
          subject.any(
            secondApiExtension.id(), emptyMap(), mockk(relaxed = true)
          ).body
        ).isEqualTo("This is a result from ${secondApiExtension.id()}")
      }
    }
  }

  private inner class Fixture {
    val firstApiExtension = SimpleApiExtension("1")
    val secondApiExtension = SimpleApiExtension("2")
    val subject = ApiExtensionController(ApiExtensionsProvider(listOf(firstApiExtension, secondApiExtension)))
  }

  class SimpleApiExtension(private val id: String) : ApiExtension {
    override fun id(): String {
      return id
    }

    override fun handles(httpRequest: HttpRequest): Boolean {
      return true
    }

    override fun handle(httpRequest: HttpRequest): HttpResponse {
      return HttpResponse.of(200, emptyMap(), "This is a result from ${id()}")
    }
  }

  private inner class ApiExtensionsProvider(
    private val apiExtensions: List<ApiExtension>
  ) : ObjectProvider<List<ApiExtension>> {
    override fun getIfUnique(): List<ApiExtension>? = apiExtensions
    override fun getObject(vararg args: Any?): List<ApiExtension> = apiExtensions
    override fun getObject(): List<ApiExtension> = apiExtensions
    override fun getIfAvailable(): List<ApiExtension>? = apiExtensions
  }
}
