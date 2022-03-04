package com.netflix.spinnaker.gradle.publishing.artifactregistry

import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

class ArtifactRegistryPublishExtension {

  protected final Project project

  final Property<Boolean> enabled

  final Property<Boolean> mavenEnabled
  final Property<Boolean> aptEnabled

  final Property<String> mavenProject
  final Property<String> mavenLocation
  final Property<String> mavenRepository

  final Property<String> aptProject
  final Property<String> aptLocation
  final Property<String> aptRepository

  final Property<String> aptTempGcsBucket

  final Property<Integer> aptImportTimeoutSeconds;

  ArtifactRegistryPublishExtension(Project project) {
    this.project = project
    ObjectFactory props = project.objects
    enabled = props.property(Boolean).convention(false)
    mavenEnabled = props.property(Boolean).convention(false)
    aptEnabled = props.property(Boolean).convention(true)
    mavenProject = props.property(String).convention("spinnaker-community")
    mavenLocation = props.property(String).convention("us")
    mavenRepository = props.property(String).convention("maven")
    aptProject = props.property(String).convention("spinnaker-community")
    aptLocation = props.property(String).convention("us")
    aptRepository = props.property(String).convention("apt")
    aptTempGcsBucket = props.property(String).convention("spinnaker-deb-temp-uploads")
    aptImportTimeoutSeconds = props.property(Integer).convention(60)
  }

  Provider<Boolean> enabled() {
    return withSysProp(enabled, Boolean, "artifactRegistryPublishEnabled")
  }

  Provider<Boolean> mavenEnabled() {
    return withSysProp(mavenEnabled, Boolean, "artifactRegistryPublishMavenEnabled")
  }

  Provider<Boolean> aptEnabled() {
    return withSysProp(aptEnabled, Boolean, "artifactRegistryPublishAptEnabled")
  }

  Provider<String> mavenProject() {
    return withSysProp(mavenProject, String, "artifactRegistryMavenProject")
  }

  Provider<String> mavenLocation() {
    return withSysProp(mavenLocation, String, "artifactRegistryMavenLocation")
  }

  Provider<String> mavenRepository() {
    return withSysProp(mavenRepository, String, "artifactRegistryMavenRepository")
  }

  Provider<String> mavenUrl() {
    return mavenProject().flatMap { String project ->
      mavenLocation().flatMap { String location ->
        mavenRepository().map { String repository ->
          "artifactregistry://$location-maven.pkg.dev/$project/$repository".toString()
        }
      }
    }
  }

  Provider<String> aptProject() {
    return withSysProp(aptProject, String, "artifactRegistryAptProject")
  }

  Provider<String> aptLocation() {
    return withSysProp(aptLocation, String, "artifactRegistryAptLocation")
  }

  Provider<String> aptRepository() {
    return withSysProp(aptRepository, String, "artifactRegistryAptRepository")
  }

  Provider<String> aptTempGcsBucket() {
    return withSysProp(aptTempGcsBucket, String, "artifactRegistryAptTempGcsBucket")
  }

  Provider<Integer> aptImportTimeoutSeconds() {
    return withSysProp(aptImportTimeoutSeconds, Integer, "artifactRegistryAptImportTimeoutSeconds")
  }

  //------------------------------------------------------------------------
  //
  // Note the following utility methods are protected rather than private
  // because the Gradle lazy properties generate some dynamic subclass that
  // needs visibility into these methods.
  //
  //------------------------------------------------------------------------
  protected <T> Provider<T> withSysProp(Property<T> property, Class<T> type, String projectPropertyName) {
    return property.map { T value ->
      return projectProperty(type, projectPropertyName, value)
    }
  }

  protected <T> T projectProperty(Class<T> type, String projectPropertyName, T defaultValue) {
    if (project.hasProperty(projectPropertyName)) {
      if (type == Boolean) {
        return Boolean.valueOf(project.property(projectPropertyName).toString()) as T
      }
      return project.property(projectPropertyName).asType(type)
    }
    return defaultValue
  }
}
