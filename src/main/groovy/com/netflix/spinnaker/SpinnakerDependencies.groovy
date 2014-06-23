package com.netflix.spinnaker

import groovy.text.SimpleTemplateEngine
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.yaml.snakeyaml.Yaml

@SuppressWarnings("GroovyMissingReturnStatement")
class SpinnakerDependencies {
  private final Map config
  private final Project project
  private final SimpleTemplateEngine templateEngine = new SimpleTemplateEngine()

  SpinnakerDependencies(Project project) {
    this.project = project
    def deps = getClass().getResourceAsStream("/dependencies.yml").text
    def yaml = new Yaml()
    config = (Map) yaml.load(deps)
  }

  Dependency dependency(String module) {
    if (config.dependencies.containsKey(module)) {
      return project.dependencies.create(templateEngine.createTemplate(config.dependencies[module] as String).make(config).toString())
    }
  }

  void group(String name) {
    if (config.groups.containsKey(name)) {
      def $project = project
      def group = config.groups[name]
      group.each { String scope, List<String> dependencies ->
        for (dep in dependencies) {
          def dependency = dependency(dep)
          if (dependency) {
            $project.dependencies.add(scope, dependency)
          }
        }
      }
    }
  }
}
