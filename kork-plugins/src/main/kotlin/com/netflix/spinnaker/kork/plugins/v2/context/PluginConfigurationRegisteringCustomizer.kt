/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.kork.plugins.v2.context

import com.google.common.base.Strings
import com.netflix.spinnaker.kork.plugins.api.PluginConfiguration
import com.netflix.spinnaker.kork.plugins.config.ConfigFactory
import com.netflix.spinnaker.kork.plugins.v2.basePackageName
import org.pf4j.Plugin
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.util.ClassUtils

/**
 * Scans a [Plugin]'s classpath for [PluginConfiguration], creating the configurations and then
 * adding them as beans to the plugin application context.
 *
 * TODO(rz): This could probably be done in a more idiomatic way by attaching a FactoryBean to the
 *  BeanDefinition, deferring instance creation to Spring, but this was easiest.
 */
class PluginConfigurationRegisteringCustomizer(
  private val configFactory: ConfigFactory,
  private var classResolver: ClassResolver? = null
) : PluginApplicationContextCustomizer {

  override fun accept(plugin: Plugin, context: ConfigurableApplicationContext) {
    val resolver = classResolver ?: DefaultClassResolver(plugin.wrapper.pluginClassLoader)

    ClassPathScanningCandidateComponentProvider(false, context.environment)
      .apply {
        resourceLoader = PathMatchingResourcePatternResolver(plugin.wrapper.pluginClassLoader)
        addIncludeFilter(AnnotationTypeFilter(PluginConfiguration::class.java))
      }
      .findCandidateComponents(plugin.basePackageName)
      .mapNotNull { it.beanClassName }
      .mapNotNull {
        val cls = resolver.resolveClassName(it)
        configFactory.createPluginConfig(
          cls,
          plugin.wrapper.pluginId,
          cls.getAnnotation(PluginConfiguration::class.java).value
        )
      }
      .forEach {
        var beanName = it.javaClass.simpleName.let { name ->
          val chars = name.toCharArray()
          chars[0] = Character.toLowerCase(chars[0])
          String(chars)
        }
        if (context.beanFactory.containsBean(beanName)) {
          // This is pretty janky, but it'll get the job done for now.
          beanName = "$beanName${System.nanoTime()}"
        }
        context.beanFactory.registerSingleton(beanName, it)
      }
  }

  /**
   * Allows for easier testing.
   */
  interface ClassResolver {
    fun resolveClassName(className: String): Class<*>
  }

  private inner class DefaultClassResolver(private val pluginClassLoader: ClassLoader) : ClassResolver {
    override fun resolveClassName(className: String): Class<*> =
      ClassUtils.resolveClassName(className, pluginClassLoader)
  }
}
