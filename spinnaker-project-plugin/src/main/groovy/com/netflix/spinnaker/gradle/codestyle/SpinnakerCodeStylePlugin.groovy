/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.gradle.codestyle

import com.diffplug.gradle.spotless.FormatExtension
import com.diffplug.gradle.spotless.JavaExtension
import com.diffplug.gradle.spotless.KotlinExtension
import com.diffplug.gradle.spotless.KotlinGradleExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin

class SpinnakerCodeStylePlugin implements Plugin<Project> {

  @Override
  void apply(Project project) {
    def extension = project.extensions.create("spinnakerCodeStyle", SpinnakerCodeStyle)

    project.afterEvaluate {
      if (!extension.enabled) {
        project.logger.warn("${project.name} has disabled codestyle enforcement!")
        return
      }

      project.rootProject.file(".git/hooks").mkdirs()
      project.rootProject.file(".git/hooks/pre-commit").write(getClass().getResource("/pre-commit").text)
      project.rootProject.file(".git/hooks/pre-commit").executable = true

      project.plugins.apply(SpotlessPlugin)
      project.spotless { SpotlessExtension spotless ->

        // Instead of performing `spotlessCheck` on `check`, let's just `spotlessApply` instead, since devs will be
        // required to make the changes anyway. But don't do this if we're running in a CI build.
        if (!isRunningUnderContinuousIntegration()) {
          spotless.enforceCheck = false
          project.getTasks()
            .matching { it.name == JavaBasePlugin.CHECK_TASK_NAME }
            .all { it.dependsOn("spotlessApply") }
        }

        spotless.java(new Action<JavaExtension>() {
          @Override
          void execute(JavaExtension javaExtension) {
            javaExtension.target("src/**/*.java")
            javaExtension.googleJavaFormat("1.8")
            javaExtension.removeUnusedImports()
            javaExtension.trimTrailingWhitespace()
            javaExtension.endWithNewline()
          }
        })

        if (hasKotlin(project)) {

          def ktlintData = [
            indent_size             : '2',
            continuation_indent_size: '2',
            // import ordering now defaults to IntelliJ-style, with java.*
            // and kotlin.* imports at the bottom. This is different than
            // google-java-format, so let's keep it consistent.
            kotlin_imports_layout   : 'ascii'
          ]

          spotless.kotlin(new Action<KotlinExtension>() {
            @Override
            void execute(KotlinExtension kotlinExtension) {
              kotlinExtension.ktlint("0.37.2").userData(ktlintData)
              kotlinExtension.trimTrailingWhitespace()
              kotlinExtension.endWithNewline()
            }
          })

          spotless.kotlinGradle(new Action<KotlinGradleExtension>() {
            @Override
            void execute(KotlinGradleExtension kotlinGradleExtension) {
              kotlinGradleExtension.target("*.gradle.kts", "**/*.gradle.kts")
              kotlinGradleExtension.ktlint("0.37.2").userData(ktlintData)
              kotlinGradleExtension.trimTrailingWhitespace()
              kotlinGradleExtension.endWithNewline()
            }
          })
        }

        spotless.format(
          'misc',
          new Action<FormatExtension>() {
            @Override
            void execute(FormatExtension formatExtension) {
              formatExtension.target('**/.gitignore', 'src/**/*.json', 'src/**/*.yml', 'src/**/*.yaml', 'config/*.yml', 'halconfig/*.yml', '**/*.gradle')
              formatExtension.trimTrailingWhitespace()
              formatExtension.indentWithSpaces(2)
              formatExtension.endWithNewline()
            }
          }
        )
      }
    }
  }

  private boolean hasKotlin(Project project) {
    Class kotlin
    try {
      kotlin = Class.forName("org.jetbrains.kotlin.gradle.plugin.KotlinPlatformJvmPlugin")
    } catch (ClassNotFoundException e) {
      return false
    }
    return !project.plugins.withType(kotlin).empty
  }

  private static boolean isRunningUnderContinuousIntegration() {
    return System.getenv().containsKey("GITHUB_WORKFLOW")
  }
}
