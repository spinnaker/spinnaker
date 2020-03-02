package com.netflix.spinnaker.gradle.baseproject

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention

class SpinnakerBaseProjectConventionsPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
      project.plugins.withType(JavaPlugin) {
        project.repositories.jcenter()
        JavaPluginConvention convention = project.convention.getPlugin(JavaPluginConvention)
        convention.sourceCompatibility = JavaVersion.VERSION_1_8
        convention.targetCompatibility = JavaVersion.VERSION_1_8
      }
      project.afterEvaluate {
        project.tasks
          .getByName("clean")
          .doLast {
            project.delete(project.files("${project.projectDir}/plugins"))
          }
      }
    }
}
