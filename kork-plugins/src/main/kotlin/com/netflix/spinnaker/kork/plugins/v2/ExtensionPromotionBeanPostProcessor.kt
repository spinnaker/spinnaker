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

import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint
import com.netflix.spinnaker.kork.plugins.events.ExtensionCreated
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered
import org.springframework.util.Assert

/**
 * Handles promotion of plugin beans to the parent service application context.
 *
 * Only beans that implement [SpinnakerExtensionPoint] are candidates for promotion.
 */
class ExtensionPromotionBeanPostProcessor(
  private val pluginWrapper: PluginWrapper,
  private val pluginApplicationContext: GenericApplicationContext,
  private val beanPromoter: BeanPromoter,
  private val applicationEventPublisher: ApplicationEventPublisher
) : BeanPostProcessor, PriorityOrdered {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun postProcessAfterInitialization(bean: Any, beanName: String): Any? {
    val definition = pluginApplicationContext.getBeanDefinition(beanName)

    Assert.notNull(definition.beanClassName, "Bean class name is null")

    val beanClass = getBeanClass(definition.beanClassName!!)
    if (SpinnakerExtensionPoint::class.java.isAssignableFrom(beanClass)) {
      log.debug("Promoting plugin extension to service context (${pluginWrapper.pluginId}): $beanClass")
      beanPromoter.promote("${pluginWrapper.pluginId}_${beanName}", bean, beanClass)

      applicationEventPublisher.publishEvent(ExtensionCreated(
        this,
        beanName,
        bean,
        beanClass
      ))
    }

    return bean
  }

  private fun getBeanClass(className: String): Class<*> =
    pluginWrapper.pluginClassLoader.loadClass(className)

  override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE
}
