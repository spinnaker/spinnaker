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
import com.netflix.spinnaker.gradle.extension.Plugins.BUNDLE_PLUGINS_TASK_NAME
import com.netflix.spinnaker.gradle.extension.Plugins.CHECKSUM_BUNDLE_TASK_NAME
import com.netflix.spinnaker.gradle.extension.Plugins.COLLECT_PLUGIN_ZIPS_TASK_NAME
import com.netflix.spinnaker.gradle.extension.Plugins.RELEASE_BUNDLE_TASK_NAME
import com.netflix.spinnaker.gradle.extension.extensions.SpinnakerBundleExtension
import com.netflix.spinnaker.gradle.extension.extensions.SpinnakerPluginExtension
import com.netflix.spinnaker.gradle.extension.tasks.BundlePluginsTask
import com.netflix.spinnaker.gradle.extension.tasks.CreatePluginInfoTask
import org.gradle.api.AntBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Zip
import org.gradle.crypto.checksum.Checksum
import org.gradle.crypto.checksum.ChecksumPlugin
import java.io.File

/**
 * Bundles all plugin artifacts into single zip.
 */
class SpinnakerExtensionsBundlerPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.plugins.apply(JavaPlugin::class.java)
    project.plugins.apply(ChecksumPlugin::class.java)

    project.extensions.create("spinnakerBundle", SpinnakerBundleExtension::class.java)

    project.tasks.register(COLLECT_PLUGIN_ZIPS_TASK_NAME, Copy::class.java) {

      it.group = Plugins.GROUP

      // Look for assemblePlugin task.
      if (it.project.subprojects.isNotEmpty()) { // Safe guard this in case if project structure is not set correctly.
        val assemblePluginTasks: Set<Task> = it.project.getTasksByName(ASSEMBLE_PLUGIN_TASK_NAME, true)
        if (assemblePluginTasks.isNotEmpty()) {
          it.dependsOn(assemblePluginTasks)
        }
      }

      val distributions = project.subprojects
        .filter { subproject ->
          subproject.plugins.hasPlugin(SpinnakerServiceExtensionPlugin::class.java) ||
            subproject.plugins.hasPlugin(SpinnakerUIExtensionPlugin::class.java)
        }
        .map { subproject -> project.file("${subproject.buildDir}/distributions") }

      it.from(distributions)
        .into("${project.buildDir}/zips")
    }

    project.tasks.register(BUNDLE_PLUGINS_TASK_NAME, Zip::class.java) {
      it.dependsOn(COLLECT_PLUGIN_ZIPS_TASK_NAME)
      it.group = Plugins.GROUP
      it.from("${project.buildDir}/zips")
    }

    project.tasks.register(CHECKSUM_BUNDLE_TASK_NAME, Checksum::class.java) {
      it.dependsOn(BUNDLE_PLUGINS_TASK_NAME)
      it.group = Plugins.GROUP

      it.files = project.tasks.getByName(BUNDLE_PLUGINS_TASK_NAME).outputs.files
      it.outputDir = File(project.buildDir, "checksums")
      it.algorithm = Checksum.Algorithm.SHA512
    }

    project.tasks.register(RELEASE_BUNDLE_TASK_NAME, CreatePluginInfoTask::class.java) {
      it.dependsOn(CHECKSUM_BUNDLE_TASK_NAME)
      it.group = Plugins.GROUP
    }
  }
}
