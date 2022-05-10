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
import org.gradle.testkit.runner.GradleRunner
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
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

    assertTrue(build.output.contains("BUILD SUCCESSFUL"))
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
      assertThat(pluginInfo, containsString(it))
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

    assertTrue(build.output.contains("Compatibility tests failed for Spinnaker " + compatibilityTestVersion))
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

    assertTrue(build.output.contains("BUILD SUCCESSFUL"))
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
      assertThat(pluginInfo, containsString(it))
    }
  }
}
