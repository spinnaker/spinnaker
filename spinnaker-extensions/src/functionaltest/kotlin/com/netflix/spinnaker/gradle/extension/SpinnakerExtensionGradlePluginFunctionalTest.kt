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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Functional test for the 'com.netflix.spinnaker.gradle.extension' plugin.
 */
class SpinnakerExtensionGradlePluginFunctionalTest {

  @BeforeTest
  fun cleanup() {
    File("build/functionaltest").also {
      if (it.exists()) it.deleteRecursively()
    }
  }

  @Test
  fun `can run task`() {
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

  @Test
  fun `can run an end-to-end build, including compatibility test`() {
    val service = "orca"
    val version = "1.22.0"
    val root = "build/functionaltest"
    val pluginPackage = "io.armory.plugin"

    directory(root) {
      write("settings.gradle") {
        """
          pluginManagement {
            repositories {
              gradlePluginPortal()
            }
          }

          include "$service-plugin"
        """
      }
      write("build.gradle") {
        """
          plugins {
            id("io.spinnaker.plugin.bundler")
          }

          spinnakerBundle {
            pluginId = "Armory.TestPlugin"
            version = "0.0.1"
            description = "A plugin used to demonstrate that the build works end-to-end"
            provider = "daniel.peach@armory.io"
            compatibility {
              spinnaker = ["$version"]
            }
          }
        """
      }

      subdirectory("$service-plugin") {
        write("build.gradle") {
          """
            plugins {
              id("org.jetbrains.kotlin.jvm")
            }

            apply plugin: "io.spinnaker.plugin.service-extension"
            apply plugin: "io.spinnaker.plugin.compatibility-test-runner"

            repositories {
              mavenCentral()
              jcenter()
              maven { url "https://spinnaker-releases.bintray.com/jars" }
            }

            spinnakerPlugin {
              serviceName = "$service"
              requires = "$service>=0.0.0"
              pluginClass = "$pluginPackage.MyPlugin"
            }

            dependencies {
              compileOnly("org.pf4j:pf4j:3.2.0")

              testImplementation("org.jetbrains.kotlin:kotlin-test")
              testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
              testImplementation("org.jetbrains.kotlin:kotlin-test")
              testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
            }
          """
        }

        subdirectory("src/test/kotlin/${pluginPackage.replace(".", "/")}") {
          // A real test would test something about a plugin here...
          write("MyTest.kt") {
            """
              package $pluginPackage

              import kotlin.test.Test
              import kotlin.test.assertTrue

              class MyTest {
                @Test
                fun addition() {
                  assertTrue(1 + 1 == 2)
                }
              }
            """
          }
        }

        subdirectory("src/main/java/${pluginPackage.replace(".", "/")}") {
          write("MyPlugin.java") {
            """
              package $pluginPackage;

              import org.pf4j.Plugin;
              import org.pf4j.PluginWrapper;

              public class MyPlugin extends Plugin {

                public MyPlugin(PluginWrapper wrapper) {
                  super(wrapper);
                }
              }
            """
          }
        }
      }
    }

    val result = GradleRunner
      .create()
      .forwardOutput()
      .withPluginClasspath()
      .withArguments("compatibilityTest", "releaseBundle")
      .withProjectDir(File(root))
      .build()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))

    val distributions = File(root).resolve("build/distributions")

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
                    "platformVersion": "1.22.0",
                    "serviceVersion": "2.16.0-20200817170018"
                }
            ]
      """
    ).forEach {
      assertTrue(pluginInfo.contains(it))
    }
  }
}

private fun directory(path: String, dsl: DirectoryDsl.() -> Unit) {
  val dir = File(path)
  dir.mkdirs()

  object : DirectoryDsl {
    override fun subdirectory(path: String, dsl: DirectoryDsl.() -> Unit) {
      directory(dir.resolve(path).toString(), dsl)
    }

    override fun write(file: String, contents: () -> String) {
      dir.resolve(file).writeText(contents().trimIndent())
    }
  }.dsl()
}

private interface DirectoryDsl {
  fun subdirectory(path: String, dsl: DirectoryDsl.() -> Unit)
  fun write(file: String, contents: () -> String)
}
