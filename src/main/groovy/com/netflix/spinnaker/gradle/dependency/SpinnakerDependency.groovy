/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gradle.dependency

import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import org.codehaus.groovy.runtime.GStringImpl
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.yaml.snakeyaml.Yaml

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class SpinnakerDependency {

    private static final String OVERRIDE_PROJECT_PROPERTY = 'spinnaker.dependenciesVersion'
    private static final String DEFAULT_DEPENDENCIES_VERSION = 'latest.release'
    private static final GString DEFAULT_DEPENDENCIES_YAML = "com.netflix.spinnaker:spinnaker-dependencies:${DEFAULT_DEPENDENCIES_VERSION}@yml"
    private final Project project
    private final DependencyBuilder dependencyBuilder = new DependencyBuilder()
    private final AtomicBoolean configResolved = new AtomicBoolean(false)
    private final Lock configLock = new ReentrantLock()

    public Object dependenciesVersion
    public Object dependenciesYaml

    SpinnakerDependency(Project project) {
        this.project = project
    }

    private void ensureConfigResolved() {
        if (configResolved.get()) {
            return
        }

        configLock.lockInterruptibly()
        try {
            if (configResolved.get()) {
                return
            }

            String dependenciesVersion = project.hasProperty(OVERRIDE_PROJECT_PROPERTY) ? project.property(OVERRIDE_PROJECT_PROPERTY) : dependenciesVersion ?: DEFAULT_DEPENDENCIES_VERSION
            Object dependencyString = dependenciesYaml ?: new GStringImpl([dependenciesVersion] as Object[], DEFAULT_DEPENDENCIES_YAML.strings)

            def conf = project.configurations.detachedConfiguration(project.dependencies.create(dependencyString))

            Map config = conf.singleFile.withReader {
                return (Map) new Yaml().load(it)
            }
            dependencyBuilder.configuration.clear()
            def depKeys = ['dependencies', 'versions', 'groups']
            for (String key : depKeys) {
                dependencyBuilder.configuration.put(key, [:])
            }
            dependencyBuilder.configuration.putAll(config)

            def extExtension = project.extensions.getByType(ExtraPropertiesExtension)
            if (extExtension) {
                for (String key : depKeys) {
                    if (extExtension.has(key)) {
                        def extVal = extExtension.get(key)
                        if (extVal instanceof Map) {
                            dependencyBuilder.configuration.get(key).putAll(extVal)
                        }
                    }
                }
            }
            configResolved.set(true)
        } finally {
            configLock.unlock()
        }
    }

    Object dependency(String name) {
        ensureConfigResolved()
        return dependencyBuilder.buildDependencyFromTemplate(name)
    }

    void group(String name) {
        ensureConfigResolved()
        Map<String, List<String>> group = dependencyBuilder.ensureGroupExists(name)

        def db = dependencyBuilder

        project.dependencies { DependencyHandler dh ->
            for (Map.Entry<String, List<String>> deps : group.entrySet()) {
                for (String dep : deps.value) {
                    dh.add(deps.key, db.buildDependencyFromTemplate(dep))
                }
            }
        }
    }

    String version(String name) {
        ensureConfigResolved()
        return dependencyBuilder.ensureVersionExists(name)
    }


    private static class DependencyBuilder {
        private final SimpleTemplateEngine templateEngine = new SimpleTemplateEngine()
        private final Map<String, Template> templates = new ConcurrentHashMap<>()
        private final Map<String, ?> configuration = [:]

        DependencyBuilder() {
        }

        public Map<String, ?> getConfiguration() {
            return configuration
        }

        Map<String, List<String>> ensureGroupExists(String name) {
            def group = configuration.groups[name]
            if (!group) {
                throw new NoSuchElementException("No dependency group $name in $configuration.groups")
            }
            return group
        }

        String ensureVersionExists(String name) {
            def version = configuration.versions[name]
            if (!version) {
                throw new NoSuchElementException("No version $name in $configuration.versions")
            }
            return version
        }

        private Template getTemplate(String name, String templateText) {
            def template = templates.get(name)
            if (template) {
                return template
            }

            template = templateEngine.createTemplate(templateText)
            return templates.putIfAbsent(name, template) ?: template
        }

        public Object buildDependencyFromTemplate(String name) {
            String depTemplate = configuration.dependencies[name]

            if (!depTemplate) {
                throw new NoSuchElementException("No dependency $name in $configuration.dependencies")
            }

            def tmpl = getTemplate(name, depTemplate)
            return tmpl.make(configuration).toString()
        }
    }
}
