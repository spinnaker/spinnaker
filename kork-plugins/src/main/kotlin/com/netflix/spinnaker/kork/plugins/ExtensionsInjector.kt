/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kork.plugins

import org.pf4j.PluginManager
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.RootBeanDefinition

/**
 * Injects extensions into the parent [ApplicationContext] as beans, allowing the primary
 * application to autowire enabled plugins into their correct places.
 */
class ExtensionsInjector(
  private val pluginManager: PluginManager,
  private var registry: BeanDefinitionRegistry
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  fun injectExtensions() {
    // add extensions from classpath (non plugin)
    var extensionClassNames = pluginManager.getExtensionClassNames(null)
    for (extensionClassName in extensionClassNames) {
      try {
        log.debug("Register extension '{}' as bean", extensionClassName)
        val extensionClass = javaClass.classLoader.loadClass(extensionClassName)
        registerExtension(extensionClass)
      } catch (e: ClassNotFoundException) {
        log.error(e.message, e)
      }
    }

    // add extensions for each started plugin
    val startedPlugins = pluginManager.startedPlugins
    for (plugin in startedPlugins) {
      log.debug("Registering extensions of the plugin '{}' as beans", plugin.pluginId)
      extensionClassNames = pluginManager.getExtensionClassNames(plugin.pluginId)
      for (extensionClassName in extensionClassNames) {
        try {
          log.debug("Register extension '{}' as bean", extensionClassName)
          val extensionClass = plugin.pluginClassLoader.loadClass(extensionClassName)
          registerExtension(extensionClass)
        } catch (e: ClassNotFoundException) {
          log.error(e.message, e)
        }
      }
    }
  }

  private fun registerExtension(extensionClass: Class<*>) {
    val extension = pluginManager.extensionFactory.create(extensionClass)

    val definition = RootBeanDefinition(extension.javaClass)
    definition.targetType = extensionClass
    definition.role = BeanDefinition.ROLE_APPLICATION

    registry.registerBeanDefinition(extension.javaClass.name, definition)
  }
}
