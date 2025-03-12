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

package com.netflix.spinnaker.kork.plugins.update.release.remote

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager
import com.netflix.spinnaker.kork.plugins.SpringPluginStatusProvider
import com.netflix.spinnaker.kork.plugins.SpringStrictPluginLoaderStatusProvider
import com.netflix.spinnaker.kork.plugins.update.SpinnakerUpdateManager
import com.netflix.spinnaker.kork.plugins.update.internal.SpinnakerPluginInfo
import com.netflix.spinnaker.kork.plugins.update.release.PluginInfoRelease
import com.netflix.spinnaker.kork.plugins.update.release.plugin1
import com.netflix.spinnaker.kork.plugins.update.release.plugin2
import com.netflix.spinnaker.kork.plugins.update.release.pluginWithRemoteExtension
import com.netflix.spinnaker.kork.plugins.update.release.provider.AggregatePluginInfoReleaseProvider
import com.netflix.spinnaker.kork.plugins.update.release.source.PluginInfoReleaseSource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class RemotePluginInfoReleaseCacheTest : JUnit5Minutests {
  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("A plugin with a remote extension is added to the cache") {
      every { updateManager.plugins } returns mutableListOf(plugin1, plugin2, pluginWithRemoteExtension)
      every { pluginStatusProvider.isPluginEnabled(any()) } returns true
      every { pluginManager.spinnakerVersionManager.checkVersionConstraint(any(), any()) } returns true

      subject.refresh()
      val result = subject.get(pluginWithRemoteExtension.id)
      expectThat(result).isA<PluginInfoRelease>()
        .get { result?.pluginId }.isEqualTo(pluginWithRemoteExtension.id)
    }
  }

  private class Fixture {
    val pluginInfoReleaseSources: List<PluginInfoReleaseSource> = mutableListOf(
      FirstPluginInfoReleaseSource(), SecondPluginInfoReleaseSource()
    )

    val applicationEventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    val pluginStatusProvider: SpringPluginStatusProvider = mockk(relaxed = true)
    val updateManager: SpinnakerUpdateManager = mockk(relaxed = true)
    val pluginManager: SpinnakerPluginManager = mockk(relaxed = true)
    val pluginLoaderStatusProvider: SpringStrictPluginLoaderStatusProvider = mockk(relaxed = true)

    val subject = RemotePluginInfoReleaseCache(
      AggregatePluginInfoReleaseProvider(pluginInfoReleaseSources, pluginLoaderStatusProvider),
      applicationEventPublisher,
      updateManager,
      pluginManager,
      pluginStatusProvider
    )
  }

  private class FirstPluginInfoReleaseSource : PluginInfoReleaseSource {
    override fun getReleases(pluginInfo: List<SpinnakerPluginInfo>): Set<PluginInfoRelease> {
      return mutableSetOf(PluginInfoRelease(plugin1.id, plugin1.getReleases()[0]), PluginInfoRelease(plugin2.id, plugin2.getReleases()[0]))
    }

    override fun getOrder(): Int = 0
  }

  private class SecondPluginInfoReleaseSource : PluginInfoReleaseSource {
    override fun getReleases(pluginInfo: List<SpinnakerPluginInfo>): Set<PluginInfoRelease> {
      return mutableSetOf(PluginInfoRelease(pluginWithRemoteExtension.id, pluginWithRemoteExtension.getReleases()[0]))
    }

    override fun getOrder(): Int = 1
  }
}
