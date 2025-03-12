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

package com.spinnaker.netflix.kork.plugins.internal

import com.netflix.spinnaker.kork.plugins.internal.PluginJar
import com.spinnaker.netflix.kork.plugins.SomeFeatureExtension
import com.spinnaker.netflix.kork.plugins.TestPlugin
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarInputStream
import strikt.api.expect
import strikt.assertions.isEqualTo

class PluginJarTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    context("PluginJar") {
      fixture {
        Fixture()
      }

      test("Builder returns a PluginJar with all manifest attributes") {
        expect {
          that(pluginJar.pluginId).isEqualTo(pluginId)
          val manifest = FileInputStream(pluginJar.path.toFile()).use { inputStream ->
            val jarStream = JarInputStream(inputStream)
            jarStream.manifest
          }
          that(manifest.mainAttributes.getValue("Plugin-Id")).isEqualTo(pluginId)
          that(manifest.mainAttributes.getValue("Plugin-Requires")).isEqualTo(pluginRequires)
          that(manifest.mainAttributes.getValue("Plugin-Version")).isEqualTo("1.0.0")
          that(manifest.mainAttributes.getValue("Plugin-Class")).isEqualTo(TestPlugin::class.java.name)
        }
      }
    }
  }

  private class Fixture {
    val path: Path = Files.createTempDirectory("plugin-downloads")
    val pluginId = "kork.plugins.tck.plugin"
    var pluginRequires = "kork>=1.0.0"
    val pluginJar = PluginJar.Builder(path.resolve("$pluginId.jar"), pluginId)
      .pluginClass(TestPlugin::class.java.name)
      .pluginVersion("1.0.0")
      .manifestAttribute("Plugin-Requires", pluginRequires)
      .extension(SomeFeatureExtension::class.java.name)
      .build()
  }
}
