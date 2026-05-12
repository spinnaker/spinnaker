package com.netflix.spinnaker.gradle.baseproject

import groovy.transform.CompileStatic
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.java.archives.Manifest
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar

@CompileStatic
class SpinnakerBaseProjectConventionsPlugin implements Plugin<Project> {
  private static final String PARAMETERS_COMPILER_ARG = "-parameters";
  @Override
  void apply(Project project) {
    project.repositories.mavenCentral()

    project.plugins.withType(JavaBasePlugin) {
      project.plugins.apply(MavenPublishPlugin)
      project.extensions.getByType(JavaPluginExtension).with {
        it.setSourceCompatibility(JavaVersion.VERSION_17)
        it.setTargetCompatibility(JavaVersion.VERSION_17)
      }
    }
    // with out these two compile blocks, bean discoveyr fails BADLY on multiple conflicting objects.  See
    // https://github.com/spring-projects/spring-boot/pull/9839/changes#diff-75fcbf238dc6bf69b7a28dd69c5c0c96a9464684bdb1379f15a92589e7534f7fR129-R130 for an example
    
    project.getTasks().withType(GroovyCompile, compile -> {
      final List<String> compilerArgs = ((GroovyCompile)compile).getOptions().getCompilerArgs();
      if (!compilerArgs.contains(PARAMETERS_COMPILER_ARG)) {
        compilerArgs.add(PARAMETERS_COMPILER_ARG);
      } 
    })
    // This is needed for serialization handling in later releases
    project.getTasks().withType(JavaCompile.class, compile -> {
      final List<String> compilerArgs = ((JavaCompile)compile).getOptions().getCompilerArgs();
      if (!compilerArgs.contains(PARAMETERS_COMPILER_ARG)) {
        compilerArgs.add(PARAMETERS_COMPILER_ARG);
      }
    })
    
    project.plugins.withType(JavaLibraryPlugin).configureEach {
      JavaPluginExtension extension = project.extensions.getByType(JavaPluginExtension)
      def sourceJar = project.tasks.create("sourceJar", Jar)
      sourceJar.dependsOn("classes")
      sourceJar.archiveClassifier.set('sources')
      sourceJar.from(extension.sourceSets.getByName('main').allSource)
      project.artifacts.add('archives', sourceJar)
      extension    }
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
