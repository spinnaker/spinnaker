/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.publishing

import com.jfrog.bintray.gradle.BintrayExtension
import com.netflix.gradle.plugins.deb.Deb
import com.netflix.gradle.plugins.packaging.SystemPackagingPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

class BintrayDebPublishingPlugin implements Plugin<Project> {
  @Override
  void apply(Project project) {
    project.plugins.apply(SystemPackagingPlugin)

    def bintrayBaseExtension = project.extensions.getByType(BintrayExtension)
    def extension = project.extensions.create('bintrayDebPublishing', BintrayDebPublishingExtension)

    def buildDebTask = ((Deb) project.tasks.buildDeb)

    BintrayDebPublishingTask publishTask = (BintrayDebPublishingTask)project.task(type: BintrayDebPublishingTask, 'bintrayDebPublish') {
      conventionMapping.with {
        apiUrl = { bintrayBaseExtension.apiUrl }
        user = { bintrayBaseExtension.user }
        userOrg = { bintrayBaseExtension.pkg.userOrg ?: bintrayBaseExtension.user }
        repoName = { extension.repoName }
        packageName = { extension.packageName ?: project.rootProject.name }
        version = { extension.packageVersion ?: project.version.replaceAll('-SNAPSHOT', '') }
        distribution = { extension.distribution }
        component = { extension.component }
        architecture = { extension.architecture }
        packagePath = { extension.packagePath ?: buildDebTask.archiveName }
        debFile = { extension.debFile ?: buildDebTask.archivePath }
        dryRun = { extension.dryRun }
      }
    }
    project.tasks.publish.dependsOn 'bintrayDebPublish'
    publishTask.baseExtension = bintrayBaseExtension
    publishTask.dependsOn 'buildDeb'
  }
}
