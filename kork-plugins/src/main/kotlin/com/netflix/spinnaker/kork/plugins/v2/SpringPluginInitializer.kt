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

import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.plugins.config.ConfigFactory
import com.netflix.spinnaker.kork.plugins.v2.context.ComponentScanningCustomizer
import com.netflix.spinnaker.kork.plugins.v2.context.PluginConfigurationRegisteringCustomizer
import com.netflix.spinnaker.kork.plugins.v2.context.PluginSdksRegisteringCustomizer
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.io.DefaultResourceLoader

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
    if (applicationContext !is ConfigurableApplicationContext) {
      throw SystemException("ApplicationContext must be configurable")
    }

    log.info("Initializing '${pluginWrapper.pluginId}'")

    // For every bean created in the plugin ApplicationContext, we'll need to post-process to evaluate
    // which ones need to be promoted to the service ApplicationContext for autowiring into core functionality.
    pluginApplicationContext
      .beanFactory
      .addBeanPostProcessor(
        ExtensionPromotionBeanPostProcessor(
          pluginWrapper,
          pluginApplicationContext,
          beanPromoter
        )
      )

    pluginApplicationContext.classLoader = pluginWrapper.pluginClassLoader
    pluginApplicationContext.setResourceLoader(DefaultResourceLoader(pluginWrapper.pluginClassLoader))

    listOf(
      PluginConfigurationRegisteringCustomizer(applicationContext.getBean(ConfigFactory::class.java)),
      PluginSdksRegisteringCustomizer(applicationContext),
      ComponentScanningCustomizer()
    ).forEach {
      it.accept(plugin, pluginApplicationContext)
    }

    pluginApplicationContext.refresh()
  }
}
