/*
 * Copyright 2019 Netflix, Inc.
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

import com.netflix.spinnaker.gradle.extension.Plugins.ASSEMBLE_PLUGIN_TASK_NAME
import com.netflix.spinnaker.gradle.extension.tasks.AssembleUIPluginTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Exec

/**
 * Gradle plugin to support spinnaker UI plugin bundling aspects.
 *
 * TODO(rz): Need a setup command for scaffolding out all of the various node/rollup/yarn/npm/whatever tool configs
 */
class SpinnakerUIExtensionPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    // Include JavaPlugin as a hack to get the Zip tasks to actually work. For whatever reason, when the JavaPlugin
    // is not applied, Zip tasks fail to bind their configurations, or something.
    project.pluginManager.apply(JavaPlugin::class.java)

    project.tasks.create(ASSEMBLE_PLUGIN_TASK_NAME, AssembleUIPluginTask::class.java)

    project.tasks.create("yarn", Exec::class.java) {
      it.group = Plugins.GROUP
      it.workingDir = project.projectDir
      it.commandLine = listOf("yarn")
    }
    project.tasks.create("yarnBuild", Exec::class.java) {
      it.group = Plugins.GROUP
      it.workingDir = project.projectDir
      it.commandLine = listOf("yarn", "build")
    }

    project.afterEvaluate {
      project.tasks.getByName("build").dependsOn("yarn", "yarnBuild")
      project.tasks
        .getByName("clean")
        .doLast {
          project.delete(project.files("${project.projectDir}/node_modules"))
        }
    }
  }
}
