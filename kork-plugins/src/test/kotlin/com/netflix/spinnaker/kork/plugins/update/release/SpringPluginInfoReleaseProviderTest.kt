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
 */

package com.netflix.spinnaker.kork.plugins.update.release

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager
import com.netflix.spinnaker.kork.plugins.SpinnakerServiceVersionManager
import com.netflix.spinnaker.kork.plugins.SpringPluginStatusProvider
import com.netflix.spinnaker.kork.plugins.update.SpinnakerUpdateManager
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.Date
import org.pf4j.update.PluginInfo
import org.springframework.core.env.ConfigurableEnvironment
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class SpringPluginInfoReleaseProviderTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("Gets a release for the enabled plugin where plugin version and system version constraints are met") {
      val expectedRelease = plugin1.releases.first()
      every { environment.getProperty("spinnaker.extensibility.plugins.${plugin1.id}.enabled") } returns "true"
      every { environment.getProperty("spinnaker.extensibility.plugins.${plugin2.id}.enabled") } returns "false"
      every { pluginStatusProvider.pluginVersion(plugin1.id) } returns expectedRelease.version
      every { pluginManager.systemVersion } returns "2.1.0"

      val releases = subject.getReleases(pluginInfoList)

      expectThat(releases).isA<Set<PluginInfoRelease>>()
        .get { releases.filterNotNull().size }.isEqualTo(1)
        .get { releases.first() }.isEqualTo(PluginInfoRelease(plugin1.id, expectedRelease))
    }

    test("Gets releases for multiple enabled plugins where plugin version and system version constraints are met") {
      val plugin1ExpectedRelease = plugin1.releases.first()
      val plugin2ExpectedRelease = plugin2.releases.first()
      every { environment.getProperty("spinnaker.extensibility.plugins.${plugin1.id}.enabled") } returns "true"
      every { environment.getProperty("spinnaker.extensibility.plugins.${plugin2.id}.enabled") } returns "true"
      every { pluginStatusProvider.pluginVersion(plugin1.id) } returns plugin1ExpectedRelease.version
      every { pluginStatusProvider.pluginVersion(plugin2.id) } returns plugin2ExpectedRelease.version
      every { pluginManager.systemVersion } returns "2.1.0"

      val releases = subject.getReleases(pluginInfoList)

      expectThat(releases).isA<Set<PluginInfoRelease>>()
        .get { releases.filterNotNull().size }.isEqualTo(2)
        .get { releases.find { it?.pluginId == plugin1.id } }.isEqualTo(PluginInfoRelease(plugin1.id, plugin1ExpectedRelease))
        .get { releases.find { it?.pluginId == plugin2.id } }.isEqualTo(PluginInfoRelease(plugin2.id, plugin2ExpectedRelease))
    }

    test("Fails to get a release due to unsatisfied system version, throws PluginNotFoundException") {
      val plugin1ExpectedRelease = plugin1.releases.first()
      val plugin2ExpectedRelease = plugin2.releases.first()
      every { environment.getProperty("spinnaker.extensibility.plugins.${plugin1.id}.enabled") } returns "true"
      every { environment.getProperty("spinnaker.extensibility.plugins.${plugin2.id}.enabled") } returns "true"
      every { pluginStatusProvider.pluginVersion(plugin1.id) } returns plugin1ExpectedRelease.version
      every { pluginStatusProvider.pluginVersion(plugin2.id) } returns plugin2ExpectedRelease.version

      // Plugin2 version 3.0.0 requires orca>=2.0.0
      every { pluginManager.systemVersion } returns "1.1.0"

      expectThrows<PluginReleaseNotFoundException> {
        subject.getReleases(pluginInfoList)
      }
    }

    test("Plugin1 version is not configured, falls back to latest version") {
      val plugin1LatestRelease = plugin1.releases.last()
      val plugin2ExpectedRelease = plugin2.releases.first()
      every { environment.getProperty("spinnaker.extensibility.plugins.${plugin1.id}.enabled") } returns "true"
      every { environment.getProperty("spinnaker.extensibility.plugins.${plugin2.id}.enabled") } returns "true"
      every { updateManager.getLastPluginRelease(plugin1.id) } returns plugin1LatestRelease
      every { pluginStatusProvider.pluginVersion(plugin2.id) } returns plugin2ExpectedRelease.version
      every { pluginManager.systemVersion } returns "2.2.0"

      val releases = subject.getReleases(pluginInfoList)

      expectThat(releases).isA<Set<PluginInfoRelease>>()
        .get { releases.filterNotNull().size }.isEqualTo(2)
        .get { releases.find { it?.pluginId == plugin1.id } }.isEqualTo(PluginInfoRelease(plugin1.id, plugin1LatestRelease))
        .get { releases.find { it?.pluginId == plugin2.id } }.isEqualTo(PluginInfoRelease(plugin2.id, plugin2ExpectedRelease))
    }

    test("Plugin1 version is not configured, falls back to latest version but a latest version can not be found - throws PluginNotFoundException") {
      val plugin2ExpectedRelease = plugin2.releases.first()
      every { environment.getProperty("spinnaker.extensibility.plugins.${plugin1.id}.enabled") } returns "true"
      every { environment.getProperty("spinnaker.extensibility.plugins.${plugin2.id}.enabled") } returns "true"
      every { updateManager.getLastPluginRelease(plugin1.id) } returns null
      every { pluginStatusProvider.pluginVersion(plugin2.id) } returns plugin2ExpectedRelease.version
      every { pluginManager.systemVersion } returns "2.2.0"

      expectThrows<PluginReleaseNotFoundException> {
        subject.getReleases(pluginInfoList)
      }
    }

    test("Gets a release from one plugin info object") {
      val expectedRelease = plugin1.releases.first()
      every { environment.getProperty("spinnaker.extensibility.plugins.${plugin1.id}.enabled") } returns "true"
      every { pluginStatusProvider.pluginVersion(plugin1.id) } returns expectedRelease.version
      every { pluginManager.systemVersion } returns "2.1.0"

      val release = subject.getRelease(plugin1)

      expectThat(release).isA<PluginInfoRelease>()
        .get { release }.isEqualTo(PluginInfoRelease(plugin1.id, expectedRelease))
    }
  }

  private class Fixture {
    val plugin1 = PluginInfo().apply {
      id = "com.netflix.plugin1"
      name = "plugin1"
      description = "A test plugin"
      provider = "netflix"
      releases = listOf(
        PluginInfo.PluginRelease().apply {
          requires = "orca>=1.0.0"
          version = "2.0.0"
          date = Date.from(Instant.now())
          url = "front50.com/plugin.zip"
        },
        PluginInfo.PluginRelease().apply {
          requires = "orca>=1.0.0"
          version = "3.0.0"
          date = Date.from(Instant.now())
          url = "front50.com/plugin.zip"
        }
      )
    }

    val plugin2 = PluginInfo().apply {
      id = "com.netflix.plugin2"
      name = "plugin2"
      description = "A test plugin"
      provider = "netflix"
      releases = listOf(
        PluginInfo.PluginRelease().apply {
          requires = "orca>=2.0.0"
          version = "3.0.0"
          date = Date.from(Instant.now())
          url = "front50.com/plugin.zip"
        },
        PluginInfo.PluginRelease().apply {
          requires = "orca>=1.0.0"
          version = "4.0.0"
          date = Date.from(Instant.now())
          url = "front50.com/plugin.zip"
        }
      )
    }

    val pluginInfoList = mutableListOf(plugin1, plugin2)

    val environment: ConfigurableEnvironment = mockk(relaxed = true)
    val pluginStatusProvider = SpringPluginStatusProvider(environment)
    val versionManager = SpinnakerServiceVersionManager("orca")
    val updateManager: SpinnakerUpdateManager = mockk(relaxed = true)
    val pluginManager: SpinnakerPluginManager = mockk(relaxed = true)

    val subject = SpringPluginInfoReleaseProvider(
      pluginStatusProvider, versionManager, updateManager, pluginManager)
  }
}
