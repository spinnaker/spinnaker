package com.netflix.spinnaker.gradle.publishing.bintray

import com.netflix.gradle.plugins.deb.Deb
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

class BintrayPublishExtension {
  protected final Project project

  final Property<Boolean> enabled

  final Property<Boolean> jarEnabled
  final Property<Boolean> debEnabled

  final Property<String> bintrayOrg
  final Property<String> bintrayJarRepo
  final Property<String> bintrayJarPackage

  final Property<String> bintrayDebRepo

  final Property<String> debDistribution
  final Property<String> debComponent
  final Property<String> debArchitectures
  final Property<Integer> publishWaitForSecs

  BintrayPublishExtension(Project project) {
    this.project = project
    ObjectFactory props = project.objects
    enabled = props.property(Boolean).convention(true)
    jarEnabled = props.property(Boolean).convention(true)
    debEnabled = props.property(Boolean).convention(true)
    bintrayOrg = props.property(String).convention("spinnaker")
    bintrayJarRepo = props.property(String).convention("spinnaker")
    bintrayJarPackage = props.property(String).convention(project.rootProject.name)

    bintrayDebRepo = props.property(String).convention("debians")
    debDistribution = props.property(String).convention("trusty,xenial,bionic")
    debComponent = props.property(String).convention("spinnaker")
    debArchitectures = props.property(String).convention( "i386,amd64")
    publishWaitForSecs = props.property(Integer).convention(0)
  }

  //------------------------------------------------------------------------
  //
  // Note the following accessor methods do not follow the java getter style
  // because the Gradle lazy properties generate getter and setter methods
  // for each property, but we want a customized getter behavior in most
  // cases to allow a project property to override the value from build
  // configuration
  //
  //------------------------------------------------------------------------


  Provider<Boolean> enabled() {
    return withSysProp(enabled, Boolean, "bintrayPublishEnabled")
  }

  Provider<Boolean> jarEnabled() {
    return withSysProp(jarEnabled, Boolean, "bintrayPublishJarEnabled")
  }

  Provider<Boolean> debEnabled() {
    return withSysProp(debEnabled, Boolean, "bintrayPublishDebEnabled")
  }

  Provider<String> bintrayOrg() {
    return withSysProp(bintrayOrg, String, "bintrayOrg")
  }

  Provider<String> bintrayJarRepo() {
    return withSysProp(bintrayJarRepo, String, "bintrayJarRepo")
  }

  Provider<String> bintrayJarPackage() {
    return withSysProp(bintrayJarPackage, String, "bintrayJarPackage")
  }

  Provider<String> bintrayDebRepo() {
    return withSysProp(bintrayDebRepo, String, "bintrayPackageRepo")
  }

  Provider<String> debDistribution() {
    return withSysProp(debDistribution, String, "bintrayPackageDebDistribution")
  }

  Provider<String> debComponent() {
    return debComponent
  }

  Provider<String> debArchitectures() {
    return debArchitectures
  }

  Provider<Integer> publishWaitForSecs() {
    return withSysProp(publishWaitForSecs, Integer, "bintrayPublishWaitForSecs")
  }

  String bintrayUser() {
    return projectProperty(String, "bintrayUser")
  }

  String bintrayKey() {
    return projectProperty(String, "bintrayKey")
  }

  boolean hasCreds() {
    return bintrayUser() && bintrayKey()
  }

  String basicAuthHeader() {
    "Basic " + "${bintrayUser()}:${bintrayKey()}".getBytes('UTF-8').encodeBase64()
  }

  Provider<String> jarPublishUri() {
    return bintrayOrg().flatMap { String org ->
      bintrayJarRepo().flatMap { String repo ->
        bintrayJarPackage().map { String pkg ->
          "https://api.bintray.com/maven/$org/$repo/$pkg/".toString()
        }
      }
    }
  }

  Provider<String> jarMavenRepoUrl() {
    return bintrayOrg().flatMap { String org ->
      bintrayJarRepo().map { String repo ->
        "https://dl.bintray.com/$org/$repo/".toString()
      }
    }
  }

  Provider<String> jarPublishVersionUri() {
    return bintrayOrg().flatMap { String org ->
      bintrayJarRepo().flatMap { String repo ->
        bintrayJarPackage().map { String pkg ->
          "https://api.bintray.com/content/$org/$repo/$pkg/${project.version}/publish".toString()
        }
      }
    }
  }

  Provider<String> jarCreateVersionUri() {
    return bintrayOrg().flatMap { String org ->
      bintrayJarRepo().flatMap { String repo ->
        bintrayJarPackage().map { String pkg ->
          "https://api.bintray.com/packages/$org/$repo/$pkg/versions".toString()
        }
      }
    }
  }

  Provider<String> debPublishUri(TaskProvider<Deb> debTaskProvider) {
    return debTaskProvider.flatMap { Deb deb ->
      deb.archiveFile.flatMap { debFile ->
        bintrayOrg().flatMap { String org ->
          bintrayDebRepo().flatMap { String repo ->
            debDistribution().flatMap { String dist ->
              debComponent().flatMap { String component ->
                debArchitectures().map { String arch ->
                  def packageName = deb.packageName
                  def poolPath = "pool/main/${packageName.charAt(0)}/$packageName"
                  def debFileName = debFile.getAsFile().name
                  String versionName = project.version.toString()
                  if (versionName.endsWith('-SNAPSHOT')) {
                    versionName = versionName.replaceAll(/SNAPSHOT/, Long.toString(System.currentTimeMillis()))
                  }

                  return "https://api.bintray.com/content/$org/$repo/$packageName/$versionName/$poolPath/$debFileName;deb_distribution=$dist;deb_component=$component;deb_architecture=$arch;publish=1".toString()
                }
              }
            }
          }
        }
      }
    }
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

  protected <T> T projectProperty(Class<T> type, String projectPropertyName) {
    return projectProperty(type, projectPropertyName, null)
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
