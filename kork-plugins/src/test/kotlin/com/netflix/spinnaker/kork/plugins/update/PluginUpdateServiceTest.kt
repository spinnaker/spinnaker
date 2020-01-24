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
package com.netflix.spinnaker.kork.plugins.update

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager
import com.netflix.spinnaker.kork.plugins.internal.PluginZip
import com.netflix.spinnaker.kork.plugins.testplugin.TestPluginBuilder
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import org.pf4j.DefaultPluginStatusProvider
import org.pf4j.update.DefaultUpdateRepository
import org.pf4j.update.PluginInfo
import org.pf4j.update.UpdateManager
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.Date

class PluginUpdateServiceTest : JUnit5Minutests {

  fun tests() = rootContext<PluginUpdateService> {
    val paths = setupTestPluginInfra()

    fixture {
      val pluginManager = SpinnakerPluginManager(
        DefaultPluginStatusProvider(paths.plugins),
        mockk(),
        "kork",
        paths.plugins
      )

      PluginUpdateService(
        SpinnakerUpdateManager(
          pluginManager,
          listOf(DefaultUpdateRepository("testing", paths.repository.toUri().toURL()))
        ),
        pluginManager,
        "kork",
        mockk(relaxed = true)
      )
    }

    after { reset(paths) }

    context("no plugins installed locally") {
      before {
        changeRepository(updateManager, paths.repository, listOf(
          createPlugin(paths.repository)
        ))
      }

      test("no plugins loaded") {
        pluginManager.loadPlugins()
        expectThat(pluginManager.plugins).hasSize(0)
      }

      test("install new plugins") {
        installNewPlugins()

        expect {
          that(pluginManager.pluginsRoot.contains(
            paths.plugins.resolve("spinnaker.generatedtestplugin-0.0.1.zip"))
          )
        }
      }
    }

    context("local plugins are out-of-date") {
      before {
        addToLocalPlugins(createPlugin(paths.repository, "0.0.1"), paths).id
        changeRepository(updateManager, paths.repository, listOf(
          createPlugin(paths.repository, "0.0.2")
        ))
      }

      test("old plugins are loaded") {
        expect {
          pluginManager.loadPlugins()

          that(pluginManager.plugins)
            .describedAs("initial loaded plugins")
            .hasSize(1)
          that(pluginManager.getPlugin("spinnaker.generatedtestplugin").descriptor.version)
            .describedAs("loaded plugin version")
            .isEqualTo("0.0.1")
          that(updateManager.hasPluginUpdate("spinnaker.generatedtestplugin"))
            .describedAs("loaded plugin update status")
            .isTrue()
        }
      }

      test("out-of-date plugins are updated") {
        expect {
          pluginManager.loadPlugins()

          updateExistingPlugins()

          expect {
            that(pluginManager.pluginsRoot.contains(
              paths.plugins.resolve("spinnaker.generatedtestplugin-0.0.2.zip"))
            )
            that(!pluginManager.pluginsRoot.contains(
              paths.plugins.resolve("spinnaker.generatedtestplugin-0.0.1.zip"))
            )
          }
        }
      }
    }

    context("all local plugins are up-to-date") {
      before {
        changeRepository(updateManager, paths.repository, listOf(
          addToLocalPlugins(createPlugin(paths.repository), paths)
        ))
      }

      test("no updates are performed") {
        expect {
          pluginManager.loadPlugins()

          that(pluginManager.plugins)
            .describedAs("loaded plugins")
            .hasSize(1)
          that(pluginManager.getPlugin("spinnaker.generatedtestplugin").descriptor.version)
            .describedAs("loaded plugin version")
            .isEqualTo("0.0.1")
          that(updateManager.hasPluginUpdate("spinnaker.generatedtestplugin"))
            .describedAs("loaded plugin has update")
            .isFalse()

          updateExistingPlugins()

          that(pluginManager.getPlugin("spinnaker.generatedtestplugin").descriptor.version)
            .describedAs("updated plugin version")
            .isEqualTo("0.0.1")
        }
      }
    }
  }

  private fun reset(paths: TestPaths) {
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
  private fun changeRepository(updateManager: UpdateManager, repositoryPath: Path, plugins: List<PluginInfo>) {
    repositoryPath.resolve("plugins.json").toFile().writeText(ObjectMapper().writeValueAsString(plugins))
    updateManager.repositories.first().refresh()
  }

  /**
   * Copies a created plugin from the repository to the local `plugins` directory.
   */
  private fun addToLocalPlugins(pluginInfo: PluginInfo, paths: TestPaths): PluginInfo {
    val releaseVersion = pluginInfo.releases.first().version
    val pluginFilename = "${pluginInfo.id}-$releaseVersion.zip"
    Files.copy(
      paths.repository.resolve("${pluginInfo.id}/$releaseVersion/$pluginFilename"),
      paths.plugins.resolve(pluginFilename),
      StandardCopyOption.REPLACE_EXISTING
    )
    return pluginInfo
  }

  /**
   * Compiles a test plugin and generates a ZIP artifact of it inside the `repository` temp directory.
   *
   * Compilation is necessary in this case so that we can assert the plugin and its extensions are actually being
   * correctly loaded into the PluginClassLoader, rather than mistakenly resolving a type from the test source set.
   */
  private fun createPlugin(
    repository: Path,
    pluginVersion: String = "0.0.1",
    className: String = "Generated"
  ): PluginInfo {
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

    return PluginInfo().apply {
      id = pluginBuilder.pluginId
      name = pluginBuilder.name
      description = "A generated TestPlugin named $name"
      provider = "Spinnaker"
      releases = listOf(
        PluginInfo.PluginRelease().apply {
          requires = "kork"
          version = pluginBuilder.version
          date = Date.from(Instant.now())
          url = releasePath.toUri().toURL().toString()
        }
      )
    }
  }

  /**
   * Creates two sets of temporary directories:
   *
   * - `repository`, simulating a remote plugin repository that PF4J will download from
   * - `plugins`, simulating a local plugins directory that will be used by the PluginManager to load from
   */
  private fun setupTestPluginInfra(): TestPaths {
    val repositoryDir = Files.createTempDirectory("repository")
    val pluginsDir = Files.createTempDirectory("plugins")
    return TestPaths(pluginsDir, repositoryDir)
  }

  private inner class TestPaths(
    val plugins: Path,
    val repository: Path
  )
}
