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

package com.netflix.spinnaker.orca.plugins.test

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager
import com.netflix.spinnaker.kork.plugins.internal.PluginJar
import com.netflix.spinnaker.kork.plugins.tck.PluginsTckFixture
import com.netflix.spinnaker.orca.Main
import com.netflix.spinnaker.orca.StageResolver
import com.netflix.spinnaker.orca.TaskResolver
import com.netflix.spinnaker.orca.clouddriver.service.JobService
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.plugins.OrcaPlugin
import com.netflix.spinnaker.orca.plugins.PreconfiguredJobConfigurationProviderExtension
import com.netflix.spinnaker.orca.plugins.StageDefinitionBuilderExtension
import com.netflix.spinnaker.orca.plugins.TaskExtension1
import com.netflix.spinnaker.orca.plugins.TaskExtension2
import com.netflix.spinnaker.q.memory.InMemoryQueue
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.q.metrics.MonitorableQueue
import java.io.File
import java.time.Clock
import java.time.Duration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource

class OrcaPluginsFixture : PluginsTckFixture, OrcaTestService() {
  val objectMapper: ObjectMapper = ObjectMapper(YAMLFactory())
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

  final override val plugins = File("build/plugins")

  final override val enabledPlugin: PluginJar
  final override val disabledPlugin: PluginJar
  final override val versionNotSupportedPlugin: PluginJar

  override val extensionClassNames: MutableList<String> = mutableListOf(
    StageDefinitionBuilderExtension::class.java.name,
    PreconfiguredJobConfigurationProviderExtension::class.java.name,
    TaskExtension1::class.java.name,
    TaskExtension2::class.java.name
  )

  final override fun buildPlugin(pluginId: String, systemVersionRequirement: String): PluginJar {
    return PluginJar.Builder(plugins.toPath().resolve("$pluginId.jar"), pluginId)
      .pluginClass(OrcaPlugin::class.java.name)
      .pluginVersion("1.0.0")
      .manifestAttribute("Plugin-Requires", "orca$systemVersionRequirement")
      .extensions(extensionClassNames)
      .build()
  }

  @Autowired
  override lateinit var spinnakerPluginManager: SpinnakerPluginManager

  @Autowired
  lateinit var taskResolver: TaskResolver

  @Autowired
  lateinit var stageResolver: StageResolver

  @Autowired
  lateinit var jobService: JobService

  @MockBean
  var executionRepository: ExecutionRepository? = null

  @MockBean
  var notificationClusterLock: NotificationClusterLock? = null

  init {
    plugins.delete()
    plugins.mkdir()
    enabledPlugin = buildPlugin("com.netflix.orca.enabled.plugin", ">=1.0.0")
    disabledPlugin = buildPlugin("com.netflix.orca.disabled.plugin", ">=1.0.0")
    // Make it very unlikely that the version of orca satisfies this requirement
    versionNotSupportedPlugin = buildPlugin("com.netflix.orca.version.not.supported.plugin", "=0.0.9")
  }
}

@SpringBootTest(classes = [Main::class])
@ContextConfiguration(classes = [PluginTestConfiguration::class])
@TestPropertySource(properties = ["spring.config.location=classpath:orca-plugins-test.yml"])
abstract class OrcaTestService

@TestConfiguration
internal class PluginTestConfiguration {

  @Bean
  @Primary
  fun queue(clock: Clock?, publisher: EventPublisher?): MonitorableQueue {
    return InMemoryQueue(
      clock!!, Duration.ofMinutes(1), emptyList(), false, publisher!!
    )
  }
}
