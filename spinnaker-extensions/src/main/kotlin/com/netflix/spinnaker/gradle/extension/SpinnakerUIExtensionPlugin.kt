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

import com.moowork.gradle.node.NodePlugin
import com.netflix.spinnaker.gradle.extension.Plugins.ASSEMBLE_PLUGIN_TASK_NAME
import com.netflix.spinnaker.gradle.extension.tasks.AssembleUIPluginTask
import com.netflix.spinnaker.gradle.extension.tasks.BuildUIExtensionTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin

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

    project.pluginManager.apply(NodePlugin::class.java)

    project.tasks.create(ASSEMBLE_PLUGIN_TASK_NAME, AssembleUIPluginTask::class.java)
    project.tasks.create("buildUi", BuildUIExtensionTask::class.java)

    project.afterEvaluate {
      project.tasks.getByName("build").dependsOn("buildUi")
      project.tasks
        .getByName("clean")
        .dependsOn("yarn_cache_clean")
        .doLast {
          project.delete(project.files("${project.projectDir}/node_modules"))
        }
    }
  }
}
