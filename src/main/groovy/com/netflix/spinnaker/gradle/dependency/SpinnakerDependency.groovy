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
    private final Map dependencyConfig = [:]
    private final SimpleTemplateEngine templateEngine = new SimpleTemplateEngine()
    private final Map<String, Template> templates = new ConcurrentHashMap<>()

    private final AtomicBoolean configResolved = new AtomicBoolean(false)
    private final Lock configLock = new ReentrantLock()



    public Object dependenciesVersion
    public Object dependenciesYaml

    SpinnakerDependency(Project project) {
        this.project = project
    }

    private Map getConfig() {
        if (configResolved.get()) {
            return dependencyConfig
        }

        configLock.lockInterruptibly()
        try {
            if (configResolved.get()) {
                return dependencyConfig
            }

            String dependenciesVersion = project.hasProperty(OVERRIDE_PROJECT_PROPERTY) ? project.property(OVERRIDE_PROJECT_PROPERTY) : dependenciesVersion ?: DEFAULT_DEPENDENCIES_VERSION
            Object dependencyString = dependenciesYaml ?: new GStringImpl([dependenciesVersion] as Object[], DEFAULT_DEPENDENCIES_YAML.strings)

            def conf = project.configurations.detachedConfiguration(project.dependencies.create(dependencyString))

            Map config = conf.singleFile.withReader {
                return (Map) new Yaml().load(it)
            }
            dependencyConfig.clear()
            def depKeys = ['dependencies', 'versions', 'groups']
            for (String key : depKeys) {
              dependencyConfig.put(key, [:])
            }
            dependencyConfig.putAll(config)

            def extExtension = project.extensions.getByType(ExtraPropertiesExtension)
            if (extExtension) {
              for (String key : depKeys) {
                if (extExtension.has(key)) {
                  def extVal = extExtension.get(key)
                  if (extVal instanceof Map) {
                    dependencyConfig.get(key).putAll(extVal)
                  }
                }
              }
            }
            configResolved.set(true)

            return dependencyConfig
        } finally {
            configLock.unlock()
        }
    }

    Dependency dependency(String name) {
        def conf = getConfig()
        return createDependency(name, conf)
    }

    void group(String name) {
        def conf = getConfig()

        def group = (Map<String, List<String>>) conf.groups[name]
        if (!group) {
            throw new NoSuchElementException("No dependency group $name in $conf.groups")
        }

        for (Map.Entry<String, List<String>> deps : group.entrySet()) {
            for (String dep : deps.value) {
                project.dependencies.add(deps.key, createDependency(dep, conf))
            }
        }
    }

    String version(String name) {
        def conf = getConfig()
        def version = conf.versions[name]
        if (!version) {
            throw new NoSuchElementException("No version $name in $conf.versions")
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

    private Dependency createDependency(String name, Map conf) {

        String depTemplate = conf.dependencies[name]

        if (!depTemplate) {
            throw new NoSuchElementException("No dependency $name in $conf.dependencies")
        }

        def tmpl = getTemplate(name, depTemplate)
        return project.dependencies.create(tmpl.make(conf).toString())
    }

}
