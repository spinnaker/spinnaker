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

import com.netflix.spinnaker.kork.plugins.api.SpringPlugin
import org.pf4j.ExtensionFactory
import org.pf4j.PluginManager
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.StandardEnvironment

/**
 * TODO(rz): Support creation of unsafe plugins
 * TODO(rz): Probably refactor such that each various plugin capability unsafe, etc) is a
 * separate ExtensionFactory that is delegated to
 */
open class SpringExtensionFactory(
  private val pluginManager: PluginManager
) : ExtensionFactory {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun <T> create(extensionClass: Class<T>): T? {
    val extension = createWithoutSpring(extensionClass) ?: return null
    val pluginWrapper = pluginManager.whichPlugin(extensionClass) ?: return extension

    val plugin = pluginWrapper.plugin
    if (plugin is SpringPlugin) {
      val applicationContext = getApplicationContext(pluginWrapper, plugin)

      plugin.applicationContext = applicationContext
      plugin.initApplicationContext()
      plugin.applicationContext.refresh()
      plugin.applicationContext.autowireCapableBeanFactory.autowireBean(extension)
    }

    return extension
  }

  private fun <T> createWithoutSpring(extensionClass: Class<T>): T? {
    try {
      return extensionClass.newInstance()
    } catch (e: Exception) {
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
}
