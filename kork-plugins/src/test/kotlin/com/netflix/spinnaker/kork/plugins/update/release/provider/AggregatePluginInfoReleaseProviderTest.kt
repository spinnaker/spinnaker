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

package com.netflix.spinnaker.kork.plugins.update.release.provider

import com.netflix.spinnaker.kork.plugins.SpringStrictPluginLoaderStatusProvider
import com.netflix.spinnaker.kork.plugins.update.internal.SpinnakerPluginInfo
import com.netflix.spinnaker.kork.plugins.update.release.PluginInfoRelease
import com.netflix.spinnaker.kork.plugins.update.release.plugin1
import com.netflix.spinnaker.kork.plugins.update.release.plugin2
import com.netflix.spinnaker.kork.plugins.update.release.plugin3
import com.netflix.spinnaker.kork.plugins.update.release.pluginNoReleases
import com.netflix.spinnaker.kork.plugins.update.release.source.PluginInfoReleaseSource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class AggregatePluginInfoReleaseProviderTest : JUnit5Minutests {
  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("Provides the releases for multiple plugins from multiple plugin sources") {
      val plugin1ExpectedRelease = plugin1.getReleases()[0]
      // LastPluginInfoReleaseSource modifies the plugin2 release via processReleases
      val plugin2ExpectedRelease = plugin2.getReleases()[2]
      val plugin3ExpectedRelease = plugin3.getReleases()[0]

      every { pluginLoaderStatusProvider.isStrictPluginLoading() } returns false

      val releases = subject.getReleases(pluginInfoList)

      expectThat(releases).isA<Set<PluginInfoRelease>>()
        .get { releases.size }.isEqualTo(3)
        .get { releases.find { it.pluginId == plugin1.id }?.props }.isEqualTo(plugin1ExpectedRelease)
        .get { releases.find { it.pluginId == plugin2.id }?.props }.isEqualTo(plugin2ExpectedRelease)
        .get { releases.find { it.pluginId == plugin3.id }?.props }.isEqualTo(plugin3ExpectedRelease)
    }

    test("Throws PluginReleaseNotFoundException if strict plugin loading is enabled and releases are not found for all provided plugin info objects") {
      every { pluginLoaderStatusProvider.isStrictPluginLoading() } returns true

      expectThrows<PluginReleaseNotFoundException> {
        subject.getReleases(pluginInfoList)
      }
    }
  }

  private class Fixture {
    val pluginInfoList = mutableListOf(plugin1, plugin2, plugin3, pluginNoReleases)
    val pluginInfoReleaseSources: List<PluginInfoReleaseSource> = mutableListOf(
      FirstPluginInfoReleaseSource(), SecondPluginInfoReleaseSource(), LastPluginInfoReleaseSource()
    )
    val pluginLoaderStatusProvider: SpringStrictPluginLoaderStatusProvider = mockk(relaxed = true)
    val subject = AggregatePluginInfoReleaseProvider(pluginInfoReleaseSources, pluginLoaderStatusProvider)
  }

  private class FirstPluginInfoReleaseSource : PluginInfoReleaseSource {
    override fun getReleases(pluginInfo: List<SpinnakerPluginInfo>): Set<PluginInfoRelease> {
      return mutableSetOf(PluginInfoRelease(plugin1.id, plugin1.getReleases()[0]), PluginInfoRelease(plugin2.id, plugin2.getReleases()[0]))
    }

    override fun getOrder(): Int = 0
  }

  private class SecondPluginInfoReleaseSource : PluginInfoReleaseSource {
    override fun getReleases(pluginInfo: List<SpinnakerPluginInfo>): Set<PluginInfoRelease> {
      return mutableSetOf(PluginInfoRelease(plugin3.id, plugin3.getReleases()[0]))
    }

    override fun getOrder(): Int = 1
  }

  private class LastPluginInfoReleaseSource : PluginInfoReleaseSource {
    override fun getReleases(pluginInfo: List<SpinnakerPluginInfo>): Set<PluginInfoRelease> {
      return mutableSetOf()
    }

    // Modify the set
    override fun processReleases(pluginInfoReleases: Set<PluginInfoRelease>) {
      pluginInfoReleases.forEach {
        if (it.pluginId == plugin2.id) {
          it.props = plugin2.getReleases()[2]
        }
      }
    }

    override fun getOrder(): Int = 2
  }
}
