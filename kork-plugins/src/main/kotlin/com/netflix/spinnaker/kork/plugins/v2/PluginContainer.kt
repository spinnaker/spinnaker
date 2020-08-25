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
import com.netflix.spinnaker.kork.plugins.proxy.aspects.InvocationAspect
import org.pf4j.Plugin
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.support.GenericApplicationContext

/**
 * A container class for the actual plugin.
 *
 * This exists so we can perform our own logic on the "plugin" without exposing internal concerns
 * to the plugin developer. This container does, however, mean that plugin framework developers
 * must be sure _not_ to use this class in `$plugin::java.class` operations, instead using
 * `$plugin.actual::java.class` to get access to the actual plugin Java class.
 */
class PluginContainer(
  val actual: Plugin,
  private val serviceApplicationContext: GenericApplicationContext,
  private val beanPromoter: BeanPromoter,
  private val invocationAspects: List<InvocationAspect<*>>
) : Plugin(actual.wrapper) {

  private val pluginContext = GenericApplicationContext(serviceApplicationContext).also {
    ApplicationContextGraph.pluginContexts[wrapper.pluginId] = it
  }

  fun registerInitializer(registry: BeanDefinitionRegistry) {
    val initializerBeanName = "${wrapper.pluginId}Initializer"

    val initializerBeanDefinition = BeanDefinitionBuilder.genericBeanDefinition(SpringPluginInitializer::class.java)
      .setScope(BeanDefinition.SCOPE_SINGLETON)
      .setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_NO)
      .addConstructorArgValue(actual)
      .addConstructorArgValue(actual.wrapper)
      .addConstructorArgValue(pluginContext)
      .addConstructorArgValue(LegacyProxyingBeanPromoter(
        beanPromoter,
        invocationAspects,
        wrapper.descriptor as SpinnakerPluginDescriptor
      ))
      .beanDefinition

    registry.registerBeanDefinition(initializerBeanName, initializerBeanDefinition)
  }

  override fun start() {
    actual.start()
  }

  override fun stop() {
    actual.stop()
  }

  override fun delete() {
    actual.delete()
  }
}
