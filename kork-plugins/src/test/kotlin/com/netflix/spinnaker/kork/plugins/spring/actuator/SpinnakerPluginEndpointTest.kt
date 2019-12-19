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

package com.netflix.spinnaker.kork.plugins.spring.actuator

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.assertThrows
import org.pf4j.DefaultPluginDescriptor
import org.pf4j.PluginDescriptor
import org.pf4j.PluginWrapper
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class SpinnakerPluginEndpointTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("endpoint should return list of plugins") {
      expectThat(subject.plugins())
        .isA<List<PluginDescriptor>>()
        .and {
          get { size }.isEqualTo(1)
        }
    }

    test("endpoint should throw plugin not found exception") {
      assertThrows<NotFoundException> { (subject.pluginById("abc")) }
    }

    test("endpoint should return plugin with matching pluginId") {
      expectThat(subject.pluginById("test"))
        .isA<PluginDescriptor>()
        .and {
          get { pluginId }.isEqualTo("test")
        }
    }
  }

  private inner class Fixture {
    val pluginManager: SpinnakerPluginManager = mockk(relaxed = true)
    val subject = SpinnakerPluginEndpoint(pluginManager)

    init {
      val pluginWrapper = PluginWrapper(pluginManager, SpinnakerPluginDescriptor(DefaultPluginDescriptor("test", "", "", "", "", "", "")), null, this.javaClass.classLoader)
      every { pluginManager.getPlugins() } returns listOf(pluginWrapper)
      every { pluginManager.getPlugin("abc") } returns null
      every { pluginManager.getPlugin("test") } returns pluginWrapper
    }
  }
}
