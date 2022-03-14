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

package com.netflix.spinnaker.echo.plugins.test

import com.netflix.spinnaker.echo.Application
import com.netflix.spinnaker.echo.plugins.EchoPlugin
import com.netflix.spinnaker.echo.plugins.EventListenerExtension
import com.netflix.spinnaker.echo.plugins.NotificationAgentExtension
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager
import com.netflix.spinnaker.kork.plugins.internal.PluginJar
import com.netflix.spinnaker.kork.plugins.tck.PluginsTckFixture
import java.io.File
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource

class EchoPluginsFixture : PluginsTckFixture, EchoTestService() {

  override val plugins = File("build/plugins")

  override val enabledPlugin: PluginJar
  override val disabledPlugin: PluginJar
  override val versionNotSupportedPlugin: PluginJar

  override val extensionClassNames: MutableList<String> = mutableListOf(
    EventListenerExtension::class.java.name,
    NotificationAgentExtension::class.java.name
  )

  final override fun buildPlugin(pluginId: String, systemVersionRequirement: String): PluginJar {
    return PluginJar.Builder(plugins.toPath().resolve("$pluginId.jar"), pluginId)
      .pluginClass(EchoPlugin::class.java.name)
      .pluginVersion("1.0.0")
      .manifestAttribute("Plugin-Requires", "echo$systemVersionRequirement")
      .extensions(extensionClassNames)
      .build()
  }

  @Autowired
  override lateinit var spinnakerPluginManager: SpinnakerPluginManager

  @Autowired
  lateinit var applicationContext: ApplicationContext

  init {
    plugins.delete()
    plugins.mkdir()
    enabledPlugin = buildPlugin("com.netflix.echo.enabled.plugin", ">=1.0.0")
    disabledPlugin = buildPlugin("com.netflix.echo.disabled.plugin", ">=1.0.0")
    // Make it very unlikely that the version of echo satisfies this requirement
    versionNotSupportedPlugin = buildPlugin("com.netflix.echo.version.not.supported.plugin", "=0.0.9")
  }
}

@SpringBootTest(classes = [Application::class])
@ContextConfiguration(classes = [PluginTestConfiguration::class])
@TestPropertySource(properties = ["spring.config.location=classpath:echo-plugins-test.yml"])
@AutoConfigureMockMvc
abstract class EchoTestService

@TestConfiguration
internal open class PluginTestConfiguration
