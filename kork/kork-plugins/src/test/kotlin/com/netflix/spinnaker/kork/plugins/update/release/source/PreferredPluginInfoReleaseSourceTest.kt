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

package com.netflix.spinnaker.kork.plugins.update.release.source

import com.netflix.spinnaker.kork.plugins.update.release.PluginInfoRelease
import com.netflix.spinnaker.kork.plugins.update.release.plugin1
import com.netflix.spinnaker.kork.plugins.update.release.plugin2
import com.netflix.spinnaker.kork.plugins.update.release.plugin3
import com.netflix.spinnaker.kork.plugins.update.release.pluginNoReleases
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class PreferredPluginInfoReleaseSourceTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("Gets a preferred release for a plugin") {
      val expectedRelease = plugin1.getReleases().find { it.preferred }!!

      val releases = subject.getReleases(pluginInfoList.subList(0, 1))

      expectThat(releases).isA<Set<PluginInfoRelease>>()
        .get { releases.size }.isEqualTo(1)
        .get { releases.first() }.isEqualTo(PluginInfoRelease(plugin1.id, expectedRelease))
    }

    test("Gets preferred releases for multiple plugins, skipping plugins with empty releases and no preferred releases") {
      val plugin1ExpectedRelease = plugin1.getReleases().find { it.preferred }!!
      val plugin2ExpectedRelease = plugin2.getReleases().find { it.preferred }!!

      val releases = subject.getReleases(pluginInfoList)

      expectThat(releases).isA<Set<PluginInfoRelease>>()
        .get { releases.size }.isEqualTo(2)
        .get { releases.find { it.pluginId == plugin1.id } }.isEqualTo(PluginInfoRelease(plugin1.id, plugin1ExpectedRelease))
        .get { releases.find { it.pluginId == plugin2.id } }.isEqualTo(PluginInfoRelease(plugin2.id, plugin2ExpectedRelease))
    }
  }

  private class Fixture {
    val pluginInfoList = mutableListOf(plugin1, plugin2, plugin3, pluginNoReleases)
    val subject = PreferredPluginInfoReleaseSource()
  }
}
