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
import com.netflix.spinnaker.kork.plugins.api.PluginConfiguration
import com.netflix.spinnaker.kork.plugins.api.PluginSdks
import com.netflix.spinnaker.kork.plugins.config.ConfigFactory
import com.netflix.spinnaker.kork.plugins.sdk.PluginSdksImpl
import com.netflix.spinnaker.kork.plugins.sdk.SdkFactory
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import org.pf4j.PluginDescriptor
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.netflix.spinnaker.kork.plugins.dsl")

/**
 * Returns whether or not a particular [PluginWrapper] is flagged as unsafe.
 */
internal fun PluginWrapper.isUnsafe(): Boolean {
  return descriptor.let {
    if (it is SpinnakerPluginDescriptor) {
      it.unsafe
    } else {
      false
    }
  }
}

/**
 * Validates a [PluginDescriptor] according to additional Spinnaker conventions.
 */
internal fun PluginDescriptor.validate() {
  CanonicalPluginId.validate(this.pluginId)
}

internal enum class ClassKind {
  PLUGIN,
  EXTENSION;

  override fun toString(): String = super.toString().toLowerCase()
}

internal fun Class<*>.createWithConstructor(
  classKind: ClassKind,
  pluginSdkFactories: List<SdkFactory>,
  configFactory: ConfigFactory,
  pluginWrapper: PluginWrapper?
): Any? {
  if (declaredConstructors.isEmpty()) {
    log.debug("No injectable constructor found for '$canonicalName': Using no-args constructor")
    return newInstanceSafely(classKind)
  }

  if (declaredConstructors.size > 1) {
    // A hinting annotation could be used, but since extension initialization is an internals-only thing, I think we
    // can enforce that an extension must have only one constructor.
    throw IntegrationException(
      "More than one injectable constructor found for '$canonicalName': Cannot initialize extension"
    )
  }

  val ctor = declaredConstructors.first()

  val args = ctor.parameterTypes.map { paramType ->
    when {
      paramType == PluginWrapper::class.java && classKind == ClassKind.PLUGIN -> pluginWrapper
      paramType == PluginSdks::class.java -> {
        PluginSdksImpl(pluginSdkFactories.map { it.create(this, pluginWrapper) })
      }
      paramType.isAnnotationPresent(PluginConfiguration::class.java) && classKind == ClassKind.EXTENSION -> {
        configFactory.createExtensionConfig(
          paramType,
          pluginWrapper?.descriptor?.pluginId,
          paramType.getAnnotation(PluginConfiguration::class.java).value
        )
      }
      paramType.isAnnotationPresent(PluginConfiguration::class.java) && classKind == ClassKind.PLUGIN -> {
        configFactory.createPluginConfig(
          paramType,
          pluginWrapper?.descriptor?.pluginId,
          paramType.getAnnotation(PluginConfiguration::class.java).value
        )
      }
      paramType.isAnnotationPresent(ExtensionConfiguration::class.java) -> {
        configFactory.createExtensionConfig(
          paramType,
          pluginWrapper?.descriptor?.pluginId,
          paramType.getAnnotation(ExtensionConfiguration::class.java).value
        )
      }
      else -> {
        throw IntegrationException(
          "'$canonicalName' has unsupported " +
            "constructor argument type '${paramType.canonicalName}'.  Expected argument classes " +
            "should be annotated with @PluginConfiguration or implement PluginSdks."
        )
      }
    }
  }

  return ctor.newInstanceSafely(classKind, args)
}

internal fun Class<*>.newInstanceSafely(kind: ClassKind): Any =
  try {
    newInstance()
  } catch (ie: InstantiationException) {
    throw IntegrationException("Failed to instantiate $kind '${declaringClass.simpleName}'", ie)
  } catch (iae: IllegalAccessException) {
    throw IntegrationException("Failed to instantiate $kind '${declaringClass.simpleName}'", iae)
  } catch (iae: IllegalArgumentException) {
    throw IntegrationException("Failed to instantiate $kind '${declaringClass.simpleName}'", iae)
  } catch (ite: InvocationTargetException) {
    throw IntegrationException("Failed to instantiate $kind '${declaringClass.simpleName}'", ite)
  }

private fun Constructor<*>.newInstanceSafely(kind: ClassKind, args: List<Any?>): Any =
  try {
    newInstance(*args.toTypedArray())
  } catch (ie: InstantiationException) {
    throw IntegrationException("Failed to instantiate $kind '${declaringClass.simpleName}'", ie)
  } catch (iae: IllegalAccessException) {
    throw IntegrationException("Failed to instantiate $kind '${declaringClass.simpleName}'", iae)
  } catch (iae: IllegalArgumentException) {
    throw IntegrationException("Failed to instantiate $kind '${declaringClass.simpleName}'", iae)
  } catch (ite: InvocationTargetException) {
    throw IntegrationException("Failed to instantiate $kind '${declaringClass.simpleName}'", ite)
  }
