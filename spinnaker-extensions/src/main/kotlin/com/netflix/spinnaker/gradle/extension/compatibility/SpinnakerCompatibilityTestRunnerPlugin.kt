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

package com.netflix.spinnaker.gradle.extension.compatibility

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.gradle.extension.PluginObjectMapper
import com.netflix.spinnaker.gradle.extension.extensions.SpinnakerBundleExtension
import com.netflix.spinnaker.gradle.extension.extensions.SpinnakerPluginExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.kotlin.dsl.*
import java.lang.IllegalStateException
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class SpinnakerCompatibilityTestRunnerPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.plugins.apply(JavaPlugin::class.java)
    project.plugins.apply(KotlinPluginWrapper::class.java)

    val bundle = project.rootProject.extensions.getByType(SpinnakerBundleExtension::class)
    val spinnakerVersionsClient = DefaultSpinnakerVersionsClient(bundle.compatibility.halconfigBaseURL)

    val resolvedVersions = spinnakerVersionsClient.resolveVersionAliases(bundle.compatibility.spinnaker)
    resolvedVersions.forEach { v ->
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

      val test = project.tasks.create<CompatibilityTestTask>("compatibilityTest-${project.name}-$v") {
        description = "Runs compatibility tests for Spinnaker $v"
        group = GROUP
        testClassesDirs = project.sourceSets.getByName(sourceSet).output.classesDirs
        classpath = project.sourceSets.getByName(sourceSet).runtimeClasspath
        ignoreFailures = project.gradle.startParameter.taskNames.contains(TASK_NAME)
        result.set(project.layout.buildDirectory.file("compatibility/$v.json"))
      }

      // Gradle hasn't seen the `SpinnakerPluginExtension` DSL values yet,
      // so push this last step into a lifecycle hook.
      project.afterEvaluate {
        val plugin = project.extensions.getByType(SpinnakerPluginExtension::class)
        val resolvedServiceVersion = spinnakerVersionsClient.getSpinnakerBOM(v).let {
          it.services[plugin.serviceName]?.version ?: throw IllegalStateException("Could not find version for service ${plugin.serviceName}")
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

        test.afterSuite { descriptor, result ->
          if (descriptor.parent == null) {
            test.result.asFile.get().writeText(
              PluginObjectMapper.mapper.writeValueAsString(CompatibilityTestResult(
                platformVersion = v,
                serviceVersion = resolvedServiceVersion,
                service = plugin.serviceName!!,
                result = result.resultType
              ))
            )
          }
        }
      }
    }

    project.rootProject.tasks.maybeCreate(TASK_NAME).apply {
      description = "Runs Spinnaker compatibility tests"
      group = GROUP
      dependsOn(resolvedVersions.map { ":${project.name}:compatibilityTest-${project.name}-$it" })
      doLast {
        project.rootProject.subprojects
          .flatMap { it.tasks.withType(CompatibilityTestTask::class.java) }
          .map { PluginObjectMapper.mapper.readValue<CompatibilityTestResult>(it.result.asFile.get()) }
          .filter { it.result == TestResult.ResultType.FAILURE  }
          .also { results ->
            if (results.isNotEmpty()) {
              throw GradleException("Compatibility tests failed for Spinnaker ${results.map { it.result }.joinToString(", ")}")
            }
          }
      }
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

private fun Test.afterSuite(cb: (desc: TestDescriptor, result: TestResult) -> Unit) =
  afterSuite(KotlinClosure2(cb))

private fun SpinnakerVersionsClient.resolveVersionAliases(versions: List<String>): Set<String> {
  // Save a lookup if none of the versions are aliases.
  if (!versions.any { SpinnakerVersionAlias.isAlias(it) }) {
    return versions.toSet()
  }

  val versionsManifest = getVersionsManifest()
  return versions.flatMapTo(mutableSetOf()) { version ->
    when (SpinnakerVersionAlias.from(version)) {
      SpinnakerVersionAlias.LATEST -> listOf(versionsManifest.latestSpinnaker)
      SpinnakerVersionAlias.SUPPORTED -> versionsManifest.versions.map { it.version }
      SpinnakerVersionAlias.NIGHTLY -> listOf("master-latest-validated")
      else -> listOf(version)
    }
  }
}
