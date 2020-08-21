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

import com.netflix.spinnaker.kork.plugins.api.ExtensionConfiguration
import com.netflix.spinnaker.kork.plugins.api.PluginComponent
import com.netflix.spinnaker.kork.plugins.api.PluginConfiguration
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.core.type.filter.AssignableTypeFilter

/**
 * Initializes the given [plugin]'s [pluginApplicationContext] after being connected to the service's
 * own [ApplicationContext].
 */
class SpringPluginInitializer(
  private val plugin: Plugin,
  private val pluginWrapper: PluginWrapper,
  private val pluginApplicationContext: GenericApplicationContext,
  private val beanPromoter: BeanPromoter
) : ApplicationContextAware {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun setApplicationContext(applicationContext: ApplicationContext) {
    log.info("Initializing '${pluginWrapper.pluginId}'")

    // For every bean created in the plugin ApplicationContext, we'll need to post-process to evaluate
    // which ones need to be promoted to the service ApplicationContext for autowiring into core functionality.
    pluginApplicationContext
      .beanFactory
      .addBeanPostProcessor(ExtensionPromotionBeanPostProcessor(
        pluginWrapper,
        pluginApplicationContext,
        beanPromoter
      ))

    pluginApplicationContext.classLoader = pluginWrapper.pluginClassLoader
    pluginApplicationContext.setResourceLoader(DefaultResourceLoader(pluginWrapper.pluginClassLoader))
    pluginApplicationContext.scanForComponents()
    pluginApplicationContext.refresh()
  }

  private fun GenericApplicationContext.scanForComponents() {
    val scanner = ClassPathBeanDefinitionScanner(this, false, environment)
      .apply {
        addIncludeFilter(AnnotationTypeFilter(PluginComponent::class.java))
        addIncludeFilter(AssignableTypeFilter(SpinnakerExtensionPoint::class.java))

        // TODO(rz): We'll need FactoryBeans for these types of components in order for them to be
        //  created correctly.
        addIncludeFilter(AnnotationTypeFilter(PluginConfiguration::class.java))
        addIncludeFilter(AnnotationTypeFilter(ExtensionConfiguration::class.java))
      }

    scanner.scan(plugin.javaClass.`package`.name)
  }
}
