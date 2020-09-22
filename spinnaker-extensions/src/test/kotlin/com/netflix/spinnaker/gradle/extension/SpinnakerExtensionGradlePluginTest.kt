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
import com.sun.net.httpserver.HttpServer
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.gradle.testfixtures.ProjectBuilder
import java.net.InetSocketAddress
import org.gradle.api.tasks.testing.Test as GradleTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

  @Test
  fun `compatibility-test-runner plugin registers tasks, source sets, and configurations`() {
    val service = "orca"
    val compatibility = listOf("1.21.1", "1.22.0")

    // Configure bundler plugin in root project.
    val rootProject = ProjectBuilder.builder().build()
    rootProject.plugins.apply("io.spinnaker.plugin.bundler")
    rootProject.extensions.getByType(SpinnakerBundleExtension::class.java).apply {
      compatibility {
        spinnaker = compatibility
      }
    }

    // Configure test runner and Spinnaker extension plugins.
    val subproject = ProjectBuilder.builder().withName("$service-plugin").withParent(rootProject).build()
    subproject.plugins.apply("io.spinnaker.plugin.compatibility-test-runner")
    subproject.plugins.apply("io.spinnaker.plugin.service-extension")
    subproject.extensions.getByType(SpinnakerPluginExtension::class.java).apply {
      serviceName = service
    }

    // Verify tasks, source sets, and configurations exist.
    assertNotNull(rootProject.tasks.findByName(SpinnakerCompatibilityTestRunnerPlugin.TASK_NAME))
    compatibility.forEach {
      subproject.tasks.findByName("${SpinnakerCompatibilityTestRunnerPlugin.TASK_NAME}-orca-plugin-${it}").also { task ->
        assertNotNull(task)
        assert(task is GradleTest) {
          "expected generated task to be of type Test"
        }
      }
      assertNotNull(subproject.sourceSets.findByName("compatibility-${it}"))
      assertNotNull(subproject.configurations.findByName("compatibility-${it}Implementation"))
    }
  }

  @Test
  fun `compatibility-test-runner sets service Gradle BOM version using Halyard BOM`() {
    val service = "orca"
    val compatibility = listOf("1.21.1", "1.22.0")

    // Set up bom server.
    val halconfigServer = HttpServer.create(InetSocketAddress(0), 0).apply {
      compatibility.forEach { version ->
        createContext("/bom/${version}.yml") {
          it.responseHeaders.set("Content-Type", "application/x-yaml")
          it.sendResponseHeaders(200, 0)
          it.responseBody.write("""
            version: "$version"
            services:
              ${service}:
                version: "google-service-version-${version}"
          """.trimIndent().toByteArray())
          it.responseBody.close()
        }
      }
      start()
    }

    // Configure bundler plugin in root project.
    val rootProject = ProjectBuilder.builder().build()
    rootProject.plugins.apply("io.spinnaker.plugin.bundler")

    rootProject.extensions.getByType(SpinnakerBundleExtension::class.java).apply {
      compatibility {
        spinnaker = compatibility
        halconfigBaseURL = "http://localhost:${halconfigServer.address.port}"
      }
    }

    // Configure test runner and Spinnaker extension plugins.
    val subproject = ProjectBuilder.builder().withName("$service-plugin").withParent(rootProject).build()
    subproject.plugins.apply("io.spinnaker.plugin.compatibility-test-runner")
    subproject.plugins.apply("io.spinnaker.plugin.service-extension")
    subproject.extensions.getByType(SpinnakerPluginExtension::class.java).apply {
      serviceName = service
    }

    // Trigger lifecycle hooks.
    subproject.evaluate()

    compatibility.forEach { version ->
      val configuration = subproject.configurations.findByName("compatibility-${version}Implementation")
      assertNotNull(configuration)

      val bom = configuration.dependencies.find { dependency ->
        dependency.group == "com.netflix.spinnaker.${service}" && dependency.name == "$service-bom"
      }
      assertNotNull(bom)
      assertEquals("google-service-version-${version}", bom.version)
      assert(bom.force) {
        "expected gradle BOM dependency version to be forced"
      }
    }
  }
}

private fun Project.evaluate() = withGroovyBuilder { "evaluate"() }
