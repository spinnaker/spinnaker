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
package com.netflix.spinnaker.kork.plugins

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import org.pf4j.PluginWrapper
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class ExtensionsInjectorTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("spring plugin application context is autowired") {
      expectThat(subject.create(TestSpringPlugin.TestExtension::class.java))
        .isA<TestSpringPlugin.TestExtension>()
        .get { myConfig.something }
        .isEqualTo(10)
    }
  }

  private inner class Fixture {
    val pluginWrapper: PluginWrapper = mockk(relaxed = true)
    val pluginManager: SpinnakerPluginManager = mockk(relaxed = true)
    val subject = SpringExtensionFactory(pluginManager)

    init {
      every { pluginManager.whichPlugin(TestSpringPlugin.TestExtension::class.java) } returns pluginWrapper
      every { pluginWrapper.plugin } returns TestSpringPlugin(pluginWrapper)
      every { pluginWrapper.pluginClassLoader } returns javaClass.classLoader
    }
  }
}
