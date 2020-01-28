/*
 * Copyright 2019 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.netflix.spinnaker.gradle.extension

import com.netflix.spinnaker.gradle.extension.extensions.SpinnakerBundleExtension
import com.netflix.spinnaker.gradle.extension.extensions.SpinnakerPluginExtension
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Unit tests to verify task existence in each spinnaker extension plugin.
 */
class SpinnakerExtensionGradlePluginTest {

  @Test
  fun `spinnakerserviceextension plugin registers task`() {
    // Create a test project and apply the plugin
    val project = ProjectBuilder.builder().build()

    project.plugins.apply("io.spinnaker.plugin.service-extension")
    project.plugins.apply("java")

    val extension: SpinnakerPluginExtension? = project.extensions.findByType(SpinnakerPluginExtension::class.java)
    assertNotNull(extension)

    extension.apply {
      this.serviceName = "test"
      this.pluginClass = "com.netflix.spinnaker.TestPlugin"
    }

    // Verify tasks exist
    assertNotNull(project.tasks.findByName(Plugins.ASSEMBLE_PLUGIN_TASK_NAME))
  }

  @Test
  fun `spinnakeruiextension plugin registers task`() {
    // Create a test project and apply the plugin
    val project = ProjectBuilder.builder().build()
    project.plugins.apply("io.spinnaker.plugin.ui-extension")

    // Verify tasks exist
    assertNotNull(project.tasks.findByName(Plugins.ASSEMBLE_PLUGIN_TASK_NAME))
  }

  @Test
  fun `spinnakerextensionbundler plugin registers task`() {
    // Create a test project and apply the plugin
    val project = ProjectBuilder.builder().build()
    project.plugins.apply("io.spinnaker.plugin.bundler")

    val extension: SpinnakerBundleExtension? = project.extensions.findByType(SpinnakerBundleExtension::class.java)
    assertNotNull(extension)

    extension.apply {
      this.provider = "test"
      this.pluginId = "Test"
      this.description = "Test Plugin"
      this.version = "1.0.0"
    }

    // Verify tasks exist
    assertNotNull(project.tasks.findByName(Plugins.RELEASE_BUNDLE_TASK_NAME))
    assertNotNull(project.tasks.findByName(Plugins.CHECKSUM_BUNDLE_TASK_NAME))
    assertNotNull(project.tasks.findByName(Plugins.BUNDLE_PLUGINS_TASK_NAME))
    assertNotNull(project.tasks.findByName(Plugins.COLLECT_PLUGIN_ZIPS_TASK_NAME))
  }

}
