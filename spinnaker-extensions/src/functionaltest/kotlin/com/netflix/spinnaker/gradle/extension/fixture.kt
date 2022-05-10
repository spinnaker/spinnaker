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

import java.io.File

internal class TestPlugin {
  var pluginPackage = "io.armory.plugins"
  var service = "orca"
  var rootDir = TEST_ROOT
  var compatibilityTestVersion = "1.22.0"

  var settingsGradle = """
    pluginManagement {
      repositories {
        gradlePluginPortal()
      }
    }

    include "{{ service }}-plugin"
  """

  var rootBuildGradle = """
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
          test "{{ version }}"
        }
      }
    }
  """

  var subprojectBuildGradle = """
    plugins {
      id("org.jetbrains.kotlin.jvm")
    }

    apply plugin: "io.spinnaker.plugin.service-extension"
    apply plugin: "io.spinnaker.plugin.compatibility-test-runner"

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
  """

  var pluginName = "MyPlugin.java"
  var plugin = """
    package {{ package }};

    import org.pf4j.Plugin;
    import org.pf4j.PluginWrapper;

    public class MyPlugin extends Plugin {

      public MyPlugin(PluginWrapper wrapper) {
        super(wrapper);
      }
    }
  """

  var testName = "MyTest.kt"
  var test = """
    package {{ package }}

    import kotlin.test.Test
    import kotlin.test.assertTrue

    class MyTest {
      @Test
      fun addition() {
        assertTrue(1 + 1 == 2)
      }
    }
  """

  class Builder {
    private val fixture = TestPlugin()

    fun withPackage(pluginPackage: String): Builder {
      fixture.apply { this.pluginPackage = pluginPackage }
      return this
    }

    fun withService(service: String): Builder {
      fixture.apply { this.service = service }
      return this
    }

    fun withRootDir(rootDir: String): Builder {
      fixture.apply { this.rootDir = rootDir }
      return this
    }

    fun withCompatibilityTestVersion(version: String): Builder {
      fixture.apply { compatibilityTestVersion = version }
      return this
    }

    fun withSettingsGradle(settingsGradle: String): Builder {
      fixture.apply { this.settingsGradle = settingsGradle }
      return this
    }

    fun withRootBuildGradle(rootBuildGradle: String): Builder {
      fixture.apply { this.rootBuildGradle = rootBuildGradle }
      return this
    }

    fun withSubprojectBuildGradle(subprojectBuildGradle: String): Builder {
      fixture.apply { this.subprojectBuildGradle = subprojectBuildGradle }
      return this
    }

    fun withPlugin(name: String, plugin: String): Builder {
      fixture.apply { pluginName = name; this.plugin = plugin }
      return this
    }

    fun withTest(name: String, test: String): Builder {
      fixture.apply { testName = name; this.test = test }
      return this
    }

    fun build(): TestPlugin {
      with(fixture) {
        directory(rootDir) {
          write("settings.gradle") {
            settingsGradle.interpolate()
          }
          write("build.gradle") {
            rootBuildGradle.interpolate()
          }
          subdirectory("$service-plugin") {
            write("build.gradle") {
              subprojectBuildGradle.interpolate()
            }
            subdirectory("src/main/java/${pluginPackage.replace(".", "/")}") {
              write(pluginName) {
                plugin.interpolate()
              }
            }
            subdirectory("src/test/kotlin/${pluginPackage.replace(".", "/")}") {
              write(testName) {
                test.interpolate()
              }
            }
          }
        }
      }
      return fixture
    }

    private fun String.interpolate(): String {
      var result = this
      listOf(
        "package" to fixture.pluginPackage,
        "service" to fixture.service,
        "version" to fixture.compatibilityTestVersion
      ).forEach { (k, v) ->
        result = result.replace("{{ $k }}", v)
      }
      return result
    }
  }
}

internal fun directory(path: String, dsl: DirectoryDsl.() -> Unit) {
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

internal interface DirectoryDsl {
  fun subdirectory(path: String, dsl: DirectoryDsl.() -> Unit)
  fun write(file: String, contents: () -> String)
}
