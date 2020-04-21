package com.netflix.spinnaker.gradle.publishing.bintray

import com.netflix.gradle.plugins.deb.Deb
import com.netflix.gradle.plugins.packaging.SystemPackagingPlugin
import com.netflix.spinnaker.gradle.Flags
import com.netflix.spinnaker.gradle.publishing.PublishingPlugin
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.TaskProvider

class BintrayPublishPlugin implements Plugin<Project> {
  private static String MAVEN_REPO_NAME = 'bintraySpinnaker'

  @Override
  void apply(Project project) {
    def extension = project.extensions.create("bintraySpinnaker", BintrayPublishExtension, project)
    if (!extension.hasCreds()) {
      project.logger.info("Not configuring bintray publishing, ensure bintrayUser and bintrayKey project properties are set")
      return
    }

    project.plugins.withType(MavenPublishPlugin) {
      try {
        project.rootProject.tasks.register("createBintrayVersion", BintrayCreateVersionTask) {
          it.createVersionUri = extension.jarCreateVersionUri()
          it.packageName = extension.bintrayJarPackage()
          it.bintrayAuthHeader = extension.basicAuthHeader()
          it.onlyIf { extension.enabled().get() }
          it.onlyIf { extension.jarEnabled().get() }
          it.onlyIf { project.version.toString() != Project.DEFAULT_VERSION }
        }
      } catch (InvalidUserDataException duplicateTask) {
        //we need a single top level task to create the version, ignore if another module already configured it
      }

      try {
        project.rootProject.tasks.register("publishBintrayVersion", BintrayPublishVersionTask) {
          it.publishUri = extension.jarPublishVersionUri()
          it.bintrayAuthHeader = extension.basicAuthHeader()
          it.publishWaitForSecs = extension.publishWaitForSecs()
          it.onlyIf { extension.enabled().get() }
          it.onlyIf { extension.jarEnabled().get() }
          it.onlyIf { project.version.toString() != Project.DEFAULT_VERSION }
        }
      } catch (InvalidUserDataException duplicateTask) {
        //we need a single top level task to publish the version, ignore if another module already configured it
      }

      project.extensions.configure(PublishingExtension) { publishing ->
        publishing.repositories.maven { MavenArtifactRepository repo ->
          repo.name = 'bintraySpinnaker'
          repo.url = extension.jarPublishUri()
          repo.credentials {
            username = extension.bintrayUser()
            password = extension.bintrayKey()
          }
        }

        def publishVersionTask = project.rootProject.tasks.named("publishBintrayVersion")
        def createVersionTask = project.rootProject.tasks.named("createBintrayVersion")

        project.tasks.matching { Task t ->
          t.name == "publish${PublishingPlugin.PUBLICATION_NAME.capitalize()}PublicationTo${MAVEN_REPO_NAME.capitalize()}Repository"
        }.configureEach {
          it.onlyIf { extension.enabled().get() }
          it.onlyIf { extension.jarEnabled().get() }
          it.onlyIf { project.version.toString() != Project.DEFAULT_VERSION }
          it.dependsOn(createVersionTask)
          it.finalizedBy(publishVersionTask)
        }
      }
    }

    project.plugins.withType(SystemPackagingPlugin) {
      TaskProvider<Deb> debTask = project.tasks.named("buildDeb", Deb)
      TaskProvider<Task> publishDeb = project.tasks.register("publishDeb", BintrayDebUploadTask) {
          it.archiveFile = debTask.flatMap { it.archiveFile }
          it.bintrayAuthHeader = extension.basicAuthHeader()
          it.publishUri = extension.debPublishUri(debTask)
          it.dependsOn(debTask)
          it.onlyIf { extension.enabled().get() }
          it.onlyIf { extension.debEnabled().get() }
          it.onlyIf { project.version.toString() != Project.DEFAULT_VERSION }
      }

      project.tasks.named('publish') {
        dependsOn(publishDeb)
      }
    }
  }
}
