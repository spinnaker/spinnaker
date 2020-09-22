/*
 * Copyright 2020 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gradle.extension

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.gradle.extension.extensions.SpinnakerBundleExtension
import com.netflix.spinnaker.gradle.extension.extensions.SpinnakerPluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.*
import java.lang.IllegalStateException
import java.net.URL
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class SpinnakerCompatibilityTestRunnerPlugin : Plugin<Project> {

  private val mapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule()).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

  override fun apply(project: Project) {
    project.plugins.apply(JavaPlugin::class.java)
    project.plugins.apply(KotlinPluginWrapper::class.java)

    val bundle = project.rootProject.extensions.getByType(SpinnakerBundleExtension::class)
    bundle.compatibility.spinnaker.forEach { v ->
      val sourceSet = "compatibility-$v"
      val configuration = "${sourceSet}Implementation"

      project.configurations.create(configuration).extendsFrom(project.configurations.getByName("${SourceSet.TEST_SOURCE_SET_NAME}Implementation"))

      project.sourceSets.create(sourceSet) {
        compileClasspath += project.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).output
        runtimeClasspath += project.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).output

        project.sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME).also { test ->
          java.srcDirs(test.java.srcDirs)
          kotlin.srcDirs(test.kotlin.srcDirs)
          resources.srcDirs(test.resources.srcDirs)
        }
      }

      project.tasks.register<Test>("compatibilityTest-${project.name}-$v") {
        description = "Runs compatibility tests for Spinnaker $v"
        group = GROUP
        testClassesDirs = project.sourceSets.getByName(sourceSet).output.classesDirs
        classpath = project.sourceSets.getByName(sourceSet).runtimeClasspath
      }

      // Gradle hasn't seen the `SpinnakerPluginExtension` DSL values yet,
      // so push this last step into a lifecycle hook.
      project.afterEvaluate {
        val plugin = project.extensions.getByType(SpinnakerPluginExtension::class)
        val resolvedServiceVersion = URL("${bundle.compatibility.halconfigBaseURL}/bom/${v}.yml").openStream().use {
          mapper.readValue<HalyardBOM>(it).services[plugin.serviceName]?.version ?: throw IllegalStateException("Could not find version for service ${plugin.serviceName}")
        }

        project.dependencies.platform("com.netflix.spinnaker.${plugin.serviceName}:${plugin.serviceName}-bom:$resolvedServiceVersion").apply {
          force = true
        }.also {
          project.dependencies.add(configuration, it)
        }

        // Copy the kotlin test compilation options into the generated compile tasks.
        project.compileKotlinTask("compileTestKotlin")?.also { compileTestKt ->
          project.compileKotlinTask("compileCompatibility-${v}Kotlin")?.apply {
            kotlinOptions {
              languageVersion = compileTestKt.kotlinOptions.languageVersion
              jvmTarget = compileTestKt.kotlinOptions.jvmTarget
            }
          } ?: throw IllegalStateException("Could not find compileKotlin task for source set $sourceSet")
        }
      }
    }

    project.rootProject.tasks.maybeCreate(TASK_NAME).apply {
      description = "Runs Spinnaker compatibility tests"
      group = GROUP
      dependsOn(bundle.compatibility.spinnaker.map { ":${project.name}:compatibilityTest-${project.name}-$it" })
    }
  }

  companion object {
    const val TASK_NAME = "compatibilityTest"
    const val GROUP = "Spinnaker Compatibility"
  }
}

internal val Project.sourceSets: SourceSetContainer
  get() = project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets

internal var Dependency.force: Boolean
  get() = withGroovyBuilder { getProperty("force") as Boolean }
  set(value) = withGroovyBuilder { "force"(value) }

private val SourceSet.kotlin: SourceDirectorySet
  get() = withConvention(KotlinSourceSet::class) { kotlin }

private fun Project.compileKotlinTask(task: String): KotlinCompile? =
  tasks.findByName(task) as KotlinCompile?

data class HalyardBOM(
  val services: Map<String, ServiceVersion>
)

data class ServiceVersion(
  val version: String?
)
