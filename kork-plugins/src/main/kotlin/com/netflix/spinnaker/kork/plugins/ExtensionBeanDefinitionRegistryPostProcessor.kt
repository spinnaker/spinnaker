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

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor

/**
 * The primary point of integration between PF4J and Spring, this class is invoked early
 * in the Spring application lifecycle (before any other Beans are created), but after
 * the environment is prepared.
 */
class ExtensionBeanDefinitionRegistryPostProcessor(
  private val pluginManager: SpinnakerPluginManager,
  private val extensionsInjector: ExtensionsInjector
) : BeanDefinitionRegistryPostProcessor {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun postProcessBeanDefinitionRegistry(registry: BeanDefinitionRegistry) {
    log.debug("Preparing extensions")
    val start = System.currentTimeMillis()
    preparePlugins()
    log.info("Finished preparing extensions in {}ms", System.currentTimeMillis() - start)

    extensionsInjector.injectExtensions(registry)
    log.info("Finished injecting extensions into parent context")
  }

  private fun preparePlugins() {
    pluginManager.loadPlugins()
    pluginManager.startPlugins()
  }

  override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
    // Do nothing.
  }
}
