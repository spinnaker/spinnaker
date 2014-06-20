package com.netflix.spinnaker

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import spock.lang.Shared
import spock.lang.Specification

class SpinnakerDependenciesSpec extends Specification {

  @Shared
  SpinnakerDependencies spinnaker

  @Shared
  Project project

  @Shared
  DependencyHandler dependencyHandler

  def setup() {
    project = Mock(Project)
    dependencyHandler = Mock(DependencyHandler)
    project.dependencies >> dependencyHandler
    spinnaker = new SpinnakerDependencies(project)
  }

  void "resolve dependencies by name"() {
    when:
      spinnaker.dependency("bootActuator")


    then:
      1 * dependencyHandler.create(_)
  }

  void "resolve group dependencies by scope"() {
    when:
      spinnaker.group("bootWeb")

    then:
      3 * dependencyHandler.create(_) >> Mock(Dependency)
      3 * dependencyHandler.add('compile', _)
  }
}
