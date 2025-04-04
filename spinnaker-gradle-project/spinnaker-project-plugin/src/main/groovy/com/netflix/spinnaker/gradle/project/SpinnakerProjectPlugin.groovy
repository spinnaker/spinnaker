/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.gradle.project

import com.netflix.spinnaker.gradle.baseproject.SpinnakerBaseProjectPlugin
import com.netflix.spinnaker.gradle.publishing.artifactregistry.ArtifactRegistryPublishPlugin
import com.netflix.spinnaker.gradle.publishing.PublishingPlugin
import com.netflix.spinnaker.gradle.publishing.nexus.NexusPublishPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.AbstractCopyTask

class SpinnakerProjectPlugin implements Plugin<Project> {

  @Override
  void apply(Project project) {
    project.plugins.apply(SpinnakerBaseProjectPlugin)
    project.plugins.apply(PublishingPlugin)
    project.plugins.apply(ArtifactRegistryPublishPlugin)
    project.plugins.apply(NexusPublishPlugin)
    project.afterEvaluate {
      propagateRootLevelProperties(project)
      configureDefaultTasks(project)
      fixGradle7DuplicatesBug(project)
    }
  }

  void fixGradle7DuplicatesBug(Project project) {
    if(project.rootProject != project) return

    // There is an issue with Gradle 7 and errors about duplicate files in source sets
    // This is a hack to set the duplicate resolution strategy on everything
    // https://youtrack.jetbrains.com/issue/KT-46978/Duplicate-resource-errors-on-gradle-7-with-multi-module-multiplatform-project-with-withJava
    project.gradle.taskGraph.whenReady { tg ->
        tg.allTasks
            .findAll { it.hasProperty("duplicatesStrategy") }
            .forEach {
                it.setProperty("duplicatesStrategy", "EXCLUDE")
            }
    }
  }

  void dependOnTasksRecursively(Project project, String taskName) {
    def task = project.tasks.findByName(taskName) ?: project.tasks.register(taskName)
    def dependents = project.subprojects*.tasks*.findByName(taskName).minus(null)
    if (dependents) {
      project.tasks.getByName(taskName).dependsOn dependents
    }
  }

  void configureDefaultTasks(Project project) {
    if(project.rootProject != project) return

    // There are some material differences when calling Gradle "lifecycle" tasks of a composite build
    // Gradle seems to have some magic regarding recursively executing named tasks in subprojects
    //   when things like "build" or "publish" are run from the project root
    // When the task name is NOT run from the top level, we have to recurse through the subprojects ourselves
    // So, assemble and publish implement that below
    def defaultTasks = ['assemble', 'check', 'clean', 'test', 'publish']
    for(String taskName in defaultTasks) {
      dependOnTasksRecursively(project, taskName)
    }
  }

  // Gradle passes most properties automatically (i.e. -P args), but not the actual properties file: https://github.com/gradle/gradle/issues/2534
  // This set of functions provides a mechanism by which to propagate things from the root gradle.properties file to all included builds
  // Any property prefixed with `io.spinnaker.` will be propagated to child projects and the prefix removed
  // If the property exists in the child project already, it will not be overwritten
  def propagatedPropertyPrefix = "io.spinnaker."

  void applyPropertyIfAllowed(Project project, java.util.Map.Entry prop) {
    if (prop == null || prop.key == null) return

    def allowed = prop.key.startsWith(propagatedPropertyPrefix)
    if (allowed) {
      def cleanKey = prop.key - ~/^${propagatedPropertyPrefix}/
      if(!project.ext.has(cleanKey)) {
        project.ext."$cleanKey" = prop.value
      }
    }
  }

  void propagateRootLevelProperties(Project project) {
    // If we are in a composite build, there will be a parent reference
    // If an allowed property is found on the parent, copy it over to the child build
    if (project.gradle.parent != null) {
      def rootPropertiesFile = project.gradle.parent.rootProject.file("./gradle.properties")

      rootPropertiesFile.withReader {
        Properties props = new Properties()
        props.load(it)
        props.each { prop ->
          applyPropertyIfAllowed(project, prop)
        }
      }

      // Things can also be set in settings properties, which are different from project properties
      // This is because included builds are defined in settings.gradle before build.gradle or its plugins are even evaluated
      // So, we also need to check the parent settings properties to see if we have anything to transfer into the child project properties
      if (project.gradle.parent.gradle.settings != null && project.gradle.parent.gradle.settings.ext != null) {
        project.gradle.parent.gradle.settings.ext.properties.each { prop ->
          applyPropertyIfAllowed(project, prop)
        }
      }
    }
  }
}
