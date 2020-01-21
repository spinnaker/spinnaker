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

import org.gradle.api.AntBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.bundling.Zip

/**
 * Bundles all plugin artifacts into single zip.
 */
class SpinnakerExtensionsBundlerPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        val allBuildDirs: MutableList<String> = mutableListOf()
        project.subprojects.forEach { subProject ->
            if (subProject.plugins.hasPlugin(SpinnakerServiceExtensionPlugin::class.java)
                || subProject.plugins.hasPlugin(SpinnakerUIExtensionPlugin::class.java)) {
                allBuildDirs.add("${subProject.name}/build/libs")
            }
        }

        // Register distPluginZip for root project.
        project.tasks.register<Zip>("distPluginZip", Zip::class.java) {
            it.from(allBuildDirs).into("/")
            it.archiveFileName.set("${project.name}-${project.version}.zip")
            it.include("*")
            it.destinationDirectory.set(project.rootDir)
            it.doLast {
                project.logger.log(LogLevel.WARN, "Computing Checksum for.." + it.outputs.files.singleFile.absolutePath)
                project.ant { ant: AntBuilder ->
                    ant.invokeMethod("checksum", mapOf("file" to it.outputs.files.singleFile))
                }
            }
        }

        val distPluginZip: Task = project.tasks.getByName("distPluginZip")
        distPluginZip.dependsOn(project.tasks.getByName("build"))
    }

}