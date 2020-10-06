package com.netflix.spinnaker.gradle.publishing.artifactregistry

import com.google.cloud.artifactregistry.gradle.plugin.ArtifactRegistryGradlePlugin
import com.netflix.gradle.plugins.deb.Deb
import com.netflix.gradle.plugins.packaging.SystemPackagingPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlatformPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.TaskProvider

class ArtifactRegistryPublishPlugin implements Plugin<Project> {
  @Override
  void apply(Project project) {

    def extension = project.extensions.create("artifactRegistrySpinnaker", ArtifactRegistryPublishExtension, project)

    if (!extension.enabled().get()) {
      return
    }

    if (extension.mavenEnabled().get()) {
      project.plugins.withType(JavaLibraryPlugin) {
        project.plugins.apply(MavenPublishPlugin)
        project.plugins.apply(ArtifactRegistryGradlePlugin)
        project.extensions.configure(PublishingExtension) { publishingExtension ->
          publishingExtension.repositories.maven {
            it.name = 'artifactRegistry'
            it.url = extension.mavenUrl().get()
          }
        }
      }

      project.plugins.withType(JavaPlatformPlugin) {
        project.plugins.apply(MavenPublishPlugin)
        project.plugins.apply(ArtifactRegistryGradlePlugin)
        project.extensions.configure(PublishingExtension) { publishingExtension ->
          publishingExtension.repositories.maven {
            it.name = 'artifactRegistry'
            it.url = extension.mavenUrl().get()
          }
        }
      }
    }

    if (extension.aptEnabled().get()) {
      project.plugins.withType(SystemPackagingPlugin) {
        TaskProvider<Deb> debTask = project.tasks.named("buildDeb", Deb)
        TaskProvider<Task> publishDeb = project.tasks.register("publishDebToArtifactRegistry", ArtifactRegistryDebPublishTask) {
          it.archiveFile = debTask.flatMap { it.archiveFile }
          it.uploadBucket = extension.aptTempGcsBucket()
          it.repoProject = extension.aptProject()
          it.location = extension.aptLocation()
          it.repository = extension.aptRepository()
          it.aptImportTimeoutSeconds = extension.aptImportTimeoutSeconds
          it.dependsOn(debTask)
          it.onlyIf { extension.enabled().get() }
          it.onlyIf { extension.aptEnabled().get() }
          it.onlyIf { project.version.toString() != Project.DEFAULT_VERSION }
        }

        project.tasks.matching { it.name == "publish" }.configureEach {
          it.dependsOn(publishDeb)
        }
      }
    }
  }
}
