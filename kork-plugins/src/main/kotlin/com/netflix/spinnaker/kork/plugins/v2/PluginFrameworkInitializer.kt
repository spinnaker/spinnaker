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

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered

/**
 * Responsible for initializing the plugin framework within a service.
 *
 * [PriorityOrdered] to run as early as possible in the service application lifecycle.
 */
class PluginFrameworkInitializer(
  private val pluginService: SpinnakerPluginService
) : BeanDefinitionRegistryPostProcessor, ApplicationContextAware, PriorityOrdered {

  override fun postProcessBeanDefinitionRegistry(registry: BeanDefinitionRegistry) {
    pluginService.initialize()
    pluginService.startPlugins(registry)
  }

  @Suppress("EmptyFunctionBlock")
  override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
  }

  override fun setApplicationContext(applicationContext: ApplicationContext) {
    ApplicationContextGraph.serviceApplicationContext = applicationContext as ConfigurableApplicationContext
  }

  override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE
}
