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

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.TaskOutcome
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

const val TEST_ROOT = "build/functionaltest"

/**
 * Functional test for the 'com.netflix.spinnaker.gradle.extension' plugin.
 */
class SpinnakerExtensionGradlePluginFunctionalTest {

  /** The version of spinnaker to test against */
  val compatibilityTestVersion = "1.27.0"

  /** The version of orca in the above spinnaker version */
  val orcaVersion = "8.18.4"

  /**
   * Assert that the specified tasks happened (or were skipped) in the order they appear
   * @param buildResult the build result
   * @param taskPaths paths of tasks to
   */
  fun assertTaskOrder(buildResult: BuildResult, vararg taskPath: String ) {
    val taskOrder = buildResult.tasks.mapIndexed { index: Int, buildTask: BuildTask? -> buildTask?.path  }
    assert(taskPath.size > 1)
    var i = 0
    while(i < taskPath.size) {
      val firstTaskPath = taskPath[i]
      val secondTaskPath = taskPath[++i]
      i++
      assert(taskOrder.indexOf(firstTaskPath) < taskOrder.indexOf(secondTaskPath))
    }
  }

  @BeforeTest
  fun cleanup() {
    File(TEST_ROOT).also {
      if (it.exists()) it.deleteRecursively()
    }
  }

  @Test
  fun `can run task`() {
    // Setup the test build
    val projectDir = File(TEST_ROOT)
    projectDir.mkdirs()
    projectDir.resolve("settings.gradle").writeText("")
    projectDir.resolve("build.gradle").writeText("""
        plugins {
            id('io.spinnaker.plugin.bundler')
        }
    """)

    // Run the build
    val runner = GradleRunner.create()
    runner.forwardOutput()
    runner.withPluginClasspath()
    runner.withArguments("collectPluginZips")
    runner.withProjectDir(projectDir)
    val result = runner.build()

    // Verify the result
    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
  }

  @Test
  fun `can run release bundle task, excluding compatibility test`() {
    // Setup the test build
    val projectDir = File(TEST_ROOT)
    TestPlugin.Builder()
      .withRootDir(TEST_ROOT)
      .withService("orca")
      .withRootBuildGradle("""
        plugins {
          id("io.spinnaker.plugin.bundler")
        }
        spinnakerBundle {
          pluginId = "Armory.TestPlugin"
          version = "0.0.1"
          description = "A plugin used to demonstrate that the build works end-to-end"
          provider = "daniel.peach@armory.io"
        }
      """)
      .withSubprojectBuildGradle("""
      plugins {
        id("org.jetbrains.kotlin.jvm")
      }

      apply plugin: "io.spinnaker.plugin.service-extension"

      repositories {
        mavenCentral()
      }

      spinnakerPlugin {
        serviceName = "{{ service }}"
        requires = "{{ service }}>=0.0.0"
        pluginClass = "{{ package }}.MyPlugin"
      }

      dependencies {
        compileOnly("org.pf4j:pf4j:3.2.0")

        testImplementation("org.jetbrains.kotlin:kotlin-test")
        testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
        testImplementation("org.jetbrains.kotlin:kotlin-test")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
      }
      """)
      .build()

    // Run the build
    val runner = GradleRunner.create()
    runner.forwardOutput()
    runner.withPluginClasspath()
    runner.withArguments("releaseBundle")
    runner.withProjectDir(projectDir)
    val result = runner.build()

    // Verify the result
    assert(result.task(":releaseBundle")!!.outcome == TaskOutcome.SUCCESS)
    assert(!result.tasks.contains(":compatibilityTest"))
    assertTrue(projectDir.resolve("build/distributions").resolve("functionaltest.zip").exists())
  }

  @Test
  fun `can run an end-to-end build, including compatibility test`() {
    TestPlugin.Builder()
      .withRootDir(TEST_ROOT)
      .withService("orca")
      .withCompatibilityTestVersion(compatibilityTestVersion)
      .build()

    val build = GradleRunner
      .create()
      .forwardOutput()
      .withPluginClasspath()
      .withArguments("compatibilityTest", "releaseBundle")
      .withProjectDir(File(TEST_ROOT))
      .build()

    assert(build.task(":compatibilityTest")!!.outcome == TaskOutcome.SUCCESS)
    assert(build.task(":releaseBundle")!!.outcome == TaskOutcome.SUCCESS)
    assertTaskOrder(build, ":compatibilityTest", ":releaseBundle")
    val distributions = File(TEST_ROOT).resolve("build/distributions")
    assertTrue(distributions.resolve("functionaltest.zip").exists())
    val pluginInfo = distributions.resolve("plugin-info.json").readText()
    listOf(
      """
        "id": "Armory.TestPlugin",
      """.trimIndent(),
      """
            "compatibility": [
                {
                    "service": "orca",
                    "result": "SUCCESS",
                    "platformVersion": "${compatibilityTestVersion}",
                    "serviceVersion": "${orcaVersion}"
                }
            ]
      """
    ).forEach {
      assertThat(pluginInfo).contains(it)
    }
  }

  @Test
  fun `compatibility test task fails with failing test`() {
    TestPlugin.Builder()
      .withRootDir(TEST_ROOT)
      .withService("orca")
      .withCompatibilityTestVersion(compatibilityTestVersion)
      .withTest("MyFailingTest.kt", """
        package {{ package }}

        import kotlin.test.Test
        import kotlin.test.assertTrue

        class MyTest {
          @Test
          fun badAddition() {
            assertTrue(1 + 1 == 3)
          }
        }
      """)
      .build()

    val build = GradleRunner
      .create()
      .forwardOutput()
      .withPluginClasspath()
      .withArguments("compatibilityTest", "releaseBundle")
      .withProjectDir(File(TEST_ROOT))
      .buildAndFail()

    assert(build.task(":compatibilityTest")!!.outcome == TaskOutcome.FAILED)
    assert(!build.tasks.contains(":releaseBundle"))
  }

  @Test
  fun `compatibility test task succeeds if failing test is not required`() {
    TestPlugin.Builder()
      .withService("orca")
      .withCompatibilityTestVersion(compatibilityTestVersion)
      .withRootBuildGradle("""
        plugins {
          id("io.spinnaker.plugin.bundler")
        }

        spinnakerBundle {
          pluginId = "Armory.TestPlugin"
          version = "0.0.1"
          description = "A plugin used to demonstrate that the build works end-to-end"
          provider = "daniel.peach@armory.io"
          compatibility {
            spinnaker {
              test(version: "{{ version }}", required: false)
            }
          }
        }
      """)
      .withTest("MyFailingTest.kt", """
        package {{ package }}

        import kotlin.test.Test
        import kotlin.test.assertTrue

        class MyTest {
          @Test
          fun badAddition() {
            assertTrue(1 + 1 == 3)
          }
        }
      """)
      .build()

    val build = GradleRunner
      .create()
      .forwardOutput()
      .withPluginClasspath()
      .withArguments("compatibilityTest", "releaseBundle")
      .withProjectDir(File(TEST_ROOT))
      .build()

    assert(build.task(":compatibilityTest")!!.outcome == TaskOutcome.SUCCESS)
    assert(build.task(":releaseBundle")!!.outcome == TaskOutcome.SUCCESS)
    assertTaskOrder(build, ":compatibilityTest", ":releaseBundle")
    val pluginInfo = File(TEST_ROOT).resolve("build/distributions/plugin-info.json").readText()
    listOf(
      """
        "id": "Armory.TestPlugin",
      """.trimIndent(),
      """
            "compatibility": [
                {
                    "service": "orca",
                    "result": "FAILURE",
                    "platformVersion": "${compatibilityTestVersion}",
                    "serviceVersion": "${orcaVersion}"
                }
            ]
      """
    ).forEach {
      assertThat(pluginInfo).contains(it)
    }
  }
}
