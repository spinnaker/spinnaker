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

import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import kotlinx.coroutines.runBlocking
import strikt.api.expectThat
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class VetoEnforcerTests : JUnit5Minutests {

  internal class Fixture(
    val veto: DummyVeto
  ) {
    val subject = VetoEnforcer(listOf(veto))

    val r = resource()

    fun canCheck() = runBlocking { subject.canCheck(r) }
  }

  fun allowTests() = rootContext<Fixture> {
    fixture {
      Fixture(
        DummyVeto(true)
      )
    }

    context("always allow veto") {
      test("resource gets checked") {
        val response = canCheck()
        expectThat(response.allowed).isTrue()
      }
    }

    fun denyTests() = rootContext<Fixture> {
      fixture {
        Fixture(
          DummyVeto(false)
        )
      }

      context("always deny veto") {
        test("when we have one deny we deny overall") {
          val response = canCheck()
          expectThat(response.allowed).isFalse()
        }
      }
    }
  }
}
