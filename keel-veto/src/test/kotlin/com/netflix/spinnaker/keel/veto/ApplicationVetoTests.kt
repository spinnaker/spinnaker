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

import com.netflix.spinnaker.keel.persistence.memory.InMemoryApplicationVetoRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.keel.veto.application.ApplicationVeto
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.hasSize
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class ApplicationVetoTests : JUnit5Minutests {
  private val appName = "keeldemo"
  private val friggaAppName = "test"
  private val r = resource()

  internal class Fixture {
    val vetoRepository = InMemoryApplicationVetoRepository()
    val resourceRepository = InMemoryResourceRepository()
    val subject = ApplicationVeto(vetoRepository, resourceRepository, configuredObjectMapper())
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("testing opt in flow") {
      after {
        vetoRepository.flush()
      }

      test("when no applications are opted out we allow any app") {
        val response = subject.check(r)
        expectThat(response.allowed).isTrue()
      }

      test("when the resource's app is excluded, we don't allow it") {
        subject.passMessage(mapOf(
          "application" to r.spec.application,
          "optedOut" to true
        ))

        val response = subject.check(r)
        expectThat(response.allowed).isFalse()
      }

      test("opting in/out works") {
        subject.passMessage(mapOf(
          "application" to appName,
          "optedOut" to true
        ))

        expectThat(subject.currentRejections())
          .hasSize(1)
          .contains(appName)

        subject.passMessage(mapOf(
          "application" to appName,
          "optedOut" to false
        ))

        expectThat(subject.currentRejections())
          .hasSize(0)
      }
    }
  }
}
