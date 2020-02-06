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
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.plugins.api.ConfigurableExtension
import com.netflix.spinnaker.kork.plugins.api.SpinnakerExtension
import com.netflix.spinnaker.kork.plugins.api.spring.SpringPlugin
import com.netflix.spinnaker.kork.plugins.config.ConfigCoordinates
import com.netflix.spinnaker.kork.plugins.config.ConfigResolver
import com.netflix.spinnaker.kork.plugins.config.PluginConfigCoordinates
import com.netflix.spinnaker.kork.plugins.config.SystemExtensionConfigCoordinates
import org.pf4j.ExtensionFactory
import org.pf4j.PluginRuntimeException
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.ResolvableType
import org.springframework.core.env.StandardEnvironment
import java.lang.reflect.InvocationTargetException

/**
 * TODO(rz): Support creation of unsafe plugins
 */
class SpringExtensionFactory(
  private val pluginManager: SpinnakerPluginManager,
  private val configResolver: ConfigResolver
) : ExtensionFactory {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun <T> create(extensionClass: Class<T>): T {
    val extension = createWithoutSpring(extensionClass)
      ?: throw PluginRuntimeException("Failed to create object of extension class: $extensionClass")

    val annot = getSpinnakerExtensionAnnotation(extension)

    // Locate the plugin that provides this extension, if no plugin exists, then we know it's a system extension
    // and we can finalize its loading and return immediately.
    val pluginWrapper = pluginManager.whichPlugin(extensionClass) ?: return finalizeSystemExtension(extension, annot)

    // If the plugin is a SpringPlugin, we'll create an [ApplicationContext] for it and autowire the extension
    val plugin = pluginWrapper.plugin
    if (plugin is SpringPlugin) {
      val applicationContext = getApplicationContext(pluginWrapper, plugin)

      plugin.applicationContext = applicationContext
      plugin.initApplicationContext()
      plugin.applicationContext.refresh()
      plugin.applicationContext.autowireCapableBeanFactory.autowireBean(extension)
    }

    // Finally, load the config according to the [SpinnakerExtension] settings
    val coordinates = pluginWrapper.getCoordinates()

    loadConfig(extension, PluginConfigCoordinates(
      coordinates.id,
      annot.id
    ))

    return extension
  }

  /**
   * Finalizes the creation of a system extension by loading any config that may exist for it.
   */
  private fun <T> finalizeSystemExtension(extension: T, annot: SpinnakerExtension): T {
    if (extension == null) {
      // This should never happen
      throw SystemException("Extension instance is null, but contract expected non-null")
    }

    loadConfig(extension, SystemExtensionConfigCoordinates(
      annot.id
    ))

    return extension
  }

  /**
   * Retrieve the [SpinnakerExtension] annotation from an extension.
   *
   * Extensions within Spinnaker MUST use the [SpinnakerExtension] annotation, rather than the default PF4J
   * [org.pf4j.Extension] annotation.
   */
  private fun getSpinnakerExtensionAnnotation(extension: Any): SpinnakerExtension =
    extension.javaClass.getAnnotation(SpinnakerExtension::class.java)
      ?: throw IntegrationException("Extensions must be defined using @SpinnakerExtension")

  /**
   * Load an extension config, provided a set of [ConfigCoordinates].
   */
  @Suppress("ThrowsCount")
  private fun loadConfig(extension: Any, coordinates: ConfigCoordinates) {
    if (extension !is ConfigurableExtension<*>) {
      return
    }

    ResolvableType.forInstance(extension)
      .apply { resolve() }
      .also { resolvedType ->
        val parentTypes = listOf<ResolvableType>(resolvedType.superType) + resolvedType.interfaces
        parentTypes.find { ConfigurableExtension::class.java.isAssignableFrom(it.rawClass!!) }
          ?.let {
            it.resolve()
            it.getGeneric(0).rawClass
          }
          ?.let { configResolver.resolve(coordinates, it) }
          ?.also {
            try {
              val method = extension.javaClass.getDeclaredMethod("setConfiguration", it.javaClass)
              method.isAccessible = true
              method.invoke(extension, it)
            } catch (nsm: NoSuchMethodException) {
              throw IntegrationException("Could not find setConfiguration method on ${extension.javaClass.name}", nsm)
            } catch (se: SecurityException) {
              throw SystemException(se)
            } catch (iae: IllegalAccessException) {
              throw SystemException(
                "Could not access setConfiguration method on extension: ${extension.javaClass.name}", iae)
            } catch (iae: IllegalArgumentException) {
              throw SystemException(
                "Configuration on extension appears to be invalid: ${extension.javaClass.name}", iae)
            } catch (ite: InvocationTargetException) {
              throw SystemException(
                "Failed to invoke setConfiguration on extension: ${extension.javaClass.name}", ite)
            }
          }
          ?: throw SystemException("Could not find configuration class " +
            "'${resolvedType.getGeneric(0)}' for extension: ${extension.javaClass.name}")
      }
  }

  /**
   * Initialize the extension.
   *
   * All extensions must implement an empty constructor for creation.
   */
  private fun <T> createWithoutSpring(extensionClass: Class<T>): T? {
    try {
      return extensionClass.newInstance()
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
      log.error(e.message, e)
    }
    return null
  }

  private fun getApplicationContext(
    pluginWrapper: PluginWrapper,
    plugin: SpringPlugin
  ): AnnotationConfigApplicationContext {
    return if (pluginWrapper.isUnsafe()) {
      TODO("Need to pass the parent application context here")
    } else {
      AnnotationConfigApplicationContext().also {
        it.classLoader = plugin.wrapper.pluginClassLoader
        it.environment = StandardEnvironment()
      }
    }
  }

  private fun PluginWrapper.getCoordinates(): PluginCoordinates =
    (descriptor as SpinnakerPluginDescriptor).let { PluginCoordinates(it.pluginId) }

  private inner class PluginCoordinates(
    val id: String
  )
}
