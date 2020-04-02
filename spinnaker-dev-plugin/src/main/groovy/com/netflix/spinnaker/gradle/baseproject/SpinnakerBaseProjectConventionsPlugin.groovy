package com.netflix.spinnaker.gradle.baseproject

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.jvm.tasks.Jar

class SpinnakerBaseProjectConventionsPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
      project.plugins.withType(JavaPlugin) {
        project.repositories.jcenter()
        JavaPluginConvention convention = project.convention.getPlugin(JavaPluginConvention)
        convention.sourceCompatibility = JavaVersion.VERSION_1_8
        convention.targetCompatibility = JavaVersion.VERSION_1_8
      }
      // Nebula insists on building Javadoc, but we don't do anything with it
      // and it seems to cause lots of errors.
      project.tasks.withType(Javadoc) { (it as Javadoc).setFailOnError(false) }
      project.tasks.withType(Jar) { setImplementationOssVersion((it as Jar), project) }
      project.afterEvaluate {
        project.tasks
          .getByName("clean")
          .doLast {
            project.delete(project.files("${project.projectDir}/plugins"))
          }
      }
    }

  /**
   * If this the property "ossVersion" exists the MANIFEST.MF "Implementation-OSS-Version" attribute
   * will be set to the corresponding property. This can be used to support use cases where services
   * are being extended and rebuilt.  Unless you're re-building services, this is likely unnecessary
   * and the default value of the attribute "Implementation-Version" will suffice for determining
   * the service version.
   */
    private static void setImplementationOssVersion(Jar jar, Project project) {
      String ossVersionProperty = "ossVersion"
      if (project.hasProperty(ossVersionProperty)) {
        jar.manifest {
          it.attributes(["Implementation-OSS-Version": project.property(ossVersionProperty)])
        }
      }
    }
}
