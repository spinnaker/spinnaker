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

import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.UnhappyVetoRepository
import com.netflix.spinnaker.keel.persistence.UnhappyVetoRepository.UnhappyVetoStatus
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.keel.veto.unhappy.UnhappyVeto
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import kotlinx.coroutines.runBlocking
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.isNotNull

class UnhappyVetoTests : JUnit5Minutests {

  internal class Fixture {
    val clock = MutableClock()
    val unhappyRepository: UnhappyVetoRepository = mockk(relaxUnitFun = true)
    val diffFingerprintRepository: DiffFingerprintRepository = mockk()
    private val dynamicConfigService: DynamicConfigService = mockk(relaxUnitFun = true) {
      every {
        getConfig(Int::class.java, "veto.unhappy.max-diff-count", any())
      } returns 5
      every {
        getConfig(String::class.java, "veto.unhappy.waiting-time", any())
      } returns "PT10M"
    }
    val subject = UnhappyVeto(diffFingerprintRepository, unhappyRepository, dynamicConfigService, "PT10M")

    val r = resource()
    var result: VetoResponse? = null
    fun check() {
      result = runBlocking { subject.check(r) }
    }
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("happy diffCount") {
      before {
        every { diffFingerprintRepository.diffCount(r.id) } returns 1
        check()
      }

      test("happy resources aren't vetoed") {
        expectThat(result).isNotNull().isAllowed()
      }

      test("veto status is cleared") {
        verify { unhappyRepository.delete(r.id) }
      }
    }

    context("unhappy diffCount") {
      context("diff has been seen more than 10 times") {
        before {
          every { diffFingerprintRepository.diffCount(r.id) } returns 11
          every { unhappyRepository.getOrCreateVetoStatus(r.id, r.application, any()) } returns UnhappyVetoStatus(shouldSkip = true)
          check()
        }

        test("unhappy resources are vetoed") {
          expectThat(result).isNotNull().isNotAllowed()
        }

        test("veto status is not cleared") {
          verify(exactly = 0) { unhappyRepository.delete(r.id) }
        }

        test("resource will be re-checked after wait time") {
          verify { unhappyRepository.getOrCreateVetoStatus(r.id, r.application, Duration.ofMinutes(10)) }
        }
      }

      context("diff has been seen less than 10 times") {
        before {
          every { diffFingerprintRepository.diffCount(r.id) } returns 4

          check()
        }

        test("resource not skipped") {
          expectThat(result).isNotNull().isAllowed()
        }
      }
    }
  }

  fun Assertion.Builder<VetoResponse>.isAllowed() = assertThat("is allowed") { it.allowed }
  fun Assertion.Builder<VetoResponse>.isNotAllowed() = assertThat("is not allowed") { !it.allowed }
}
