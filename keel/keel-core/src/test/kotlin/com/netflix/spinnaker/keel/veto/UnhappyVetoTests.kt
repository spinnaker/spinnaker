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

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.config.UnhappyVetoConfig
import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.UnhappyVetoRepository
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.keel.veto.unhappy.UnhappyVeto
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.springframework.core.env.Environment
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.isNotNull
import java.time.Duration

class UnhappyVetoTests : JUnit5Minutests {

  internal class Fixture {
    val clock = MutableClock()
    val unhappyRepository: UnhappyVetoRepository = mockk(relaxUnitFun = true)
    val diffFingerprintRepository: DiffFingerprintRepository = mockk()
    val resourceRepository: ResourceRepository = mockk()
    val config = UnhappyVetoConfig()
    private val springEnv: Environment = mockk(relaxUnitFun = true) {
      every {
        getProperty("keel.unhappy.maxRetries", Int::class.java, any())
      } returns config.maxRetries
      every {
        getProperty("keel.unhappy.timeBetweenRetries", Duration::class.java, any())
      } returns config.timeBetweenRetries
    }
    val subject = UnhappyVeto(
      diffFingerprintRepository,
      unhappyRepository,
      resourceRepository,
      springEnv,
      config,
      NoopRegistry(),
      clock
    )

    val r = resource()
    var result: VetoResponse? = null
    fun check() {
      result = runBlocking { subject.check(r) }
    }
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("number of actions taken on resource diff is 0") {
      before {
        every { diffFingerprintRepository.actionTakenCount(r.id) } returns 0
        check()
      }

      test("resource is not vetoed") {
        expectThat(result).isNotNull().isAllowed()
      }

      test("veto status is cleared") {
        verify { unhappyRepository.delete(r) }
      }
    }

    context("number of actions taken on resource diff is 1") {
      before {
        every { diffFingerprintRepository.actionTakenCount(r.id) } returns 1

        every {
          unhappyRepository.markUnhappy(r, any())
        } just Runs

        every {
          unhappyRepository.getRecheckTime(r)
        } returns null

        clock.reset()
        check()
      }


      test("the resource check is denied") {
        expectThat(result).isNotNull().isNotAllowed()
      }

      test("veto status is not cleared") {
        verify(exactly = 0) { unhappyRepository.delete(r) }
      }

      test("resource is marked unhappy") {
        verify(exactly = 1) { unhappyRepository.markUnhappy(r, clock.instant() + config.timeBetweenRetries) }
      }
    }

    context("num actions taken is 1, but waiting time is over") {
      before {
        every { diffFingerprintRepository.actionTakenCount(r.id) } returns 1

        every {
          unhappyRepository.markUnhappy(r, any())
        } just Runs

        every {
          unhappyRepository.getRecheckTime(r)
        } returns clock.instant().minusSeconds(60)

        clock.reset()
        check()
      }

      test("recheck is allowed after the waiting time") {
        check()
        expectThat(result).isNotNull().isAllowed()
      }
    }


    context("number of actions taken on resource diff is more than max actions") {
      before {
        every { diffFingerprintRepository.actionTakenCount(r.id) } returns config.maxRetries

        every {
          unhappyRepository.markUnhappy(r, any())
        } just Runs

        clock.reset()
        check()
      }


      test("the resource is vetoed") {
        expectThat(result).isNotNull().isNotAllowed()
      }

      test("veto status is not cleared") {
        verify(exactly = 0) { unhappyRepository.delete(r) }
      }

      test("resource is marked unhappy") {
        verify(exactly = 1) { unhappyRepository.markUnhappy(r, null) }
      }
    }

    context("clearing a rejection") {
      before {
        every { resourceRepository.get(any()) } returns r
        every { diffFingerprintRepository.clear(any()) } just Runs
      }

      test("clears the veto and the diff") {
        subject.clearVeto(r.id)

        verify(exactly = 1) { unhappyRepository.delete(r) }
        verify(exactly = 1) { diffFingerprintRepository.clear(r.id) }
      }
    }
  }

  fun Assertion.Builder<VetoResponse>.isAllowed() = assertThat("is allowed") { it.allowed }
  fun Assertion.Builder<VetoResponse>.isNotAllowed() = assertThat("is not allowed") { !it.allowed }
}
