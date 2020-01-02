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

import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.plugins.events.ExtensionLoaded
import com.netflix.spinnaker.kork.plugins.proxy.ExtensionInvocationProxy
import com.netflix.spinnaker.kork.plugins.proxy.aspects.InvocationAspect
import com.netflix.spinnaker.kork.plugins.proxy.aspects.InvocationState
import com.netflix.spinnaker.kork.plugins.update.PluginUpdateService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.context.ApplicationEventPublisher

/**
 * The primary point of integration between PF4J and Spring, this class is invoked early
 * in the Spring application lifecycle (before any other Beans are created), but after
 * the environment is prepared.
 */
class ExtensionBeanDefinitionRegistryPostProcessor(
  private val pluginManager: SpinnakerPluginManager,
  private val updateManagerService: PluginUpdateService,
  private val applicationEventPublisher: ApplicationEventPublisher,
  private val invocationAspects: List<InvocationAspect<*>>
) : BeanDefinitionRegistryPostProcessor {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun postProcessBeanDefinitionRegistry(registry: BeanDefinitionRegistry) {
    val start = System.currentTimeMillis()
    log.debug("Preparing plugins")
    updateManagerService.checkForUpdates()
    pluginManager.loadPlugins()
    pluginManager.startPlugins()

    log.debug("Finished preparing plugins in {}ms", System.currentTimeMillis() - start)
  }

  override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
    pluginManager.getExtensionClassNames(null).forEach {
      log.debug("Creating extension '{}'", it)

      val extensionClass = try {
        javaClass.classLoader.loadClass(it)
      } catch (e: ClassNotFoundException) {
        throw IntegrationException("Could not find system extension class '$it'", e)
      }

      val bean = pluginManager.extensionFactory.create(extensionClass)
      val beanName = "${extensionClass.simpleName.decapitalize()}SystemExtension"

      beanFactory.registerSingleton(beanName, bean)

      applicationEventPublisher.publishEvent(ExtensionLoaded(this, beanName, extensionClass))
    }

    pluginManager.startedPlugins.forEach { plugin ->
      if (plugin.isUnsafe()) return@forEach
      log.debug("Creating extensions for plugin '{}'", plugin.pluginId)

      val pluginExtensions = pluginManager.getExtensionClassNames(plugin.pluginId)
      if (pluginExtensions.isNullOrEmpty()) log.warn("No extensions found for plugin '{}'", plugin.pluginId)

      pluginExtensions.forEach {
        log.debug("Creating extension '{}' for plugin '{}'", it, plugin.pluginId)

        val extensionClass = try {
          plugin.pluginClassLoader.loadClass(it)
        } catch (e: ClassNotFoundException) {
          throw IntegrationException("Could not find extension class '$it' for plugin '${plugin.pluginId}'", e)
        }

        val bean = ExtensionInvocationProxy.proxy(
          pluginManager.extensionFactory.create(extensionClass),
          invocationAspects as List<InvocationAspect<InvocationState>>,
          plugin.descriptor as SpinnakerPluginDescriptor)

        val beanName = "${plugin.pluginId.replace(".", "")}${extensionClass.simpleName.capitalize()}"

        beanFactory.registerSingleton(beanName, bean)

        applicationEventPublisher.publishEvent(
          ExtensionLoaded(this, beanName, extensionClass, plugin.descriptor as SpinnakerPluginDescriptor)
        )
      }
    }
  }
}
