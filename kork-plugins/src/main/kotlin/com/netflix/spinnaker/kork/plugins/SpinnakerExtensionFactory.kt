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
import com.netflix.spinnaker.kork.plugins.api.ExtensionConfiguration
import com.netflix.spinnaker.kork.plugins.api.PluginSdks
import com.netflix.spinnaker.kork.plugins.config.ConfigFactory
import com.netflix.spinnaker.kork.plugins.sdk.PluginSdksImpl
import com.netflix.spinnaker.kork.plugins.sdk.SdkFactory
import java.lang.reflect.InvocationTargetException
import org.pf4j.ExtensionFactory
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory

/**
 * Creates extensions, injecting in dependencies as needed.
 */
class SpinnakerExtensionFactory(
  private val pluginManager: SpinnakerPluginManager,
  private val configFactory: ConfigFactory,
  private val pluginSdkFactories: List<SdkFactory>
) : ExtensionFactory {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun <T> create(extensionClass: Class<T>): T {
    val pluginWrapper = pluginManager.whichPlugin(extensionClass)

    @Suppress("UNCHECKED_CAST")
    return createWithConstructor(extensionClass, pluginWrapper) as T
  }

  private fun createWithConstructor(extensionClass: Class<*>, pluginWrapper: PluginWrapper?): Any {
    val candidates = extensionClass.declaredConstructors

    if (candidates.isEmpty()) {
      log.debug("No injectable constructor found for '${extensionClass.simpleName}': Using no-args constructor")
      return extensionClass.newInstance()
    }

    if (candidates.size > 1) {
      // A hinting annotation could be used, but since extension initialization is an internals-only thing, I think we
      // can enforce that an extension must have only one constructor.
      throw IntegrationException(
        "More than one injectable constructor found for '${extensionClass.simpleName}': Cannot initialize extension"
      )
    }

    val ctor = candidates.first()

    val paramValues = ctor.parameterTypes.map { paramType ->
      when {
          paramType == PluginSdks::class.java -> {
            PluginSdksImpl(pluginSdkFactories.map { it.create(extensionClass, pluginWrapper) })
          }
          paramType.isAnnotationPresent(ExtensionConfiguration::class.java) -> {
            configFactory.createExtensionConfig(
              paramType,
              paramType.getAnnotation(ExtensionConfiguration::class.java).value,
              pluginWrapper?.descriptor?.pluginId
            )
          }
          else -> {
            throw IntegrationException("'${extensionClass.simpleName}' extension has unsupported " +
              "constructor argument type '${paramType.simpleName}'.  Expected argument classes " +
              "should be annotated with @ExpectedConfiguration or implement PluginSdks.")
          }
      }
    }

    try {
      return ctor.newInstance(*paramValues.toTypedArray())
    } catch (ie: InstantiationException) {
      throw IntegrationException("Failed to instantiate extension '${extensionClass.simpleName}'", ie)
    } catch (iae: IllegalAccessException) {
      throw IntegrationException("Failed to instantiate extension '${extensionClass.simpleName}'", iae)
    } catch (iae: IllegalArgumentException) {
      throw IntegrationException("Failed to instantiate extension '${extensionClass.simpleName}'", iae)
    } catch (ite: InvocationTargetException) {
      throw IntegrationException("Failed to instantiate extension '${extensionClass.simpleName}'", ite)
    }
  }
}
