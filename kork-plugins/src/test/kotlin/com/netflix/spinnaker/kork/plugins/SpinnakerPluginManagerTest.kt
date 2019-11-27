/*
 * Copyright 2019 Armory, Inc.
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
 */
package com.netflix.spinnaker.kork.plugins

import com.netflix.spinnaker.kork.plugins.config.ConfigCoordinates
import com.netflix.spinnaker.kork.plugins.config.ConfigResolver
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.pf4j.DefaultPluginDescriptor
import org.pf4j.PluginState
import org.pf4j.PluginStatusProvider
import org.pf4j.PluginWrapper
import strikt.api.expectThat
import strikt.assertions.isTrue
import java.nio.file.Paths

class SpinnakerPluginManagerTest : JUnit5Minutests {

  fun tests() = rootContext {

    test("SpinnakerPluginManager is initialized properly and usable") {
      val pluginManager = SpinnakerPluginManager(FakePluginStatusProvider(), FakeConfigResolver(), Paths.get("plugins"))
      val testPluginWrapper = PluginWrapper(
        pluginManager,
        DefaultPluginDescriptor(
          "TestPlugin",
          "desc",
          "TestPlugin.java",
          "1.0.0",
          "",
          "Armory",
          "Apache"
        ),
        null,
        null
      )
      testPluginWrapper.pluginState = PluginState.DISABLED
      pluginManager.setPlugins(listOf(testPluginWrapper))

      expectThat(pluginManager.enablePlugin("TestPlugin")).isTrue()
    }
  }
}

class FakePluginStatusProvider : PluginStatusProvider {
  override fun disablePlugin(pluginId: String?) {}
  override fun isPluginDisabled(pluginId: String?) = false
  override fun enablePlugin(pluginId: String?) {}
}

class FakeConfigResolver : ConfigResolver {
  override fun <T> resolve(coordinates: ConfigCoordinates, expectedType: Class<T>) = expectedType.newInstance()
}
