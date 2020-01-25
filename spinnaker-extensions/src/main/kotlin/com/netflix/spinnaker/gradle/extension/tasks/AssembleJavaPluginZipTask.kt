/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.gradle.extension.tasks

import com.netflix.spinnaker.gradle.extension.Plugins
import com.netflix.spinnaker.gradle.extension.SpinnakerServiceExtensionPlugin
import com.netflix.spinnaker.gradle.extension.extensions.SpinnakerPluginExtension
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.bundling.Zip
import java.io.File
import java.lang.IllegalStateException

/**
 * Task to assemble plugin related files(dependency jars, class files etc) into a zip.
 */
open class AssembleJavaPluginZipTask : Zip() {

  override fun getGroup(): String? = Plugins.GROUP

  init {
    val ext = project.extensions.findByType(SpinnakerPluginExtension::class.java)
      ?: throw IllegalStateException("A 'spinnakerPlugin' configuration block is required")

    this.archiveBaseName.set(ext.serviceName)
    this.archiveVersion.set("")
    this.archiveExtension.set("zip")

    val sourceSets = project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets

    this.with(
      // TODO(rz): Make sure kork / service deps are not included (compileOnly)
      project.copySpec().from(sourceSets.getByName("main").compileClasspath)
        .into("libs/"),
      project.copySpec()
        .from(sourceSets.getByName("main").runtimeClasspath)
        .from(sourceSets.getByName("main").resources)
        .into("classes/"),
      project.copySpec().from(File(project.buildDir, "tmp/jar"))
        .into("classes/META-INF/")
    )

    this.dependsOn(JavaPlugin.JAR_TASK_NAME)
  }
}
