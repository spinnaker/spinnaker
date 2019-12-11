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

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor
import com.netflix.spinnaker.kork.plugins.internal.PluginJar
import com.netflix.spinnaker.kork.plugins.internal.TestPlugin
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.junit.jupiter.api.io.TempDir
import org.pf4j.ManifestPluginDescriptorFinder
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Manifest

class SpinnakerManifestPluginDescriptorFinderTest : JUnit5Minutests {

  @TempDir
  lateinit var pluginsPath: Path

  fun tests() = rootContext<Fixture> {
    fixture { Fixture(pluginsPath) }

    test("unsafe property is decorated into plugin descriptor") {
      val descriptorFinder = SpinnakerManifestPluginDescriptorFinder()

      expectThat(descriptorFinder.find(pluginsPath.resolve("pf4j.test-plugin-1")))
        .isA<SpinnakerPluginDescriptor>()
        .and {
          get { pluginId }.isEqualTo("pf4j.test-plugin-1")
          get { unsafe }.isTrue()
        }
    }
  }

  private class Fixture(val pluginsPath: Path) {
    init {
      val pluginPath = Files.createDirectories(pluginsPath.resolve("pf4j.test-plugin-1"))
      storeManifestToPath(getPlugin1Manifest(), pluginPath)
    }

    private fun getPlugin1Manifest(): Manifest {
      return PluginJar.createManifest(mapOf(
        ManifestPluginDescriptorFinder.PLUGIN_ID to "pf4j.test-plugin-1",
        ManifestPluginDescriptorFinder.PLUGIN_CLASS to TestPlugin::class.java.name,
        ManifestPluginDescriptorFinder.PLUGIN_VERSION to "0.0.1",
        ManifestPluginDescriptorFinder.PLUGIN_DESCRIPTION to "Test Plugin 1",
        ManifestPluginDescriptorFinder.PLUGIN_PROVIDER to "Decebal Suiu",
        ManifestPluginDescriptorFinder.PLUGIN_DEPENDENCIES to "foo.test-plugin-2,foo.test-plugin-3@~1.0",
        ManifestPluginDescriptorFinder.PLUGIN_REQUIRES to "*",
        ManifestPluginDescriptorFinder.PLUGIN_LICENSE to "Apache-2.0",
        SpinnakerManifestPluginDescriptorFinder.PLUGIN_UNSAFE to "true"
      ))
    }

    private fun storeManifestToPath(manifest: Manifest, pluginPath: Path) {
      val path = Files.createDirectory(pluginPath.resolve("META-INF"))
      FileOutputStream(path.resolve("MANIFEST.MF").toFile()).use { output -> manifest.write(output) }
    }
  }
}
