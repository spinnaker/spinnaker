package com.netflix.spinnaker.gradle.baseproject

import com.netflix.spinnaker.gradle.Flags
import groovy.transform.CompileStatic
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.java.archives.Manifest
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar

@CompileStatic
class SpinnakerBaseProjectConventionsPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
      def javaVersion = Flags.targetJava17(project) ? JavaVersion.VERSION_17 : JavaVersion.VERSION_11
      project.repositories.mavenCentral()
      project.plugins.withType(JavaBasePlugin) {
        project.plugins.apply(MavenPublishPlugin)
        JavaPluginConvention convention = project.convention.getPlugin(JavaPluginConvention)
        convention.sourceCompatibility = javaVersion
        convention.targetCompatibility = javaVersion
      }
      project.plugins.withType(JavaLibraryPlugin) {
        JavaPluginConvention convention = project.convention.getPlugin(JavaPluginConvention)
        def sourceJar = project.tasks.create("sourceJar", Jar)
        sourceJar.dependsOn("classes")
        sourceJar.archiveClassifier.set('sources')
        sourceJar.from(convention.sourceSets.getByName('main').allSource)
        project.artifacts.add('archives', sourceJar)
      }

      // Disable JVM class data sharing in test workers to avoid this warning on startup
      //
      // "Sharing is only supported for boot loader classes because bootstrap classpath has been appended"
      //
      // This has a minor startup performance cost (~13ms per JVM invocation), but it seems worth it to clean up the noise.
      //
      // See also https://stackoverflow.com/questions/54205486/how-to-avoid-sharing-is-only-supported-for-boot-loader-classes-because-bootstra
      project.tasks.withType(Test).configureEach { it.jvmArgs('-Xshare:off') }

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
