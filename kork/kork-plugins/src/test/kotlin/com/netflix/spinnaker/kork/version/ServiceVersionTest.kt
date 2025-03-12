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

package com.netflix.spinnaker.kork.version

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import org.springframework.context.ApplicationContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class ServiceVersionTest : JUnit5Minutests {

  fun tests() = rootContext {

    derivedContext<KnownVersionFixture>("Known version test") {
      fixture {
        KnownVersionFixture()
      }

      test("should resolve to known version") {
        expectThat(subject.resolve()).isEqualTo("1.0.0")
      }
    }

    derivedContext<UnKnownVersionFixture>("Unknown version test") {
      fixture {
        UnKnownVersionFixture()
      }

      test("should resolve to unknown version") {
        expectThat(subject.resolve()).isEqualTo(ServiceVersion.UNKNOWN_VERSION)
      }
    }
  }

  private inner class KnownVersionFixture {
    val applicationContext: ApplicationContext = mockk(relaxed = true)
    val resolver: VersionResolver = mockk(relaxed = true)
    val subject = ServiceVersion(applicationContext, listOf(resolver))

    init {
      every { applicationContext.applicationName } returns "test"
      every { resolver.resolve("test") } returns "1.0.0"
    }
  }

  private inner class UnKnownVersionFixture {
    val applicationContext: ApplicationContext = mockk(relaxed = true)
    val resolver: VersionResolver = mockk(relaxed = true)
    val subject = ServiceVersion(applicationContext, listOf(resolver))
    init {
      every { resolver.resolve("test") } returns null
    }
  }
}
