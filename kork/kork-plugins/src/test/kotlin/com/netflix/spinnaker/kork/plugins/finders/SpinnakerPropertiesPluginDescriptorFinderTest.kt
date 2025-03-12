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
import com.netflix.spinnaker.kork.plugins.internal.PluginZip
import com.netflix.spinnaker.kork.plugins.internal.TestPlugin
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import org.junit.jupiter.api.io.TempDir
import org.pf4j.PropertiesPluginDescriptorFinder
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue

class SpinnakerPropertiesPluginDescriptorFinderTest : JUnit5Minutests {

  @TempDir
  lateinit var pluginsPath: Path

  fun tests() = rootContext<Fixture> {
    fixture { Fixture(pluginsPath) }

    test("unsafe property is decorated into plugin descriptor") {
      val descriptorFinder = SpinnakerPropertiesPluginDescriptorFinder()

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
      val pluginPath = Files.createDirectory(pluginsPath.resolve("pf4j.test-plugin-1"))
      storePropertiesToPath(getPlugin1Properties(), pluginPath)
    }

    private fun getPlugin1Properties(): Properties {
      return PluginZip.createProperties(
        mapOf(
          PropertiesPluginDescriptorFinder.PLUGIN_ID to "pf4j.test-plugin-1",
          PropertiesPluginDescriptorFinder.PLUGIN_CLASS to TestPlugin::class.java.name,
          PropertiesPluginDescriptorFinder.PLUGIN_VERSION to "0.0.1",
          PropertiesPluginDescriptorFinder.PLUGIN_DESCRIPTION to "Test Plugin 1",
          PropertiesPluginDescriptorFinder.PLUGIN_PROVIDER to "Decebal Suiu",
          PropertiesPluginDescriptorFinder.PLUGIN_DEPENDENCIES to "foo.test-plugin-2,foo.test-plugin-3@~1.0",
          PropertiesPluginDescriptorFinder.PLUGIN_REQUIRES to ">=1",
          PropertiesPluginDescriptorFinder.PLUGIN_LICENSE to "Apache-2.0",
          SpinnakerPropertiesPluginDescriptorFinder.PLUGIN_UNSAFE to "true"
        )
      )
    }

    private fun storePropertiesToPath(properties: Properties, pluginPath: Path) {
      val path = pluginPath.resolve(PropertiesPluginDescriptorFinder.DEFAULT_PROPERTIES_FILE_NAME)
      OutputStreamWriter(FileOutputStream(path.toFile()), StandardCharsets.UTF_8)
        .use { writer -> properties.store(writer, "") }
    }
  }
}
