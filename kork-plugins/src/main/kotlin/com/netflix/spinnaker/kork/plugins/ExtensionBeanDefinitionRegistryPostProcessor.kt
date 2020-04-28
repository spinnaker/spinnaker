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
import com.netflix.spinnaker.kork.plugins.api.spring.PrivilegedSpringPlugin
import com.netflix.spinnaker.kork.plugins.events.ExtensionLoaded
import com.netflix.spinnaker.kork.plugins.proxy.aspects.InvocationAspect
import com.netflix.spinnaker.kork.plugins.update.SpinnakerUpdateManager
import com.netflix.spinnaker.kork.plugins.update.release.provider.PluginInfoReleaseProvider
import kotlin.jvm.javaClass
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
  private val updateManager: SpinnakerUpdateManager,
  private val pluginInfoReleaseProvider: PluginInfoReleaseProvider,
  private val springPluginStatusProvider: SpringPluginStatusProvider,
  private val applicationEventPublisher: ApplicationEventPublisher,
  private val invocationAspects: List<InvocationAspect<*>>
) : BeanDefinitionRegistryPostProcessor {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun postProcessBeanDefinitionRegistry(registry: BeanDefinitionRegistry) {
    val start = System.currentTimeMillis()
    log.debug("Preparing plugins")

    // 1) Load plugins prior to downloading so we can resolve what needs to be updated
    pluginManager.loadPlugins()

    // 2) Determine the plugins for release from the list of enabled plugins
    val releases = updateManager.plugins
      .filter { springPluginStatusProvider.isPluginEnabled(it.id) }
      .let { enabledPlugins -> pluginInfoReleaseProvider.getReleases(enabledPlugins) }

    // 3) Download releases, updating previously loaded plugins where necessary
    updateManager.downloadPluginReleases(releases).forEach { pluginPath ->
      pluginManager.loadPlugin(pluginPath)
    }

    // 4) Start plugins - should only be called once in kork-plugins
    pluginManager.startPlugins()

    pluginManager.startedPlugins.forEach { pluginWrapper ->
      val p = pluginWrapper.plugin
      if (p is PrivilegedSpringPlugin) {
        p.registerBeanDefinitions(registry)
      }
    }

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

        // TODO(rz): Major issues with using an InvocationProxy in extensions today. A lot of services are written
        //  expecting beans to not be proxied and some extension points wind up getting serialized, which proxies
        //  do not handle well. This functionality is very valuable, but we need to think this problem through more.
//        val bean = ExtensionInvocationProxy.proxy(
//          pluginManager.extensionFactory.create(extensionClass),
//          invocationAspects as List<InvocationAspect<InvocationState>>,
//          plugin.descriptor as SpinnakerPluginDescriptor)
        val bean = pluginManager.extensionFactory.create(extensionClass)

        val beanName = "${plugin.pluginId.replace(".", "")}${extensionClass.simpleName.capitalize()}"

        beanFactory.registerSingleton(beanName, bean)

        applicationEventPublisher.publishEvent(
          ExtensionLoaded(this, beanName, extensionClass, plugin.descriptor as SpinnakerPluginDescriptor)
        )
      }
    }
  }
}
