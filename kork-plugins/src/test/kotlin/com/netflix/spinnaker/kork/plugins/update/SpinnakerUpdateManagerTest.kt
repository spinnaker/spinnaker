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

package com.netflix.spinnaker.kork.plugins.update

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager
import com.netflix.spinnaker.kork.plugins.SpinnakerServiceVersionManager
import com.netflix.spinnaker.kork.plugins.bundle.PluginBundleExtractor
import com.netflix.spinnaker.kork.plugins.internal.PluginZip
import com.netflix.spinnaker.kork.plugins.testplugin.TestPluginBuilder
import com.netflix.spinnaker.kork.plugins.update.internal.SpinnakerPluginInfo
import com.netflix.spinnaker.kork.plugins.update.release.PluginInfoRelease
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.Date
import org.pf4j.DefaultPluginStatusProvider
import org.pf4j.update.DefaultUpdateRepository
import org.pf4j.update.PluginInfo
import org.pf4j.update.UpdateManager
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isNull

class SpinnakerUpdateManagerTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    after { reset(paths) }

    test("No plugins loaded, downloads new plugins") {
      pluginManager.loadPlugins()
      expectThat(pluginManager.plugins).hasSize(0)

      val plugin = createPlugin(paths.repository)
      changeRepository(subject, paths.repository, listOf(plugin))

      val releases = mutableSetOf(PluginInfoRelease(plugin.id, plugin.getReleases().first()))
      subject.downloadPluginReleases(releases)

      expect {
        that(pluginManager.pluginsRoot.contains(
          paths.plugins.resolve("spinnaker.generatedtestplugin-0.0.1.zip"))
        )
      }
    }

    test("Plugins loaded, download new releases") {
      addToLocalPlugins(createPlugin(paths.repository, "0.0.1"), paths).id

      val plugin = createPlugin(paths.repository, "0.0.2")
      changeRepository(subject, paths.repository, listOf(plugin))

      pluginManager.loadPlugins()
      expectThat(pluginManager.plugins).hasSize(1)

      val releases = mutableSetOf(PluginInfoRelease(plugin.id, plugin.getReleases().first()))
      subject.downloadPluginReleases(releases)

      expect {
        that(pluginManager.pluginsRoot.contains(
          paths.plugins.resolve("spinnaker.generatedtestplugin-0.0.2.zip"))
        )
      }

      // Previously loaded plugin deleted - we do not load plugins from SpinnakerUpdateManager
      expectThat(pluginManager.plugins).hasSize(0)
    }

    test("Plugins loaded with newer version, no need to download") {
      addToLocalPlugins(createPlugin(paths.repository, "0.0.2"), paths).id

      val plugin = createPlugin(paths.repository, "0.0.1")
      changeRepository(subject, paths.repository, listOf(plugin))

      pluginManager.loadPlugins()
      expectThat(pluginManager.plugins).hasSize(1)

      val releases = mutableSetOf(PluginInfoRelease(plugin.id, plugin.getReleases().first()))
      subject.downloadPluginReleases(releases)

      expect {
        that(pluginManager.pluginsRoot.contains(
          paths.plugins.resolve("spinnaker.generatedtestplugin-0.0.1.zip"))
        )
      }

      // Previously loaded plugin is still loaded
      expectThat(pluginManager.plugins).hasSize(1)

      // Get the plugin release matching the service name, if not found we should receive null
      expectThat(subject.getLastPluginRelease(plugin.id, "orca")).isA<PluginInfo.PluginRelease>()
      expectThat(subject.getLastPluginRelease(plugin.id, "deck")).isNull()
    }
  }

  private class Fixture {
    val paths = setupTestPluginInfra()
    val applicationEventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    val pluginManager = SpinnakerPluginManager(
      mockk(relaxed = true),
      SpinnakerServiceVersionManager("orca"),
      DefaultPluginStatusProvider(paths.plugins),
      mockk(relaxed = true),
      listOf(),
      "orca",
      paths.plugins,
      PluginBundleExtractor(mockk(relaxed = true))
    )

    val repositories = listOf(DefaultUpdateRepository("testing",
      paths.repository.toUri().toURL()))
    val subject = SpinnakerUpdateManager(applicationEventPublisher, pluginManager, repositories)

    private fun setupTestPluginInfra(): TestPaths {
      val repositoryDir = Files.createTempDirectory("repository")
      val pluginsDir = Files.createTempDirectory("plugins")
      return TestPaths(pluginsDir, repositoryDir)
    }

    inner class TestPaths(
      val plugins: Path,
      val repository: Path
    )

    /**
     * Compiles a test plugin and generates a ZIP artifact of it inside the `repository` temp directory.
     *
     * Compilation is necessary in this case so that we can assert the plugin and its extensions are actually being
     * correctly loaded into the PluginClassLoader, rather than mistakenly resolving a type from the test source set.
     */
    fun createPlugin(
      repository: Path,
      pluginVersion: String = "0.0.1",
      className: String = "Generated"
    ): SpinnakerPluginInfo {
      val generatedPluginPath = Files.createTempDirectory("generated-plugin")
      val pluginBuilder = TestPluginBuilder(
        pluginPath = generatedPluginPath,
        name = className,
        version = pluginVersion
      )
      pluginBuilder.build()

      val releasePath = File(repository.resolve("${pluginBuilder.pluginId}/$pluginVersion").toUri())
        .also { it.mkdirs() }
        .let { pluginRepositoryPath ->
          val pluginPath = pluginRepositoryPath.resolve("${pluginBuilder.pluginId}-$pluginVersion.zip").toPath()

          val zip = PluginZip.Builder(pluginPath, pluginBuilder.pluginId)
            .pluginClass(pluginBuilder.canonicalPluginClass)
            .pluginVersion(pluginBuilder.version)

          "classes/${pluginBuilder.canonicalPluginClass.replace(".", "/")}.class".let {
            zip.addFile(Paths.get(it), generatedPluginPath.resolve(it).toFile().readBytes())
          }
          "classes/${pluginBuilder.canonicalExtensionClass.replace(".", "/")}.class".let {
            zip.addFile(Paths.get(it), generatedPluginPath.resolve(it).toFile().readBytes())
          }
          zip.addFile(Paths.get("META-INF/extensions.idx"), pluginBuilder.canonicalExtensionClass)

          zip.build()

          pluginPath
        }

      return SpinnakerPluginInfo().apply {
        id = pluginBuilder.pluginId
        name = pluginBuilder.name
        description = "A generated TestPlugin named $name"
        provider = "Spinnaker"
        releases = listOf(
          SpinnakerPluginInfo.SpinnakerPluginRelease(false).apply {
            requires = "orca>=0.0.0"
            version = pluginBuilder.version
            date = Date.from(Instant.now())
            url = releasePath.toUri().toURL().toString()
          }
        )
      }
    }

    fun reset(paths: TestPaths) {
      fun Path.recreate() {
        toFile().deleteRecursively()
        toFile().mkdir()
      }
      paths.repository.recreate()
      paths.plugins.recreate()
    }

    /**
     * Alters the repository without creating a new UpdateRepository within the Fixture.
     */
    fun changeRepository(updateManager: UpdateManager, repositoryPath: Path, plugins: List<PluginInfo>) {
      repositoryPath.resolve("plugins.json").toFile().writeText(ObjectMapper().writeValueAsString(plugins))
      updateManager.repositories.first().refresh()
    }

    /**
     * Copies a created plugin from the repository to the local `plugins` directory.
     */
    fun addToLocalPlugins(pluginInfo: PluginInfo, paths: TestPaths): PluginInfo {
      val releaseVersion = pluginInfo.releases.first().version
      val pluginFilename = "${pluginInfo.id}-$releaseVersion.zip"
      Files.copy(
        paths.repository.resolve("${pluginInfo.id}/$releaseVersion/$pluginFilename"),
        paths.plugins.resolve(pluginFilename),
        StandardCopyOption.REPLACE_EXISTING
      )
      return pluginInfo
    }
  }
}
