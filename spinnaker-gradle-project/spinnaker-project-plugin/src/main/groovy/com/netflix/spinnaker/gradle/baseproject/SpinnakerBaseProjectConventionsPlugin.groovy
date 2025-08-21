package com.netflix.spinnaker.gradle.baseproject

import groovy.transform.CompileStatic
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.java.archives.Manifest
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.jvm.tasks.Jar

@CompileStatic
class SpinnakerBaseProjectConventionsPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
      project.repositories.mavenCentral()
      project.plugins.withType(JavaBasePlugin) {
        project.extensions.getByType(JavaPluginExtension).with {
          it.setSourceCompatibility(JavaVersion.VERSION_17)
          it.setTargetCompatibility(JavaVersion.VERSION_17)
        }
        project.plugins.apply(MavenPublishPlugin)
      }
      project.plugins.withType(JavaLibraryPlugin).configureEach {
        JavaPluginExtension extension = project.extensions.getByType(JavaPluginExtension)
        def sourceJar = project.tasks.create("sourceJar", Jar)
        sourceJar.dependsOn("classes")
        sourceJar.archiveClassifier.set('sources')
        sourceJar.from(extension.sourceSets.getByName('main').allSource)
        project.artifacts.add('archives', sourceJar)
      }
      // Nebula insists on building Javadoc, but we don't do anything with it
      // and it seems to cause lots of errors.
      project.tasks.withType(Javadoc) { (it as Javadoc).setFailOnError(false) }
      project.tasks.withType(Jar) { setImplementationVersion((it as Jar), project) }

      project.plugins.withType(BasePlugin) {
        Delete clean = project.getTasks().getByName(BasePlugin.CLEAN_TASK_NAME) as Delete
        clean.delete("${project.projectDir}/plugins")
      }
    }

  private static void setImplementationVersion(Jar jar, Project project) {
    def version = project.findProperty("ossVersion") ?: project.getVersion()
    if (version != Project.DEFAULT_VERSION) {
      jar.manifest {
        (it as Manifest).attributes(["Implementation-Version": version.toString()])
      }
    }
  }
}
