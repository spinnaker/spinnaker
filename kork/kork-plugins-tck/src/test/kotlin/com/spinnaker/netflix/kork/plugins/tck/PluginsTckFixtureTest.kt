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

package com.spinnaker.netflix.kork.plugins.tck

import com.netflix.spinnaker.config.PluginsAutoConfiguration
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager
import com.netflix.spinnaker.kork.plugins.internal.PluginJar
import com.netflix.spinnaker.kork.plugins.tck.PluginsTck
import com.netflix.spinnaker.kork.plugins.tck.PluginsTckFixture
import com.netflix.spinnaker.kork.plugins.tck.serviceFixture
import com.spinnaker.netflix.kork.plugins.SomeFeatureExtension
import com.spinnaker.netflix.kork.plugins.TestPlugin
import dev.minutest.rootContext
import java.io.File
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

class PluginsTckFixtureTest : PluginsTck<PluginsTckFixtureImpl>() {
  fun tests() = rootContext<PluginsTckFixtureImpl> {
    context("an orca integration test environment and an orca plugin") {
      serviceFixture {
        PluginsTckFixtureImpl()
      }

      before {
        spinnakerPluginManager.systemVersion = "1.0.0"
      }

      defaultPluginTests()
    }
  }
}

@SpringBootTest(
  classes = [StartupTestApp::class],
  properties = [
    "spring.application.name=kork",
    "spinnaker.extensibility.plugins-root-path=build/plugins",
    "spinnaker.extensibility.plugins.enabled.plugin.enabled=true",
    "spinnaker.extensibility.plugins.disabled.plugin.enabled=false",
    "spinnaker.extensibility.plugins.version.not.supported.plugin=true"
  ]
)
class PluginsTckFixtureImpl : PluginsTckFixture {

  final override val plugins: File = File("build/plugins")

  @Autowired
  final override lateinit var spinnakerPluginManager: SpinnakerPluginManager

  final override val enabledPlugin: PluginJar
  final override val disabledPlugin: PluginJar
  final override val versionNotSupportedPlugin: PluginJar

  override val extensionClassNames: MutableList<String> = mutableListOf(SomeFeatureExtension::class.java.name)

  final override fun buildPlugin(pluginId: String, systemVersionRequirement: String): PluginJar {
    return PluginJar.Builder(plugins.toPath().resolve("$pluginId.jar"), pluginId)
      .pluginClass(TestPlugin::class.java.name)
      .pluginVersion("1.0.0")
      .manifestAttribute("Plugin-Requires", "kork$systemVersionRequirement")
      .extensions(extensionClassNames)
      .build()
  }

  init {
    plugins.delete()
    plugins.mkdir()
    enabledPlugin = buildPlugin("enabled.plugin", ">=1.0.0")
    disabledPlugin = buildPlugin("disabled.plugin", ">=1.0.0")
    versionNotSupportedPlugin = buildPlugin("version.not.supported.plugin", ">=2.0.0")
  }
}

@SpringBootApplication
@Import(PluginsAutoConfiguration::class)
internal class StartupTestApp
