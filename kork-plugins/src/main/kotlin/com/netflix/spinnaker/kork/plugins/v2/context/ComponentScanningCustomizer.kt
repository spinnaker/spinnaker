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

import com.netflix.spinnaker.kork.plugins.api.PluginComponent
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint
import com.netflix.spinnaker.kork.plugins.v2.basePackageName
import org.pf4j.Plugin
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.core.type.filter.AssignableTypeFilter

/**
 * Scans a [Plugin] classpath for known annotated classes that should be registered into the bean factory.
 */
class ComponentScanningCustomizer : PluginApplicationContextCustomizer {

  override fun accept(plugin: Plugin, context: ConfigurableApplicationContext) {
    val scanner = ClassPathBeanDefinitionScanner(context as GenericApplicationContext, false, context.environment)
      .apply {
        addIncludeFilter(AnnotationTypeFilter(PluginComponent::class.java))
        addIncludeFilter(AssignableTypeFilter(SpinnakerExtensionPoint::class.java))
      }

    scanner.scan(plugin.basePackageName)
  }
}
