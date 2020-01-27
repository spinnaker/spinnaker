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
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Functional test for the 'com.netflix.spinnaker.gradle.extension' plugin.
 */
class SpinnakerExtensionGradlePluginFunctionalTest {

  @Test fun `can run task`() {
    // Setup the test build
    val projectDir = File("build/functionaltest")
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

}
