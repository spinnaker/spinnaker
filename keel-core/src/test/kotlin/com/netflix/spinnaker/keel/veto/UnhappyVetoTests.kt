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

import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.UnhappyVetoRepository
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.keel.veto.unhappy.UnhappyVeto
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import java.time.Duration
import java.time.Instant

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
    val subject = UnhappyVeto(diffFingerprintRepository, unhappyRepository, dynamicConfigService, "PT10M", clock)

    val r = resource()
    var result: VetoResponse? = null
    fun check() {
      result = runBlocking { subject.check(r) }
    }
    val recheckTime = slot<Instant>()
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("number of actions taken on resource diff within allowed limit") {
      before {
        every { diffFingerprintRepository.actionTakenCount(r.id) } returns 1
        check()
      }

      test("happy resources aren't vetoed") {
        expectThat(result).isNotNull().isAllowed()
      }

      test("veto status is cleared") {
        verify { unhappyRepository.delete(r.id) }
      }
    }

    context("number or actions taken on resource diff above allowed limit") {
      before {
        every { diffFingerprintRepository.actionTakenCount(r.id) } returns 11

        every {
          unhappyRepository.markUnhappy(r.id, r.application, capture(recheckTime))
        } just Runs

        every {
          unhappyRepository.getRecheckTime(r.id)
        } answers {
          if (recheckTime.isCaptured) recheckTime.captured else null
        }
      }

      context("recheck time has not yet expired") {
        before {
          clock.reset()
          check()
        }

        test("unhappy resources are vetoed") {
          expectThat(result).isNotNull().isNotAllowed()
        }

        test("veto status is not cleared") {
          verify(exactly = 0) { unhappyRepository.delete(r.id) }
        }

        test("recheck time is set") {
          expectThat(recheckTime.captured).isEqualTo(clock.instant() + Duration.ofMinutes(10))
        }

        context("some time elapsed but still before recheck time") {
          before {
            clock.tickMinutes(5)
            check()
          }

          test("resource still vetoed, but recheck time is untouched") {
            expectThat(result).isNotNull().isNotAllowed()
            expectThat(recheckTime.captured).isEqualTo(clock.instant() + Duration.ofMinutes(5))
          }
        }
      }

      context("recheck time has expired") {
        before {
          clock.reset()
          // first check sets the recheck time
          check()
          clock.tickMinutes(11)
          // second check compares the recheck time
          check()
        }

        test("should allow recheck of unhappy resource") {
          expectThat(result).isNotNull().isAllowed()
        }

        test("should update recheck time") {
          verify { unhappyRepository.markUnhappy(r.id, r.application, any()) }
          expectThat(recheckTime.captured).isEqualTo(clock.instant() + Duration.ofMinutes(10))
        }
      }

      context("with a null recheck expiry time") {
        before {
          every {
            unhappyRepository.getRecheckTime(r.id)
          } returns null
          check()
        }
  
        test("should veto unhappy resource") {
          expectThat(result).isNotNull().isNotAllowed()
        }
      }
    }
  }

  fun Assertion.Builder<VetoResponse>.isAllowed() = assertThat("is allowed") { it.allowed }
  fun Assertion.Builder<VetoResponse>.isNotAllowed() = assertThat("is not allowed") { !it.allowed }
}
