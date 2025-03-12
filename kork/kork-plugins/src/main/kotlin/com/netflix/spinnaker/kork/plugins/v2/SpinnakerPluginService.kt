/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.kork.plugins.v2

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager
import com.netflix.spinnaker.kork.plugins.SpringPluginStatusProvider
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint
import com.netflix.spinnaker.kork.plugins.api.spring.PrivilegedSpringPlugin
import com.netflix.spinnaker.kork.plugins.events.ExtensionCreated
import com.netflix.spinnaker.kork.plugins.proxy.LazyExtensionInvocationProxy
import com.netflix.spinnaker.kork.plugins.proxy.aspects.InvocationAspect
import com.netflix.spinnaker.kork.plugins.proxy.aspects.InvocationState
import com.netflix.spinnaker.kork.plugins.update.SpinnakerUpdateManager
import com.netflix.spinnaker.kork.plugins.update.release.provider.PluginInfoReleaseProvider
import org.pf4j.Plugin
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.AnnotationBeanNameGenerator
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.type.filter.AssignableTypeFilter
import org.springframework.util.Assert
import java.lang.IllegalStateException
import java.util.function.Supplier

/**
 * A service for managing the plugin framework.
 *
 * NOTE: Over time, we should be moving to this class over [SpinnakerPluginManager] and
 * [SpinnakerUpdateManager] as the primary touch points for the plugin framework, decoupling
 * Spinnaker-specific plugin framework logic from PF4J wherever possible.
 */
class SpinnakerPluginService(
  private val pluginManager: SpinnakerPluginManager,
  private val updateManager: SpinnakerUpdateManager,
  private val pluginInfoReleaseProvider: PluginInfoReleaseProvider,
  private val springPluginStatusProvider: SpringPluginStatusProvider,
  private val invocationAspects: List<InvocationAspect<*>>,
  private val applicationEventPublisher: ApplicationEventPublisher
) {

  private val log = LoggerFactory.getLogger(javaClass)

  /**
   * Tracks the initialization state of the plugin framework: It can only be initialized once.
   */
  private var initialized: Boolean = false

  /**
   * Starts the plugin framework and completely initializes extensions for use by the application.
   */
  fun initialize() {
    Assert.isTrue(!initialized, "Plugin framework has already been initialized")

    withTiming("initializing plugins") {
      // Load known plugins prior to downloading so we can resolve what needs to be updated.
      pluginManager.loadPlugins()

      // Find the plugin releases for the currently enabled list of plugins
      val releases = updateManager.plugins
        .filter { springPluginStatusProvider.isPluginEnabled(it.id) }
        .let { enabledPlugins -> pluginInfoReleaseProvider.getReleases(enabledPlugins) }

      // Download releases, if any, updating previously loaded plugins where necessary
      updateManager.downloadPluginReleases(releases).forEach { pluginPath ->
        pluginManager.loadPlugin(pluginPath)
      }
    }
  }

  /**
   * Start the plugins, attaching exported plugin extensions to the provided [registry].
   */
  fun startPlugins(registry: BeanDefinitionRegistry) {
    withTiming("starting plugins") {
      // Start plugins. This should only be called once.
      pluginManager.startPlugins()

      // Perform additional work for Spring plugins; registering internal classes as beans where necessary
      pluginManager.startedPlugins.forEach { pluginWrapper ->
        val p = pluginWrapper.plugin
        if (p is PrivilegedSpringPlugin) {
          p.registerBeanDefinitions(registry)
        }

        if (p is PluginContainer) {
          val initializerBeanName = p.registerInitializer(registry)
          registerProxies(p, registry, initializerBeanName)
        }
      }
    }
  }

  /**
   * Registers lazy, proxied bean definitions for a plugin's extensions.
   * This allows service-level beans to inject and use extensions like any other beans.
   * The proxied extensions are initialized via [SpringPluginInitializer] when called for the first time.
   */
  private fun registerProxies(container: PluginContainer, registry: BeanDefinitionRegistry, initializerBeanName: String) {
    val pluginContext = container.pluginContext
    val pluginId = container.wrapper.descriptor.pluginId

    // Find the plugin's SpinnakerExtensionPoints.
    ClassPathScanningCandidateComponentProvider(false).apply {
      addIncludeFilter(AssignableTypeFilter(SpinnakerExtensionPoint::class.java))
      resourceLoader = DefaultResourceLoader(container.wrapper.pluginClassLoader)
    }.findCandidateComponents(container.actual.basePackageName).forEach { extensionBeanDefinition ->
      val extensionBeanClass = container.wrapper.pluginClassLoader.loadClass(extensionBeanDefinition.beanClassName) as Class<out SpinnakerExtensionPoint>

      // Find the name that the extension bean will (but hasn't yet) be given inside the plugin application context.
      // We'll use this to look up the extension inside the lazy loader.
      val pluginContextBeanName = AnnotationBeanNameGenerator.INSTANCE.generateBeanName(
        extensionBeanDefinition,
        pluginContext
      )

      // Provide an implementation of the extension that can be injected immediately by service-level classes.
      val proxy = LazyExtensionInvocationProxy.proxy(
        lazy {
          // Force the plugin's initializer to run if it hasn't already.
          pluginContext.parent?.also { it.getBean(initializerBeanName) }
            ?: throw IllegalStateException("Plugin context for \"${pluginId}\" was not configured with a parent context")

          // Fetch the extension from the plugin context.
          return@lazy pluginContext.getBean(pluginContextBeanName) as SpinnakerExtensionPoint
        },
        extensionBeanClass,
        invocationAspects as List<InvocationAspect<InvocationState>>,
        container.wrapper.descriptor as SpinnakerPluginDescriptor
      )

      val proxyBeanDefinition = BeanDefinitionBuilder.genericBeanDefinition().beanDefinition.apply {
        instanceSupplier = Supplier { proxy }
        beanClass = extensionBeanClass
      }
      registry.registerBeanDefinition(
        "${pluginId}_${extensionBeanClass.simpleName.decapitalize()}",
        proxyBeanDefinition
      )

      applicationEventPublisher.publishEvent(ExtensionCreated(
        this,
        pluginContextBeanName,
        proxy,
        extensionBeanClass
      ))
    }
  }

  private fun withTiming(task: String, callback: () -> Unit) {
    val start = System.currentTimeMillis()
    log.debug(task.capitalize())

    callback.invoke()

    log.debug("Finished $task in {}ms", System.currentTimeMillis() - start)
  }
}
