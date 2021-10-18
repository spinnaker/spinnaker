/*
 * Copyright 2020 Armory, Inc.
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

import com.netflix.spinnaker.gradle.extension.compatibility.*
import com.netflix.spinnaker.gradle.extension.extensions.SpinnakerBundleExtension
import com.netflix.spinnaker.gradle.extension.extensions.SpinnakerPluginExtension
import com.sun.net.httpserver.HttpServer
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.gradle.testfixtures.ProjectBuilder
import java.lang.IllegalStateException
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.Test

/**
 * Unit tests for the compatibility test runner plugin.
 * */
class SpinnakerCompatibilityTestRunnerPluginTest {

  @Test
  fun `compatibility-test-runner plugin registers tasks, source sets, and configurations`() {
    val service = "orca"
    val compatibility = listOf("1.21.1", "1.22.0")

    val (rootProject, subproject) = withProjects {
      rootProject {
        plugins.apply("io.spinnaker.plugin.bundler")
        spinnakerBundle {
          compatibility {
            spinnaker {
              compatibility.forEach {
                test(it)
              }
            }
          }
        }
      }
      subproject("$service-plugin") {
        plugins.apply("io.spinnaker.plugin.compatibility-test-runner")
        plugins.apply("io.spinnaker.plugin.service-extension")
        spinnakerPlugin {
          serviceName = service
        }
      }
    }

    // Verify tasks, source sets, and configurations exist.
    assertNotNull(rootProject.tasks.findByName(SpinnakerCompatibilityTestRunnerPlugin.TASK_NAME))
    compatibility.forEach {
      subproject.tasks.findByName("${SpinnakerCompatibilityTestRunnerPlugin.TASK_NAME}-$service-plugin-$it").also { task ->
        require(task is CompatibilityTestTask)
        assertEquals(task.result.get().asFile.path, subproject.buildDir.toPath().resolve("compatibility/$it.json").toString())
      }
      assertNotNull(subproject.sourceSets.findByName("compatibility-$it"))
      assertNotNull(subproject.configurations.findByName("compatibility-${it}Implementation"))
      assertNotNull(subproject.configurations.findByName("compatibility-${it}Runtime"))
    }
  }

  @Test
  fun `compatibility-test-runner plugin registers tasks, source sets, and configurations (simple DSL style)`() {
    val service = "orca"
    val compatibility = listOf("1.21.1", "1.22.0")

    val (rootProject, subproject) = withProjects {
      rootProject {
        plugins.apply("io.spinnaker.plugin.bundler")
        spinnakerBundle {
          compatibility {
            spinnaker = compatibility
          }
        }
      }
      subproject("$service-plugin") {
        plugins.apply("io.spinnaker.plugin.compatibility-test-runner")
        plugins.apply("io.spinnaker.plugin.service-extension")
        spinnakerPlugin {
          serviceName = service
        }
      }
    }

    // Verify tasks, source sets, and configurations exist.
    assertNotNull(rootProject.tasks.findByName(SpinnakerCompatibilityTestRunnerPlugin.TASK_NAME))
    compatibility.forEach {
      subproject.tasks.findByName("${SpinnakerCompatibilityTestRunnerPlugin.TASK_NAME}-$service-plugin-$it").also { task ->
        require(task is CompatibilityTestTask)
        assertEquals(task.result.get().asFile.path, subproject.buildDir.toPath().resolve("compatibility/$it.json").toString())
      }
      assertNotNull(subproject.sourceSets.findByName("compatibility-$it"))
      assertNotNull(subproject.configurations.findByName("compatibility-${it}Implementation"))
      assertNotNull(subproject.configurations.findByName("compatibility-${it}Runtime"))
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

    val (_, subproject) = withProjects {
      rootProject {
        plugins.apply("io.spinnaker.plugin.bundler")
        spinnakerBundle {
          compatibility {
            spinnaker {
              compatibility.forEach {
                test(it)
              }
            }
            halconfigBaseURL = "http://localhost:${halconfigServer.address.port}"
          }
        }
      }
      subproject("$service-plugin") {
        plugins.apply("io.spinnaker.plugin.compatibility-test-runner")
        plugins.apply("io.spinnaker.plugin.service-extension")
        spinnakerPlugin {
          serviceName = service
        }
      }
    }

    // Trigger lifecycle hooks.
    subproject.evaluate()

    compatibility.forEach { version ->
      val runtime = subproject.configurations.findByName("compatibility-${version}Runtime")
      assertNotNull(runtime)

      val bom = runtime.dependencies.find { dependency ->
        dependency.group == "io.spinnaker.${service}" && dependency.name == "$service-bom"
      }
      assertNotNull(bom)
      assertEquals("google-service-version-${version}", bom.version)
      assert(bom.force) { "expected gradle BOM dependency version to be forced" }
    }
  }

  @Test
  fun `compatibility-test-runner resolves version aliases`() {
    val service = "orca"
    val compatibility = listOf("supported", "latest", "nightly", "1.22.0")

    // Set up Spinnaker versions server.
    val halconfigServer = HttpServer.create(InetSocketAddress(0), 0).apply {
      createContext("/versions.yml") {
        it.responseHeaders.set("Content-Type", "application/x-yaml")
        it.sendResponseHeaders(200, 0)
        it.responseBody.write("""
          latestSpinnaker: "1.22.1"
          versions:
            - version: 1.22.1
            - version: 1.21.4
            - version: 1.20.7
          """.trimIndent().toByteArray())
        it.responseBody.close()
      }
      start()
    }

    val (_, subproject) = withProjects {
      rootProject {
        plugins.apply("io.spinnaker.plugin.bundler")
        spinnakerBundle {
          compatibility {
            spinnaker {
              compatibility.forEach {
                test(it)
              }
            }
            halconfigBaseURL = "http://localhost:${halconfigServer.address.port}"
          }
        }
      }
      subproject("$service-plugin") {
        plugins.apply("io.spinnaker.plugin.compatibility-test-runner")
        plugins.apply("io.spinnaker.plugin.service-extension")
        spinnakerPlugin {
          serviceName = service
        }
      }
    }

    listOf(
      "1.22.1", // "latest", but also included in "supported"
      "1.22.0", // explicit
      "1.21.4", // included in "supported"
      "1.20.7", // included in "supported"
      "master-latest-validated" // "nightly"
    ).forEach {
      assertNotNull(subproject.tasks.findByName("${SpinnakerCompatibilityTestRunnerPlugin.TASK_NAME}-$service-plugin-$it"))
    }
  }
}

private fun Project.evaluate() = withGroovyBuilder { "evaluate"() }

private fun withProjects(dsl: ProjectsDsl.() -> Unit): Pair<Project, Project> {
  val rootProject = ProjectBuilder.builder().build()
  val subprojectBuilder = ProjectBuilder.builder().withParent(rootProject)
  var subproject: Project? = null

  object : ProjectsDsl {
    override fun rootProject(dsl: Project.() -> Unit) {
      rootProject.dsl()
    }
    override fun subproject(name: String, dsl: Project.() -> Unit) {
      subproject = subprojectBuilder.withName(name).build().also { it.dsl() }
    }
  }.dsl()

  return if (subproject != null) Pair(rootProject, subproject!!) else throw IllegalStateException("Must configure subproject!")
}

private interface ProjectsDsl {
  fun rootProject(dsl: Project.() -> Unit)
  fun subproject(name: String, dsl: Project.() -> Unit)
}

private fun Project.spinnakerBundle(dsl: SpinnakerBundleExtension.() -> Unit) =
  extensions.getByType(SpinnakerBundleExtension::class.java).dsl()

private fun Project.spinnakerPlugin(dsl: SpinnakerPluginExtension.() -> Unit) =
  extensions.getByType(SpinnakerPluginExtension::class.java).dsl()

