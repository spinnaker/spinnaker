/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.kork.plugins.finders

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.pf4j.CompoundPluginDescriptorFinder
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue
import java.nio.file.Paths

class SpinnakerPluginDescriptorFinderTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    test("delegates isApplicable to internal chain") {
      every { finder.isApplicable(any()) } returns true
      expectThat(subject.isApplicable(Paths.get("/somewhere"))).isTrue()
      verify(exactly = 1) { finder.isApplicable(any()) }
    }

    test("delegates find to internal chain") {
      every { finder.find(any()) } returns pluginDescriptor
      expectThat(subject.find(Paths.get("/somewhere/plugin"))).isEqualTo(pluginDescriptor)
    }
  }

  internal class Fixture {
    val finder: CompoundPluginDescriptorFinder = mockk(relaxed = true)
    val subject = SpinnakerPluginDescriptorFinder(finder)
  }
}
