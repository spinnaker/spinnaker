/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.plugin

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.stereotype.Component

@Component
internal class KubernetesAdapterBeanPostProcessor()
  : BeanDefinitionRegistryPostProcessor {
  override fun postProcessBeanDefinitionRegistry(registry: BeanDefinitionRegistry) {
    log.info("postProcessBeanDefinitionRegistry")
  }

  override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
    beanFactory
      .getBeanNamesForType(ResourcePlugin::class.java)
      .also {
        log.info("Found these plugins: {}", it.joinToString())
      }
      .forEach { name ->
        when (beanFactory) {
          is BeanDefinitionRegistry -> beanFactory.registerBeanDefinition(
            "${name}Adapter",
            BeanDefinitionBuilder
              .genericBeanDefinition(ResourcePluginKubernetesAdapter::class.java)
              .addConstructorArgReference("resourceRepository")
              .addConstructorArgReference("resourceVersionTracker")
              .addConstructorArgReference("extensionsApi")
              .addConstructorArgReference("customObjectsApi")
              .addConstructorArgReference(name)
              .beanDefinition
          )
          else -> throw IllegalStateException("This is some weird Spring setup that doesn't use a regular bean factory")
        }
      }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
