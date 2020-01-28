/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.gradle.extension.tasks

import com.netflix.spinnaker.gradle.extension.Plugins
import com.netflix.spinnaker.gradle.extension.Plugins.ASSEMBLE_PLUGIN_TASK_NAME
import com.netflix.spinnaker.gradle.extension.SpinnakerServiceExtensionPlugin
import com.netflix.spinnaker.gradle.extension.SpinnakerUIExtensionPlugin
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Zip

open class BundlePluginsTask : Zip() {

  override fun getGroup(): String? = Plugins.GROUP

  init {
    project.afterEvaluate {
      dependsOn(project.getTasksByName(ASSEMBLE_PLUGIN_TASK_NAME, true))

    }
  }

  @TaskAction
  fun doAction() {
    val distributions = project.subprojects
      .filter {
        it.plugins.hasPlugin(SpinnakerServiceExtensionPlugin::class.java) ||
          it.plugins.hasPlugin(SpinnakerUIExtensionPlugin::class.java)
      }
      .map { project.file("${it.buildDir}/distributions") }

    from(distributions).into("/")
    include("*")

    archiveFileName.set("${project.name}-${project.version}.zip")

    // TODO(rz): Compute checksum
    //  See: https://github.com/gradle/gradle-checksum
  }

}
