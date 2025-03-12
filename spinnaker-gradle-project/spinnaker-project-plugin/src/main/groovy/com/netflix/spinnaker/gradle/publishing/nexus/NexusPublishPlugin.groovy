/*
 * Copyright 2021 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.gradle.publishing.nexus

import com.netflix.spinnaker.gradle.publishing.PublishingPlugin
import io.github.gradlenexus.publishplugin.NexusPublishPlugin as BaseNexusPublishPlugin
import io.github.gradlenexus.publishplugin.NexusPublishExtension as BaseNexusPublishExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlatformPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.Publication
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin
import org.jetbrains.dokka.gradle.DokkaPlugin

class NexusPublishPlugin implements Plugin<Project> {

  @Override
  void apply(Project project) {
    def nexusExtension = project.extensions.create("nexusSpinnaker", NexusPublishExtension, project)
    if (!nexusExtension.enabled().get()) {
      return
    }

    if (project == project.rootProject) {
      configureBaseNexusPlugin(project, nexusExtension)
    }

    project.plugins.withType(JavaLibraryPlugin) {
      project.plugins.apply(MavenPublishPlugin)
      project.plugins.apply(SigningPlugin)

      configureSpinnakerPublicationForNexus(project, nexusExtension)
    }
    project.plugins.withType(JavaPlatformPlugin) {
      project.plugins.apply(MavenPublishPlugin)
      project.plugins.apply(SigningPlugin)

      configureSpinnakerPublicationForNexus(project, nexusExtension)
    }
  }

  private void configureBaseNexusPlugin(Project project, NexusPublishExtension nexusExtension) {
    project.plugins.apply(BaseNexusPublishPlugin)
    project.extensions.configure(BaseNexusPublishExtension) { nexus ->
      nexus.repositories.create("nexus") {
        nexusUrl = new URI(nexusExtension.nexusStagingUrl().get())
        snapshotRepositoryUrl = new URI(nexusExtension.nexusSnapshotUrl().get())
        username = nexusExtension.nexusUsername()
        password = nexusExtension.nexusPassword()
        stagingProfileId = nexusExtension.nexusStagingProfileId()
      }
    }
  }

  private void configureSpinnakerPublicationForNexus(Project project, NexusPublishExtension nexusExtension) {
    project.extensions.configure(PublishingExtension) { publishingExtension ->
      def spinnakerPublication = publishingExtension.publications.getByName(PublishingPlugin.PUBLICATION_NAME)

      configurePom(project, spinnakerPublication)

      project.extensions.configure(SigningExtension) { signingExtension ->
        signingExtension.useInMemoryPgpKeys(nexusExtension.pgpSigningKey(), nexusExtension.pgpSigningPassword())
        signingExtension.sign(spinnakerPublication)
      }

      if (project.plugins.hasPlugin(DokkaPlugin)) {
        def javadocTask = project.tasks.findByName("dokkaJavadoc")
        def dokkaJar = project.task(type: Jar, "dokkaJar") {
          archiveClassifier.set("javadoc")
          from(javadocTask)
        }
        spinnakerPublication.artifact(dokkaJar)
      } else if (project.plugins.hasPlugin(JavaPlugin)) {
        def javadocTask = project.tasks.findByName(JavaPlugin.JAVADOC_TASK_NAME)
        def javadocJar = project.task(type: Jar, "javadocJar") {
          archiveClassifier.set("javadoc")
          from(javadocTask)
        }
        spinnakerPublication.artifact(javadocJar)
      }
    }
  }

  void configurePom(Project project, Publication publication) {
    def service = project.rootProject.name
    publication.pom {
      name = service
      description = "Spinnaker ${service.capitalize()}"
      url = "https://github.com/spinnaker/$service"
      licenses {
        license {
          name = "The Apache License, Version 2.0"
          url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
        }
      }
      developers {
        developer {
          id = "toc"
          name = "Technical Oversight Committee"
          email = "toc@spinnaker.io"
        }
      }
      inceptionYear = "2014"
      scm {
        connection = "scm:git:git://github.com/spinnaker/${service}.git"
        developerConnection = "scm:git:ssh://github.com/spinnaker/${service}.git"
        url = "http://github.com/spinnaker/${service}/"
      }
      issueManagement {
        system = "GitHub Issues"
        url = "https://github.com/spinnaker/spinnaker/issues"
      }
    }
  }
}
