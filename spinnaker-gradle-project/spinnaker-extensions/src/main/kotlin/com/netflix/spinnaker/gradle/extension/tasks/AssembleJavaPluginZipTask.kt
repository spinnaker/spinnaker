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
import com.netflix.spinnaker.gradle.extension.extensions.SpinnakerPluginExtension
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.getByType
import org.gradle.work.DisableCachingByDefault
import java.lang.IllegalStateException

/**
 * Task to assemble plugin related files(dependency jars, class files etc) into a zip.
 */
@DisableCachingByDefault(because = "Plugin zip assembly is not cacheable")
abstract class AssembleJavaPluginZipTask : Zip() {

  @Internal
  override fun getGroup(): String = Plugins.GROUP

  init {
    val ext = project.extensions.findByType(SpinnakerPluginExtension::class.java)
      ?: throw IllegalStateException("A 'spinnakerPlugin' configuration block is required")

    this.archiveBaseName.set(ext.serviceName)
    this.archiveVersion.set("")
    this.archiveExtension.set("zip")

    val sourceSets = project.extensions.getByType<JavaPluginExtension>().sourceSets
    val mainSourceSet = sourceSets.getByName("main")

    // Use runtimeClasspath (already resolvable) to get dependency JARs.
    // .copy() on non-resolvable configurations was removed in Gradle 9.
    val runtimeClasspath = project.configurations.getByName("runtimeClasspath")
    val ownClassDirs = mainSourceSet.runtimeClasspath.files.filter { !it.absolutePath.endsWith(".jar") }

    this.with(
      project.copySpec()
        .from(runtimeClasspath.filter { it.absolutePath.endsWith(".jar") })
        .into("lib/"),
      project.copySpec()
        .from(ownClassDirs)
        .from(mainSourceSet.resources)
        .into("classes/"),
      project.copySpec().from(project.layout.buildDirectory.dir("tmp/jar"))
        .into("classes/META-INF/")
    )

    this.dependsOn(JavaPlugin.JAR_TASK_NAME)
  }
}
