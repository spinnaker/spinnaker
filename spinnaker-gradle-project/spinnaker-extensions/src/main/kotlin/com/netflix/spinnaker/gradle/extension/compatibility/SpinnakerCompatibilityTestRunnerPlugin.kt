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
import com.netflix.spinnaker.gradle.extension.extensions.VersionTestConfig
import com.netflix.spinnaker.gradle.extension.getParent
import com.netflix.spinnaker.gradle.extension.Plugins.RELEASE_BUNDLE_TASK_NAME
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.register
import java.lang.IllegalStateException
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class SpinnakerCompatibilityTestRunnerPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.plugins.apply(JavaPlugin::class.java)
    project.plugins.apply(KotlinPluginWrapper::class.java)

    val bundle = getParent(project).extensions.getByType(SpinnakerBundleExtension::class)
    val spinnakerVersionsClient = DefaultSpinnakerVersionsClient(bundle.compatibility.halconfigBaseURL)

    val versionTestConfigs = spinnakerVersionsClient.resolveVersionAliases(bundle.compatibility.versionTestConfigs)
    versionTestConfigs.forEach { config ->
      val sourceSet = "compatibility-${config.version}"
      val runtimeConfiguration = "${sourceSet}RuntimeOnly"
      val implementationConfiguration = "${sourceSet}Implementation"

      project.configurations.create(runtimeConfiguration).extendsFrom(project.configurations.getByName("${SourceSet.TEST_SOURCE_SET_NAME}RuntimeOnly"))
      project.configurations.create(implementationConfiguration).extendsFrom(project.configurations.getByName("${SourceSet.TEST_SOURCE_SET_NAME}Implementation"))

      project.sourceSets.create(sourceSet) {
        compileClasspath += project.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).output
        runtimeClasspath += project.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).output

        project.sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME).also { test ->
          java.srcDirs(test.java.srcDirs)
          kotlin.srcDirs(test.kotlin.srcDirs)
          resources.srcDirs(test.resources.srcDirs)
        }
      }

      val test = project.tasks.register<CompatibilityTestTask>("compatibilityTest-${project.name}-${config.version}") {
        description = "Runs compatibility tests for Spinnaker ${config.version}"
        group = GROUP
        testClassesDirs = project.sourceSets.getByName(sourceSet).output.classesDirs
        classpath = project.sourceSets.getByName(sourceSet).runtimeClasspath
        ignoreFailures = project.gradle.startParameter.taskNames.contains(TASK_NAME)
        result.set(project.layout.buildDirectory.file("compatibility/${config.version}.json"))
      }

      // Gradle hasn't seen the `SpinnakerPluginExtension` DSL values yet,
      // so push this last step into a lifecycle hook.
      project.afterEvaluate {
        val plugin = project.extensions.getByType(SpinnakerPluginExtension::class)
        val resolvedServiceVersion = spinnakerVersionsClient.getSpinnakerBOM(config.version).let {
          it.services[plugin.serviceName]?.version ?: throw IllegalStateException("Could not find version for service ${plugin.serviceName}")
        }

        val platformDependency = project.dependencies.platform("io.spinnaker.${plugin.serviceName}:${plugin.serviceName}-bom:$resolvedServiceVersion")
        project.dependencies.add(runtimeConfiguration, platformDependency)

        // Force the BOM version using dependency constraints
        project.configurations.getByName(runtimeConfiguration).resolutionStrategy.eachDependency {
          if (requested.group == "io.spinnaker.${plugin.serviceName}" && requested.name == "${plugin.serviceName}-bom") {
            useVersion(resolvedServiceVersion)
            because("Force BOM version for compatibility testing")
          }
        }

        // Copy the kotlin test compilation options into the generated compile tasks.
        project.compileKotlinTask("compileTestKotlin")?.also { compileTestKt ->
          project.compileKotlinTask("compileCompatibility-${config.version}Kotlin")?.apply {
            compilerOptions {

              languageVersion = compileTestKt.compilerOptions.languageVersion
              jvmTarget = compileTestKt.compilerOptions.jvmTarget
            }
          } ?: throw IllegalStateException("Could not find compileKotlin task for source set $sourceSet")
        }

        test.get().afterSuite { descriptor, result ->
          if (descriptor.parent == null) {
            test.get().result.asFile.get().writeText(
              PluginObjectMapper.mapper.writeValueAsString(CompatibilityTestResult(
                platformVersion = config.version,
                serviceVersion = resolvedServiceVersion,
                service = plugin.serviceName!!,
                result = result.resultType
              ))
            )
          }
        }
      }
    }

    getParent(project).tasks.maybeCreate(TASK_NAME).apply {
      description = "Runs Spinnaker compatibility tests"
      group = GROUP
      dependsOn(versionTestConfigs.map { ":${project.name}:compatibilityTest-${project.name}-${it.version}" })
      val releaseBundle = getParent(project).tasks.findByName(RELEASE_BUNDLE_TASK_NAME)
      if(releaseBundle != null)
        releaseBundle.mustRunAfter(TASK_NAME)
      doLast {
        val failedTests = getParent(project).subprojects
          .flatMap { it.tasks.withType(CompatibilityTestTask::class.java) }
          .map { PluginObjectMapper.mapper.readValue<CompatibilityTestResult>(it.result.asFile.get()) }
          .filter { it.result == TestResult.ResultType.FAILURE  }

        // Only fail the top-level task if one of the tests is required.
        if (failedTests.any { result -> versionTestConfigs.findForResult(result).required }) {
          throw GradleException("Compatibility tests failed for Spinnaker ${failedTests.joinToString(", ") { it.platformVersion }}")
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
  get() = project.extensions.getByType<JavaPluginExtension>().sourceSets

private val SourceSet.kotlin: SourceDirectorySet
  get() {
    val kotlinExtension = (this as ExtensionAware).extensions.getByName("kotlin") as SourceDirectorySet
    return kotlinExtension
  }

private fun Project.compileKotlinTask(task: String): KotlinCompile? =
  tasks.findByName(task) as KotlinCompile?

private fun Test.afterSuite(cb: (desc: TestDescriptor, result: TestResult) -> Unit) =
  afterSuite(KotlinClosure2(cb))

private fun Collection<VersionTestConfig>.findForResult(result: CompatibilityTestResult): VersionTestConfig =
  find { it.version == result.platformVersion }
    // Should never happen.
    ?: throw IllegalStateException("Test result for Spinnaker ${result.platformVersion} has no test config")

private fun SpinnakerVersionsClient.resolveVersionAliases(versions: List<VersionTestConfig>): Set<VersionTestConfig> {
  // Save a lookup if none of the versions are aliases.
  if (!versions.any { SpinnakerVersionAlias.isAlias(it.version) }) {
    return versions.toSet()
  }

  val versionsManifest = getVersionsManifest()
  return versions.flatMapTo(mutableSetOf()) { version ->
    when (version.alias) {
      SpinnakerVersionAlias.LATEST -> listOf(version.copy(version = versionsManifest.latestSpinnaker))
      SpinnakerVersionAlias.SUPPORTED -> versionsManifest.versions.map { version.copy(version = it.version) }
      SpinnakerVersionAlias.NIGHTLY -> listOf(version.copy(version = "master-latest-validated"))
      else -> listOf(version)
    }
  }
}
