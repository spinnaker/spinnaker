/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    def deps = SpinnakerDependencies.getResourceAsStream("/dependencies.yml").text
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
