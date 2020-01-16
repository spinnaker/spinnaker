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
import org.pf4j.PluginDescriptor
import org.pf4j.PluginDescriptorFinder
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue
import java.nio.file.Path
import java.nio.file.Paths

class PluginRefPluginDescriptorFinderTests : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    test("delegates isApplicable to internal chain") {
      expectThat(subject.isApplicable(pluginRefPath)).isTrue()
    }

    test("delegates find to internal chain") {
      // TODO(cf): implement .equals on SpinnakerPluginDescriptor that accounts for lack of equals on DefaultPluginDescriptor..
      expectThat(subject.find(pluginRefPath)).isEqualTo(expectedDescriptor)
    }
  }

  private inner class Fixture {
    val expectedDescriptor: PluginDescriptor = SpinnakerPropertiesPluginDescriptorFinder().find(Paths.get(javaClass.getResource("/testplugin/plugin.properties").toURI()).parent)
    val pluginRefPath: Path = Paths.get(javaClass.getResource("/test.plugin-ref").toURI())
    val finder: PluginDescriptorFinder = SpinnakerPropertiesPluginDescriptorFinder()
    val subject = PluginRefPluginDescriptorFinder(finder)
  }
}
