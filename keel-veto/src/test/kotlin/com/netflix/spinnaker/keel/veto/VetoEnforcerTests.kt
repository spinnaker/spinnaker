/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 *
 */
package com.netflix.spinnaker.keel.veto

import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.persistence.memory.InMemoryApplicationVetoRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.veto.application.ApplicationVeto
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class VetoEnforcerTests : JUnit5Minutests {

  val appName = "myapp"

  internal class Fixture {
    val applicationVetoRepository = InMemoryApplicationVetoRepository()
    private val resourceRepository = InMemoryResourceRepository()
    val applicationVeto = ApplicationVeto(applicationVetoRepository, resourceRepository, configuredObjectMapper())
    val subject = VetoEnforcer(listOf(applicationVeto))
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("enforcing things") {
      after {
        applicationVetoRepository.flush()
      }

      test("no vetos means it's allowed") {
        val response = subject.canCheck(ResourceId(appName))
        expectThat(response.allowed).isTrue()
      }

      test("when we have one deny we deny overall") {
        applicationVeto.passMessage(
          mapOf(
            "application" to appName,
            "optedOut" to true
          )
        )

        val response = subject.canCheck(ResourceId(appName))
        expectThat(response.allowed).isFalse()
      }
    }
  }
}
