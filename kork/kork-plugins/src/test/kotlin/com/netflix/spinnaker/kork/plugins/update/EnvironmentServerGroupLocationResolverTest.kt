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
 *
 */
package com.netflix.spinnaker.kork.plugins.update

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import org.springframework.core.env.Environment
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

class EnvironmentServerGroupLocationResolverTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("returns null with no matches") {
      every { environment.getProperty(any()) } returns null
      expectThat(subject.get()).isNull()
    }

    test("returns value of existing property") {
      every { environment.getProperty(eq("EC2_REGION")) } returns "us-west-2"
      expectThat(subject.get()).isEqualTo("us-west-2")
    }
  }

  private inner class Fixture {
    val environment: Environment = mockk(relaxed = true)
    val subject = EnvironmentServerGroupLocationResolver(environment)
  }
}
