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
import org.pf4j.PluginState
import org.pf4j.PluginWrapper

class PluginBeanPostProcessorTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }
  }

  private class Fixture {
    val pluginManager: SpinnakerPluginManager = mockk(relaxed = true)
    val extensionsInjector: ExtensionsInjector = mockk(relaxed = true)
    val pluginWrapper: PluginWrapper = mockk(relaxed = true)

    val subject = PluginBeanPostProcessor(pluginManager, extensionsInjector)

    init {
      every { pluginWrapper.plugin } returns TestSpringPlugin(pluginWrapper)
      every { pluginWrapper.pluginId } returns "testSpringPlugin"
      every { pluginManager.getPlugins(eq(PluginState.STARTED)) } returns listOf(pluginWrapper)
    }
  }
}
